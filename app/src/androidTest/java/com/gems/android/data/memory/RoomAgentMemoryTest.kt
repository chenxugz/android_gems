package com.gems.android.data.memory

import android.graphics.Bitmap
import android.graphics.Color
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gems.android.domain.model.AttemptRecord
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for [RoomAgentMemory] — Room DB insert, retrieval,
 * and compression trigger logic.
 */
@RunWith(AndroidJUnit4::class)
class RoomAgentMemoryTest {

    private lateinit var database: AgentDatabase
    private lateinit var memory: RoomAgentMemory

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, AgentDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        memory = RoomAgentMemory(database.agentDao(), context)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun saveAndLoadAttempt() = runBlocking {
        val bitmap = createTestBitmap()
        val record = AttemptRecord(
            iteration = 1,
            prompt = "a red apple",
            experience = "Apple was generated correctly",
            passed = listOf("Is there an apple?"),
            failed = listOf("Is it red?"),
            image = bitmap
        )

        memory.saveAttempt("session-1", record)
        val loaded = memory.loadAttempts("session-1")

        assertEquals(1, loaded.size)
        assertEquals(1, loaded[0].iteration)
        assertEquals("a red apple", loaded[0].prompt)
        assertEquals("Apple was generated correctly", loaded[0].experience)
        assertEquals(listOf("Is there an apple?"), loaded[0].passed)
        assertEquals(listOf("Is it red?"), loaded[0].failed)
        assertEquals(512, loaded[0].image.width)
        assertEquals(512, loaded[0].image.height)
    }

    @Test
    fun multipleAttemptsOrderedByIteration() = runBlocking {
        for (i in 1..3) {
            memory.saveAttempt("session-2", AttemptRecord(
                iteration = i,
                prompt = "prompt $i",
                experience = "exp $i",
                passed = emptyList(),
                failed = emptyList(),
                image = createTestBitmap()
            ))
        }

        val loaded = memory.loadAttempts("session-2")
        assertEquals(3, loaded.size)
        assertEquals(1, loaded[0].iteration)
        assertEquals(2, loaded[1].iteration)
        assertEquals(3, loaded[2].iteration)
    }

    @Test
    fun saveSessionSummary() = runBlocking {
        memory.saveSessionSummary("session-3", "Overall good results")
        val dao = database.agentDao()
        val summary = dao.getSessionSummary("session-3")
        assertEquals("Overall good results", summary?.summary)
    }

    @Test
    fun loadAttemptsReturnsEmptyForUnknownSession() = runBlocking {
        val loaded = memory.loadAttempts("nonexistent")
        assertTrue(loaded.isEmpty())
    }

    @Test
    fun compressionDeletesOldAttempts() = runBlocking {
        val dao = database.agentDao()

        // Create 7 sessions (more than KEEP_RECENT=5)
        for (s in 1..7) {
            memory.saveAttempt("session-$s", AttemptRecord(
                iteration = 1,
                prompt = "prompt",
                experience = "exp",
                passed = emptyList(),
                failed = emptyList(),
                image = createTestBitmap()
            ))
            memory.saveSessionSummary("session-$s", "summary $s")
        }

        // Simulate what TrajectoryCompressionWorker does
        val allSessions = dao.getAllAttemptSessionIdsOldestFirst()
        assertEquals(7, allSessions.size)

        val toCompress = allSessions.dropLast(TrajectoryCompressionWorker.KEEP_RECENT)
        assertEquals(2, toCompress.size) // sessions 1 and 2

        for (sessionId in toCompress) {
            dao.deleteAttemptsBySession(sessionId)
        }

        // Old sessions should have no attempts but keep summaries
        assertTrue(dao.getAttemptsBySession("session-1").isEmpty())
        assertTrue(dao.getAttemptsBySession("session-2").isEmpty())
        assertEquals("summary 1", dao.getSessionSummary("session-1")?.summary)

        // Recent sessions should still have attempts
        assertEquals(1, dao.getAttemptsBySession("session-3").size)
        assertEquals(1, dao.getAttemptsBySession("session-7").size)
    }

    private fun createTestBitmap(): Bitmap {
        val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.RED)
        return bitmap
    }
}
