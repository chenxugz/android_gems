package com.gems.android.data.memory

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [AttemptEntity::class, SessionSummaryEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AgentDatabase : RoomDatabase() {
    abstract fun agentDao(): AgentDao
}
