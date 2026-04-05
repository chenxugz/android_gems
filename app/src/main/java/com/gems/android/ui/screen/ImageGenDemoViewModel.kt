package com.gems.android.ui.screen

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gems.android.data.engine.LiteRtLmEngine
import com.gems.android.data.engine.SdCppEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ImageGenDemoUiState(
    val prompt: String = "",
    val image: Bitmap? = null,
    val loading: Boolean = false,
    val statusMessage: String = "",
    val currentStep: Int = 0,
    val totalSteps: Int = 0,
    val totalTimeMs: Long? = null,
    val error: String? = null,
)

@HiltViewModel
class ImageGenDemoViewModel @Inject constructor(
    private val imageGenEngine: SdCppEngine,
    private val llmEngine: LiteRtLmEngine,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImageGenDemoUiState())
    val uiState: StateFlow<ImageGenDemoUiState> = _uiState.asStateFlow()

    fun generateImage(prompt: String, steps: Int = 2) {
        imageGenEngine.numSteps = steps
        _uiState.value = ImageGenDemoUiState(
            prompt = prompt,
            loading = true,
            statusMessage = "Freeing LLM memory...",
        )

        // Set up progress callback
        imageGenEngine.progressCallback = object : SdCppEngine.ProgressCallback {
            override fun onProgress(step: Int, totalSteps: Int, timePerStep: Float) {
                _uiState.update {
                    it.copy(
                        currentStep = step,
                        totalSteps = totalSteps,
                        statusMessage = "Step $step/$totalSteps (${timePerStep.toInt()}s/step)",
                    )
                }
            }
        }

        viewModelScope.launch {
            try {
                llmEngine.close()
                System.gc()
                kotlinx.coroutines.delay(1000) // wait for GPU memory release
                _uiState.update { it.copy(statusMessage = "Loading SD model (Vulkan GPU)...") }

                val startMs = System.currentTimeMillis()
                val bitmap = imageGenEngine.generate(prompt)
                val elapsed = System.currentTimeMillis() - startMs

                _uiState.value = ImageGenDemoUiState(
                    prompt = prompt,
                    image = bitmap,
                    totalTimeMs = elapsed,
                )
            } catch (e: Exception) {
                _uiState.value = ImageGenDemoUiState(
                    prompt = prompt,
                    error = "Error: ${e.message}",
                )
            }
        }
    }
}
