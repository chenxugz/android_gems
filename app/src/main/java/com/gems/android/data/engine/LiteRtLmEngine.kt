package com.gems.android.data.engine

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.gems.android.domain.engine.LlmEngine
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import com.google.ai.edge.litertlm.SamplerConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LiteRtLmEngine @Inject constructor(
    @ApplicationContext private val context: Context,
) : LlmEngine {

    companion object {
        private const val TAG = "LiteRtLmEngine"

        private val E2B_FILENAMES = listOf("gemma-4-E2B-it.litertlm")
        private val E4B_FILENAMES = listOf("gemma-4-E4B-it.litertlm")
        private val FALLBACK_FILENAMES = listOf(
            "gemma-4-E2B-it.litertlm",
            "gemma-4-E4B-it.litertlm",
        )

        const val DEFAULT_MODEL_FILENAME = "gemma-4-E2B-it.litertlm"
        private const val MAX_TOKENS = 4096
    }

    var preferE4B: Boolean = false
    var modelPath: String = resolveModelPath()
    private var useCpu: Boolean = false

    private fun resolveModelPath(): String {
        val preferred = if (preferE4B) E4B_FILENAMES else E2B_FILENAMES
        val searchOrder = preferred + FALLBACK_FILENAMES
        for (filename in searchOrder) {
            val extPath = File(context.getExternalFilesDir(null), "models/$filename")
            if (extPath.exists()) return extPath.absolutePath
            val internalPath = File(context.filesDir, "models/$filename")
            if (internalPath.exists()) return internalPath.absolutePath
            val devPath = File("/data/local/tmp", filename)
            if (devPath.exists()) return devPath.absolutePath
        }
        return File(context.filesDir, DEFAULT_MODEL_FILENAME).absolutePath
    }

    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private val mutex = Mutex()

    private suspend fun ensureLoaded() = mutex.withLock {
        if (engine != null && conversation != null) return@withLock

        modelPath = resolveModelPath()
        Log.i(TAG, "Loading model from: $modelPath")
        val startMs = System.currentTimeMillis()
        val cacheDir = context.getExternalFilesDir(null)?.absolutePath ?: context.cacheDir.absolutePath

        Log.i(TAG, "Using CPU backend with vision")

        val engineConfig = EngineConfig(
            modelPath = modelPath,
            backend = Backend.CPU(),
            visionBackend = Backend.CPU(),
            maxNumTokens = MAX_TOKENS,
            cacheDir = cacheDir,
        )

        val eng = Engine(engineConfig)
        eng.initialize()
        engine = eng

        conversation = engine!!.createConversation(
            ConversationConfig(
                samplerConfig = null // use default sampler to avoid OpenCL issues,
            )
        )
        Log.i(TAG, "Model loaded in ${System.currentTimeMillis() - startMs}ms")
    }

    private fun resetConversation() {
        val eng = engine ?: return
        conversation?.close()
        conversation = eng.createConversation(
            ConversationConfig(
                samplerConfig = null // use default sampler to avoid OpenCL issues,
            )
        )
        Log.d(TAG, "Conversation reset")
    }

    // -- LlmEngine contract -------------------------------------------------

    override suspend fun think(
        userPrompt: String,
        systemPrompt: String?,
        images: List<Bitmap>,
    ): String = withContext(Dispatchers.Default) {
        val result = runInference(userPrompt, systemPrompt, images)

        // If inference failed (empty result), switch to CPU and retry
        if (result.isEmpty()) {
            Log.w(TAG, "Inference returned empty, switching to CPU and retrying...")
            close()
            useCpu = true
            // Retry with images — CPU will just ignore them but won't crash
            runInference(userPrompt, systemPrompt, images)
        } else {
            result
        }
    }

    private suspend fun runInference(
        userPrompt: String,
        systemPrompt: String?,
        images: List<Bitmap>,
    ): String {
        // If images provided, MUST use GPU for vision — force reload if on CPU
        if (images.isNotEmpty()) {
            if (useCpu || engine != null) {
                // Close existing engine and force GPU reload for vision
                Log.i(TAG, "Vision requested — ensuring GPU with vision backend")
                close()
                useCpu = false
            }
        }
        ensureLoaded()
        val conv = conversation ?: throw IllegalStateException("Conversation not initialized")

        val prompt = buildPromptText(systemPrompt, userPrompt)
        Log.d(TAG, "Inference start — prompt length: ${prompt.length} chars")
        val startMs = System.currentTimeMillis()

        val contents = buildContents(prompt, images)
        val accumulated = StringBuilder()
        val latch = java.util.concurrent.CountDownLatch(1)

        conv.sendMessageAsync(
            contents,
            object : MessageCallback {
                override fun onMessage(message: Message) {
                    accumulated.append(message.toString())
                }
                override fun onDone() { latch.countDown() }
                override fun onError(throwable: Throwable) {
                    Log.e(TAG, "Inference error: ${throwable.message}")
                    latch.countDown()
                }
            },
        )

        val completed = latch.await(60, java.util.concurrent.TimeUnit.SECONDS)
        if (!completed) {
            Log.w(TAG, "Inference timed out after 60s")
            try { conv.cancelProcess() } catch (_: Exception) {}
            close()
        } else {
            resetConversation()
        }

        val result = stripThinkingAndStopTokens(accumulated.toString())
        val elapsed = System.currentTimeMillis() - startMs
        Log.i(TAG, "Inference completed in ${elapsed}ms — ${result.length} chars")
        return result
    }

    override fun thinkStream(
        userPrompt: String,
        systemPrompt: String?,
    ): Flow<String> = callbackFlow {
        ensureLoaded()
        val conv = conversation ?: throw IllegalStateException("Conversation not initialized")

        val prompt = buildPromptText(systemPrompt, userPrompt)
        Log.d(TAG, "Streaming start — prompt length: ${prompt.length} chars")
        val startMs = System.currentTimeMillis()
        val accumulated = StringBuilder()
        var hadError = false

        conv.sendMessageAsync(
            Contents.of(Content.Text(prompt)),
            object : MessageCallback {
                override fun onMessage(message: Message) {
                    val token = message.toString()
                    accumulated.append(token)
                    val visible = stripThinkingAndStopTokens(accumulated.toString())
                    trySend(visible)
                }

                override fun onDone() {
                    val elapsed = System.currentTimeMillis() - startMs
                    Log.i(TAG, "Streaming done in ${elapsed}ms")
                    close()
                }

                override fun onError(throwable: Throwable) {
                    Log.e(TAG, "Streaming error: ${throwable.message}")
                    hadError = true
                    useCpu = true // switch to CPU for next call
                    close()
                }
            },
        )

        awaitClose {
            resetConversation()
            Log.d(TAG, "Stream closed, conversation reset")
        }
    }.flowOn(Dispatchers.Default)

    // -- helpers ------------------------------------------------------------

    private fun buildPromptText(systemPrompt: String?, userPrompt: String): String {
        val noThink = "Do NOT use <think> tags. Respond directly without reasoning steps."
        return if (!systemPrompt.isNullOrBlank()) {
            "$systemPrompt\n$noThink\n\n$userPrompt"
        } else {
            "$noThink\n\n$userPrompt"
        }
    }

    private fun buildContents(prompt: String, images: List<Bitmap>): Contents {
        val parts = mutableListOf<Content>()
        for (image in images) {
            val stream = ByteArrayOutputStream()
            image.compress(Bitmap.CompressFormat.PNG, 100, stream)
            parts.add(Content.ImageBytes(stream.toByteArray()))
        }
        parts.add(Content.Text(prompt))
        return Contents.of(parts)
    }

    private fun stripThinkingAndStopTokens(raw: String): String {
        var text = raw
            .substringBefore("<end_of_turn>")
            .substringBefore("<eos>")
            .substringBefore("<|endoftext|>")
        text = text.replace(Regex("<think>[\\s\\S]*?</think>"), "")
        val openIdx = text.indexOf("<think>")
        if (openIdx >= 0) text = text.substring(0, openIdx)
        return text.trim()
    }

    fun close() {
        conversation?.close()
        conversation = null
        engine?.close()
        engine = null
        // Don't reset useCpu here — if GPU failed, keep using CPU until app restart
        Log.i(TAG, "Engine released")
    }
}
