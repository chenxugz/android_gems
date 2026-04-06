package com.gems.android.ui.screen

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gems.android.data.engine.LiteRtLmEngine
import com.gems.android.data.engine.SdCppEngine
import com.gems.android.domain.agent.AgentOrchestrator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ComparisonUiState(
    val originalPrompt: String = "",

    // Pipeline A — direct generation
    val directImage: Bitmap? = null,
    val directTimeMs: Long? = null,
    val directLoading: Boolean = false,
    val directError: String? = null,

    // Pipeline B — GEMS agent loop
    val gemsImage: Bitmap? = null,
    val gemsTimeMs: Long? = null,
    val gemsRefinedPrompt: String? = null,
    val gemsVerifierScore: Float? = null,
    val gemsTotalIterations: Int? = null,
    val gemsLoading: Boolean = false,
    val gemsError: String? = null,
    val gemsStatus: String = "",
    val gemsRoundImages: List<RoundImage> = emptyList(),
    val gemsUsedSkill: String? = null,
)

data class RoundImage(
    val round: Int,
    val image: Bitmap,
    val prompt: String,
    val score: Float? = null,
)

@HiltViewModel
class ComparisonViewModel @Inject constructor(
    private val imageGenEngine: SdCppEngine,
    private val llmEngine: LiteRtLmEngine,
    private val agentOrchestrator: AgentOrchestrator,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ComparisonUiState())
    val uiState: StateFlow<ComparisonUiState> = _uiState.asStateFlow()

    /**
     * Run both pipelines sequentially (not parallel — they share GPU).
     * Pipeline A: direct image gen. Pipeline B: GEMS agent loop.
     */
    fun startComparison(prompt: String, steps: Int = 2, iterations: Int = 2, useE4B: Boolean = false) {
        imageGenEngine.numSteps = steps
        agentOrchestrator.maxIterations = iterations
        llmEngine.preferE4B = useE4B
        llmEngine.close() // force reload with new model preference
        _uiState.value = ComparisonUiState(
            originalPrompt = prompt,
            directLoading = true,
            gemsLoading = true,
            gemsStatus = "Waiting for direct generation to finish...",
        )

        viewModelScope.launch {
            // Pipeline A: Direct generation
            runDirectPipeline(prompt)

            // Pipeline B: GEMS agent loop (runs after direct is done)
            runGemsPipeline(prompt)
        }
    }

    private suspend fun runDirectPipeline(prompt: String) {
        try {
            llmEngine.close()
            val start = System.currentTimeMillis()
            val bitmap = imageGenEngine.generate(prompt)
            val elapsed = System.currentTimeMillis() - start
            _uiState.update {
                it.copy(directImage = bitmap, directTimeMs = elapsed, directLoading = false)
            }
        } catch (e: Exception) {
            _uiState.update {
                it.copy(directLoading = false, directError = e.message ?: "Direct generation failed")
            }
        }
    }

    private suspend fun runGemsPipeline(prompt: String) {
        try {
            _uiState.update { it.copy(gemsStatus = "Starting GEMS agent loop...") }

            agentOrchestrator.progressListener = object : AgentOrchestrator.ProgressListener {
                override fun onStatus(message: String) {
                    _uiState.update { it.copy(gemsStatus = message) }
                }
                override fun onRoundImage(round: Int, image: android.graphics.Bitmap, prompt: String, score: Float?) {
                    _uiState.update { state ->
                        state.copy(
                            gemsImage = image, // always show latest as the main GEMS image
                            gemsRoundImages = state.gemsRoundImages + RoundImage(round, image, prompt, score),
                        )
                    }
                }
            }

            val result = agentOrchestrator.run(prompt)
            _uiState.update {
                it.copy(
                    gemsImage = result.image,
                    gemsTimeMs = result.elapsedMs,
                    gemsRefinedPrompt = result.refinedPrompt,
                    gemsVerifierScore = result.verifierScore,
                    gemsTotalIterations = result.totalIterations,
                    gemsLoading = false,
                    gemsStatus = "",
                    gemsUsedSkill = result.usedSkill,
                )
            }
        } catch (e: Exception) {
            _uiState.update {
                it.copy(gemsLoading = false, gemsError = e.message ?: "GEMS pipeline failed")
            }
        }
    }
}
