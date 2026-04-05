package com.gems.android.domain.model

import android.graphics.Bitmap

/** The role the single LLM instance is currently playing. */
enum class AgentRole {
    PLANNER,
    DECOMPOSER,
    VERIFIER,
    EXPERIENCE_SUMMARIZER,
    REFINER
}

/** A single yes/no verification result for one requirement question. */
data class Verification(
    val question: String,
    val answer: String,
    val passed: Boolean
)

/** One full iteration record in the agent loop. */
data class AttemptRecord(
    val iteration: Int,
    val prompt: String,
    val experience: String,
    val passed: List<String>,
    val failed: List<String>,
    val image: Bitmap
)

/** Final result returned by the orchestrator. */
data class AgentResult(
    val image: Bitmap,
    val refinedPrompt: String,
    val verifierScore: Float,
    val totalIterations: Int,
    val elapsedMs: Long
)
