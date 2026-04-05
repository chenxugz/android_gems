package com.gems.android.ui.screen

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gems.android.data.engine.LiteRtLmEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DemoUiState(
    val response: String = "",
    val inferenceTimeMs: Long? = null,
    val loading: Boolean = false,
    val streaming: Boolean = false,
    val statusMessage: String = "",
    val error: String? = null,
    val selectedImage: Bitmap? = null,
)

@HiltViewModel
class DemoViewModel @Inject constructor(
    private val llmEngine: LiteRtLmEngine,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DemoUiState())
    val uiState: StateFlow<DemoUiState> = _uiState.asStateFlow()

    fun setModelPreference(useE4B: Boolean) {
        if (llmEngine.preferE4B != useE4B) {
            llmEngine.preferE4B = useE4B
            llmEngine.close() // force reload
        }
    }

    fun setImage(bitmap: Bitmap?) {
        _uiState.update { it.copy(selectedImage = bitmap) }
    }

    fun runInference(prompt: String) {
        val image = _uiState.value.selectedImage
        _uiState.update { it.copy(loading = true, statusMessage = "Loading model...", response = "", error = null) }

        viewModelScope.launch {
            try {
                _uiState.update { it.copy(statusMessage = "Generating...") }
                val startMs = System.currentTimeMillis()

                if (image != null) {
                    // Vision mode: always send the image, let the engine handle it
                    val result = llmEngine.think(
                        userPrompt = prompt,
                        systemPrompt = "You are a helpful assistant. Respond concisely.",
                        images = listOf(image),
                    )
                    val elapsed = System.currentTimeMillis() - startMs
                    _uiState.value = DemoUiState(
                        response = result,
                        inferenceTimeMs = elapsed,
                        selectedImage = image,
                    )
                } else {
                    // Text-only: try streaming, fall back to sync if GPU fails
                    var gotResult = false
                    llmEngine.thinkStream(
                        userPrompt = prompt,
                        systemPrompt = "You are a helpful assistant. Respond concisely."
                    ).collect { visibleText ->
                        gotResult = true
                        _uiState.update { state ->
                            state.copy(
                                response = visibleText,
                                loading = false,
                                streaming = true,
                                statusMessage = "",
                            )
                        }
                    }

                    if (!gotResult) {
                        // Streaming failed (GPU error) — retry with sync think()
                        _uiState.update { it.copy(loading = true, statusMessage = "Retrying on CPU...") }
                        llmEngine.close()
                        val result = llmEngine.think(
                            userPrompt = prompt,
                            systemPrompt = "You are a helpful assistant. Respond concisely.",
                        )
                        val elapsed = System.currentTimeMillis() - startMs
                        _uiState.value = DemoUiState(response = result, inferenceTimeMs = elapsed)
                    } else {
                        val elapsed = System.currentTimeMillis() - startMs
                        _uiState.update { it.copy(streaming = false, inferenceTimeMs = elapsed) }
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        loading = false,
                        streaming = false,
                        error = "Error: ${e.message}",
                        selectedImage = image,
                    )
                }
            }
        }
    }
}
