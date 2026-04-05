package com.gems.android.domain.engine

import android.graphics.Bitmap
import kotlinx.coroutines.flow.Flow

/**
 * Domain-layer interface for the multimodal LLM (Gemma 4 E4B via LiteRT).
 * Single model instance — role-switching is done via [systemPrompt].
 */
interface LlmEngine {
    /** Run inference with an optional system prompt and optional image inputs. */
    suspend fun think(
        userPrompt: String,
        systemPrompt: String? = null,
        images: List<Bitmap> = emptyList()
    ): String

    /**
     * Stream inference token-by-token. Each emitted string is a partial token.
     * Collect the flow and concatenate emissions to build the full response.
     */
    fun thinkStream(
        userPrompt: String,
        systemPrompt: String? = null,
    ): Flow<String>
}
