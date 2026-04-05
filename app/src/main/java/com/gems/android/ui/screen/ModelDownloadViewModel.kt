package com.gems.android.ui.screen

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class ModelDownloadInfo(
    val id: String,
    val name: String,
    val description: String,
    val sizeLabel: String,
    val url: String,
    val fileName: String,
    val requiresAuth: Boolean = false,
    val licensePage: String = "",
    val downloaded: Boolean = false,
    val downloading: Boolean = false,
    val progress: Float = 0f,
    val error: String? = null,
)

data class ModelDownloadUiState(
    val models: List<ModelDownloadInfo> = emptyList(),
)

private data class ModelDef(
    val id: String, val name: String, val description: String,
    val sizeLabel: String, val url: String, val fileName: String,
    val requiresAuth: Boolean = false,
    val licensePage: String = "",
)

private val MODEL_DEFS = listOf(
    ModelDef(
        id = "sd_turbo",
        name = "SD Turbo (Image Generator)",
        description = "Stable Diffusion Turbo Q8 — fast 1-step image generation",
        sizeLabel = "1.9 GB",
        url = "https://huggingface.co/Green-Sky/SD-Turbo-GGUF/resolve/main/sd_turbo-f16-q8_0.gguf",
        fileName = "sd_turbo.gguf",
    ),
    ModelDef(
        id = "taesd",
        name = "TAESD (Fast Decoder)",
        description = "Tiny AutoEncoder — 10x faster image decoding",
        sizeLabel = "9 MB",
        url = "https://huggingface.co/madebyollin/taesd/resolve/main/diffusion_pytorch_model.safetensors",
        fileName = "taesd.safetensors",
    ),
    ModelDef(
        id = "gemma4_e2b",
        name = "Gemma 4 E2B (LLM — faster)",
        description = "2B multimodal LLM — fast inference, good quality",
        sizeLabel = "2.4 GB",
        url = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm",
        fileName = "gemma-4-E2B-it.litertlm",
    ),
    ModelDef(
        id = "gemma4_e4b",
        name = "Gemma 4 E4B (LLM — smarter)",
        description = "4B multimodal LLM — better reasoning, slower",
        sizeLabel = "3.4 GB",
        url = "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/main/gemma-4-E4B-it.litertlm",
        fileName = "gemma-4-E4B-it.litertlm",
    ),
)

/**
 * Singleton download manager using Android's DownloadManager.
 * Downloads continue even if the app is closed or killed.
 */
@Singleton
class ModelDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val TAG = "ModelDownload"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    // Use external files dir — DownloadManager can't write to internal storage
    val modelDir = File(context.getExternalFilesDir(null), "models").also { it.mkdirs() }

    // Track active download IDs: downloadId -> modelId
    private val activeDownloads = mutableMapOf<Long, String>()

    private val _uiState = MutableStateFlow(ModelDownloadUiState())
    val uiState: StateFlow<ModelDownloadUiState> = _uiState.asStateFlow()

    var hfToken: String = ""

    init {
        refreshState()
        registerCompletionReceiver()
        // Start polling progress for any active downloads
        scope.launch { pollProgress() }
    }

    fun refreshState() {
        val currentModels = _uiState.value.models
        _uiState.value = ModelDownloadUiState(
            models = MODEL_DEFS.map { def ->
                val existing = currentModels.find { it.id == def.id }
                if (existing != null && existing.downloading) {
                    existing.copy(downloaded = File(modelDir, def.fileName).exists())
                } else {
                    ModelDownloadInfo(
                        id = def.id,
                        name = def.name,
                        description = def.description,
                        sizeLabel = def.sizeLabel,
                        url = def.url,
                        fileName = def.fileName,
                        requiresAuth = def.requiresAuth,
                        licensePage = def.licensePage,
                        downloaded = File(modelDir, def.fileName).exists(),
                    )
                }
            }
        )
    }

    fun downloadModel(modelId: String) {
        val def = MODEL_DEFS.find { it.id == modelId } ?: return
        val destFile = File(modelDir, def.fileName)

        // Remove old file if exists
        if (destFile.exists()) destFile.delete()

        val request = DownloadManager.Request(Uri.parse(def.url)).apply {
            setTitle("Downloading ${def.name}")
            setDescription(def.sizeLabel)
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            setDestinationUri(Uri.fromFile(destFile))
            addRequestHeader("User-Agent", "Android-GEMS/1.0")
            if (def.requiresAuth && hfToken.isNotBlank()) {
                addRequestHeader("Authorization", "Bearer $hfToken")
            }
        }

        val downloadId = dm.enqueue(request)
        activeDownloads[downloadId] = modelId
        Log.i(TAG, "Started download $downloadId for ${def.fileName}")

        _uiState.update { state ->
            state.copy(models = state.models.map {
                if (it.id == modelId) it.copy(downloading = true, error = null, progress = 0f) else it
            })
        }

        // Start polling progress
        scope.launch { pollProgress() }
    }

    private fun registerCompletionReceiver() {
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(completionReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(completionReceiver, filter)
        }
    }

    private val completionReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            val downloadId = intent?.getLongExtra("extra_download_id", -1) ?: return
            val modelId = activeDownloads.remove(downloadId) ?: return

            // Check status
            val query = DownloadManager.Query().setFilterById(downloadId)
            val cursor = dm.query(query)
            if (cursor.moveToFirst()) {
                val statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                val status = cursor.getInt(statusIdx)

                if (status == DownloadManager.STATUS_SUCCESSFUL) {
                    Log.i(TAG, "Download complete: $modelId")
                    _uiState.update { state ->
                        state.copy(models = state.models.map {
                            if (it.id == modelId) it.copy(downloading = false, downloaded = true, progress = 1f) else it
                        })
                    }
                } else {
                    val reasonIdx = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
                    val reason = cursor.getInt(reasonIdx)
                    Log.e(TAG, "Download failed: $modelId, status=$status, reason=$reason")
                    _uiState.update { state ->
                        state.copy(models = state.models.map {
                            if (it.id == modelId) it.copy(
                                downloading = false,
                                error = "Download failed (code $reason). Check network and retry."
                            ) else it
                        })
                    }
                }
            }
            cursor.close()
        }
    }

    private suspend fun pollProgress() {
        while (activeDownloads.isNotEmpty()) {
            for ((downloadId, modelId) in activeDownloads.toMap()) {
                val query = DownloadManager.Query().setFilterById(downloadId)
                val cursor = dm.query(query)
                if (cursor.moveToFirst()) {
                    val bytesIdx = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                    val totalIdx = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                    val downloaded = cursor.getLong(bytesIdx)
                    val total = cursor.getLong(totalIdx)

                    if (total > 0) {
                        val progress = downloaded.toFloat() / total
                        _uiState.update { state ->
                            state.copy(models = state.models.map {
                                if (it.id == modelId) it.copy(progress = progress) else it
                            })
                        }
                    }
                }
                cursor.close()
            }
            delay(1000)
        }
    }
}

@HiltViewModel
class ModelDownloadViewModel @Inject constructor(
    private val manager: ModelDownloadManager,
) : ViewModel() {

    val uiState: StateFlow<ModelDownloadUiState> = manager.uiState

    init {
        manager.refreshState()
    }

    fun setHfToken(token: String) {
        manager.hfToken = token
    }

    fun downloadModel(modelId: String) {
        manager.downloadModel(modelId)
    }
}
