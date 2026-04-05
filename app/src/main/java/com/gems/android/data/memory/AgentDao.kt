package com.gems.android.data.memory

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AgentDao {

    @Insert
    suspend fun insertAttempt(entity: AttemptEntity)

    @Query("SELECT * FROM attempts WHERE sessionId = :sessionId ORDER BY iteration ASC")
    suspend fun getAttemptsBySession(sessionId: String): List<AttemptEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSessionSummary(entity: SessionSummaryEntity)

    @Query("SELECT * FROM session_summaries WHERE sessionId = :sessionId LIMIT 1")
    suspend fun getSessionSummary(sessionId: String): SessionSummaryEntity?

    /**
     * Returns distinct session IDs that have attempt records, ordered oldest-first.
     * Used by [TrajectoryCompressionWorker] to find sessions eligible for compression.
     */
    @Query(
        """
        SELECT DISTINCT sessionId FROM attempts
        ORDER BY timestamp ASC
        """
    )
    suspend fun getAllAttemptSessionIdsOldestFirst(): List<String>

    /**
     * Returns the image file paths for a session so the worker can delete
     * the bitmap files from internal storage before removing the DB rows.
     */
    @Query("SELECT imagePath FROM attempts WHERE sessionId = :sessionId")
    suspend fun getImagePathsBySession(sessionId: String): List<String>

    @Query("DELETE FROM attempts WHERE sessionId = :sessionId")
    suspend fun deleteAttemptsBySession(sessionId: String)
}
