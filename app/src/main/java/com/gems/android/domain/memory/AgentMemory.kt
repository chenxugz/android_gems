package com.gems.android.domain.memory

import com.gems.android.domain.model.AttemptRecord

/** Domain-layer interface for persisting agent trajectory history to Room. */
interface AgentMemory {
    /** Persist one attempt record for a given session. */
    suspend fun saveAttempt(sessionId: String, record: AttemptRecord)

    /** Load all attempt records for a session, ordered by iteration. */
    suspend fun loadAttempts(sessionId: String): List<AttemptRecord>

    /** Persist the final experience summary for a completed session. */
    suspend fun saveSessionSummary(sessionId: String, summary: String)
}
