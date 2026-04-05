package com.gems.android.data.memory

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.gems.android.domain.memory.AgentMemory
import com.gems.android.domain.model.AttemptRecord
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-backed implementation of [AgentMemory].
 *
 * Bitmaps are saved as PNG files under the app's internal storage directory
 * `agent_images/`. Only the file path is stored in Room to avoid blob overhead.
 */
@Singleton
class RoomAgentMemory @Inject constructor(
    private val dao: AgentDao,
    @ApplicationContext private val context: Context
) : AgentMemory {

    private val imageDir: File by lazy {
        File(context.filesDir, "agent_images").also { it.mkdirs() }
    }

    override suspend fun saveAttempt(sessionId: String, record: AttemptRecord) {
        val imagePath = saveBitmap(record.image)
        val entity = AttemptEntity(
            sessionId = sessionId,
            iteration = record.iteration,
            prompt = record.prompt,
            experience = record.experience,
            passedJson = toJson(record.passed),
            failedJson = toJson(record.failed),
            imagePath = imagePath
        )
        dao.insertAttempt(entity)
    }

    override suspend fun loadAttempts(sessionId: String): List<AttemptRecord> {
        return dao.getAttemptsBySession(sessionId).mapNotNull { entity ->
            val bitmap = loadBitmap(entity.imagePath) ?: return@mapNotNull null
            AttemptRecord(
                iteration = entity.iteration,
                prompt = entity.prompt,
                experience = entity.experience,
                passed = fromJson(entity.passedJson),
                failed = fromJson(entity.failedJson),
                image = bitmap
            )
        }
    }

    override suspend fun saveSessionSummary(sessionId: String, summary: String) {
        dao.insertSessionSummary(
            SessionSummaryEntity(sessionId = sessionId, summary = summary)
        )
    }

    // ---- Bitmap helpers ----

    private fun saveBitmap(bitmap: Bitmap): String {
        val file = File(imageDir, "${UUID.randomUUID()}.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        return file.absolutePath
    }

    private fun loadBitmap(path: String): Bitmap? {
        val file = File(path)
        if (!file.exists()) return null
        return BitmapFactory.decodeFile(path)
    }

    // ---- JSON helpers for List<String> ----

    private fun toJson(list: List<String>): String {
        return JSONArray(list).toString()
    }

    private fun fromJson(json: String): List<String> {
        val array = JSONArray(json)
        return (0 until array.length()).map { array.getString(it) }
    }
}
