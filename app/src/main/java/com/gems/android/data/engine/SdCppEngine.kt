package com.gems.android.data.engine

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.gems.android.domain.engine.ImageGenEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Image generation engine using stable-diffusion.cpp with Vulkan GPU backend.
 */
@Singleton
class SdCppEngine @Inject constructor(
    @ApplicationContext private val context: Context
) : ImageGenEngine {

    companion object {
        private const val TAG = "SdCppEngine"

        private val MODEL_NAMES = listOf(
            "sd_turbo.gguf",           // SD Turbo — 1-step, fastest
            "sd15_q4_0.gguf",
            "sd15.safetensors",
        )

        private const val TAESD_NAME = "taesd.safetensors"

        init {
            System.loadLibrary("sdcpp")
        }
    }

    /** Callback interface for generation progress. */
    interface ProgressCallback {
        fun onProgress(step: Int, totalSteps: Int, timePerStep: Float)
    }

    var progressCallback: ProgressCallback? = null

    /** Number of sampling steps for SD Turbo (1 = fastest, 2 = better quality). */
    var numSteps: Int = 2

    private var modelLoaded = false

    private fun resolveModelPath(): String? {
        for (name in MODEL_NAMES) {
            // External files dir (from in-app DownloadManager)
            val extPath = File(context.getExternalFilesDir(null), "models/$name")
            if (extPath.exists()) return extPath.absolutePath
            // Internal storage
            val internalPath = File(context.filesDir, "models/$name")
            if (internalPath.exists()) return internalPath.absolutePath
            // Dev path (from adb push)
            val devPath = File("/data/local/tmp", name)
            if (devPath.exists()) return devPath.absolutePath
        }
        return null
    }

    private fun resolveTaesdPath(): String? {
        val extPath = File(context.getExternalFilesDir(null), "models/$TAESD_NAME")
        if (extPath.exists()) return extPath.absolutePath
        val internalPath = File(context.filesDir, "models/$TAESD_NAME")
        if (internalPath.exists()) return internalPath.absolutePath
        val devPath = File("/data/local/tmp", TAESD_NAME)
        if (devPath.exists()) return devPath.absolutePath
        return null
    }

    private suspend fun ensureLoaded() {
        if (modelLoaded) return

        val modelPath = resolveModelPath()
            ?: throw IllegalStateException("No SD model found. Push model to /data/local/tmp/sd15.safetensors")

        Log.i(TAG, "Loading model: $modelPath")
        val startMs = System.currentTimeMillis()

        val taesdPath = resolveTaesdPath()
        val success = nativeLoadModel(modelPath, taesdPath, 4)
        if (!success) throw RuntimeException("Failed to load model: $modelPath")

        modelLoaded = true
        Log.i(TAG, "Model loaded in ${System.currentTimeMillis() - startMs}ms")
    }

    override suspend fun generate(prompt: String): Bitmap = withContext(Dispatchers.Default) {
        ensureLoaded()

        Log.i(TAG, "Generating image for: \"$prompt\"")
        val startMs = System.currentTimeMillis()

        // Create a JNI-compatible progress callback
        val jniCallback = object : Any() {
            @Suppress("unused") // Called from JNI
            fun onProgress(step: Int, totalSteps: Int, timePerStep: Float) {
                progressCallback?.onProgress(step, totalSteps, timePerStep)
            }
        }

        val isTurbo = resolveModelPath()?.contains("turbo") == true
        val steps = if (isTurbo) numSteps else 8
        val cfg = if (isTurbo) 1.0f else 7.0f

        val pixels = nativeGenerate(
            prompt = prompt,
            negPrompt = "",
            width = 512,
            height = 512,
            steps = steps,
            cfgScale = cfg,
            seed = System.currentTimeMillis(),
            progressCallback = jniCallback
        ) ?: throw RuntimeException("Image generation failed")

        val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, 512, 0, 0, 512, 512)

        val elapsed = System.currentTimeMillis() - startMs
        Log.i(TAG, "Image generated in ${elapsed}ms")

        // Reset native context to avoid Vulkan state corruption on next run
        nativeFree()
        modelLoaded = false

        bitmap
    }

    fun close() {
        nativeFree()
        modelLoaded = false
        Log.i(TAG, "Engine closed")
    }

    private external fun nativeLoadModel(modelPath: String, taesdPath: String?, nThreads: Int): Boolean
    private external fun nativeGenerate(
        prompt: String, negPrompt: String?,
        width: Int, height: Int, steps: Int, cfgScale: Float, seed: Long,
        progressCallback: Any?
    ): IntArray?
    private external fun nativeFree()
}
