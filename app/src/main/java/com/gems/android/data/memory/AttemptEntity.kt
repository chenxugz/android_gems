package com.gems.android.data.memory

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity storing a single agent loop attempt record.
 * Bitmap is saved to internal storage; only the file path is persisted here.
 * passed/failed lists are serialized as JSON strings via [StringListConverter].
 */
@Entity(tableName = "attempts")
data class AttemptEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: String,
    val iteration: Int,
    val prompt: String,
    val experience: String,
    val passedJson: String,
    val failedJson: String,
    val imagePath: String,
    val timestamp: Long = System.currentTimeMillis()
)
