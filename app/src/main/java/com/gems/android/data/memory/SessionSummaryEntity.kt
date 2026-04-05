package com.gems.android.data.memory

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity storing the compressed experience summary for a completed session.
 * After compression, individual [AttemptEntity] rows for this session are deleted
 * and only this summary remains.
 */
@Entity(tableName = "session_summaries")
data class SessionSummaryEntity(
    @PrimaryKey
    val sessionId: String,
    val summary: String,
    val timestamp: Long = System.currentTimeMillis()
)
