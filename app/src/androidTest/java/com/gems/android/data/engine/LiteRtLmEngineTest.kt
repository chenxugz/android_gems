package com.gems.android.data.engine

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * Instrumented tests for [LiteRtLmEngine] on a physical device.
 * These tests require the Gemma 4 E4B model file to be present on device.
 */
@RunWith(AndroidJUnit4::class)
class LiteRtLmEngineTest {

    private lateinit var engine: LiteRtLmEngine
    private lateinit var modelFile: File

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        engine = LiteRtLmEngine(context)
        modelFile = File(engine.modelPath)

        // Also check dev path for models pushed via adb
        if (!modelFile.exists()) {
            val devPath = "/data/local/tmp/${LiteRtLmEngine.DEFAULT_MODEL_FILENAME}"
            val devFile = File(devPath)
            if (devFile.exists()) {
                engine.modelPath = devPath
                modelFile = devFile
            }
        }
    }

    @Test
    fun engineInstantiatesWithoutCrash() {
        assertNotNull(engine)
    }

    @Test
    fun modelFilePathIsConfigurable() {
        val customPath = "/data/local/tmp/custom_model.bin"
        engine.modelPath = customPath
        assertTrue(engine.modelPath == customPath)
    }

    @Test
    fun loadsModelWithoutOOM() {
        assumeTrue(
            "Skipping: model file not found at ${modelFile.absolutePath}",
            modelFile.exists()
        )

        runBlocking {
            val startMs = System.currentTimeMillis()
            val response = engine.think(userPrompt = "Hello")
            val elapsed = System.currentTimeMillis() - startMs

            Log.i("LiteRtLmEngineTest", "First inference (includes load): ${elapsed}ms")
            assertNotNull(response)
            assertTrue("Response should not be blank", response.isNotBlank())

            // Log timing — E4B on CPU/XNNPack is slow; this test validates no OOM crash
            Log.i("LiteRtLmEngineTest", "Model load + first inference: ${elapsed}ms")
        }
    }

    @Test
    fun returnsValidJsonFromStructuredPrompt() {
        assumeTrue(
            "Skipping: model file not found at ${modelFile.absolutePath}",
            modelFile.exists()
        )

        runBlocking {
            val response = engine.think(
                userPrompt = "List 3 colors as a JSON array. Respond ONLY with the JSON array.",
                systemPrompt = "You are a JSON response agent. Respond only in valid JSON."
            )
            Log.i("LiteRtLmEngineTest", "JSON response: $response")
            assertNotNull(response)
            assertTrue("Response should contain '[' for JSON array", response.contains("["))
        }
    }
}
