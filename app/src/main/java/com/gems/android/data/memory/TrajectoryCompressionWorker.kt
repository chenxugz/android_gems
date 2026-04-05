package com.gems.android.data.memory

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.io.File

/**
 * WorkManager job that compresses old agent trajectories.
 *
 * "Compression" means: for sessions older than the most recent [KEEP_RECENT] sessions,
 * delete the individual [AttemptEntity] rows and their associated bitmap files on disk.
 * The [SessionSummaryEntity] is intentionally kept so that the agent memory can still
 * reference the high-level experience summary.
 *
 * Per CLAUDE.md: this compression is for DB read efficiency, not because of model
 * context limits (Gemma 4 E4B supports 128K context).
 */
class TrajectoryCompressionWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        /** Number of most-recent sessions whose attempt rows are preserved. */
        const val KEEP_RECENT = 5
        const val WORK_NAME = "trajectory_compression"
    }

    override suspend fun doWork(): Result {
        val db = androidx.room.Room.databaseBuilder(
            applicationContext,
            AgentDatabase::class.java,
            "agent_db"
        ).build()

        return try {
            val dao = db.agentDao()
            val allSessionIds = dao.getAllAttemptSessionIdsOldestFirst()

            // Only compress if there are more sessions than we want to keep.
            if (allSessionIds.size <= KEEP_RECENT) {
                return Result.success()
            }

            val sessionsToCompress = allSessionIds.dropLast(KEEP_RECENT)

            for (sessionId in sessionsToCompress) {
                // Delete bitmap files from internal storage first.
                val imagePaths = dao.getImagePathsBySession(sessionId)
                for (path in imagePaths) {
                    val file = File(path)
                    if (file.exists()) {
                        file.delete()
                    }
                }
                // Remove the attempt rows; session summary is kept.
                dao.deleteAttemptsBySession(sessionId)
            }

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        } finally {
            db.close()
        }
    }
}
