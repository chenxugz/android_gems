package com.gems.android.data.di

import android.content.Context
import androidx.room.Room
import com.gems.android.data.memory.AgentDao
import com.gems.android.data.memory.AgentDatabase
import com.gems.android.data.memory.RoomAgentMemory
import com.gems.android.domain.memory.AgentMemory
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class MemoryModule {

    @Binds
    @Singleton
    abstract fun bindAgentMemory(impl: RoomAgentMemory): AgentMemory

    companion object {
        @Provides
        @Singleton
        fun provideAgentDatabase(@ApplicationContext context: Context): AgentDatabase {
            return Room.databaseBuilder(
                context,
                AgentDatabase::class.java,
                "agent_db"
            ).build()
        }

        @Provides
        fun provideAgentDao(database: AgentDatabase): AgentDao {
            return database.agentDao()
        }
    }
}
