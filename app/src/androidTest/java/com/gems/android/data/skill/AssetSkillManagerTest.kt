package com.gems.android.data.skill

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for [AssetSkillManager] — verifies correct SKILL.md
 * parsing and keyword routing from actual assets.
 */
@RunWith(AndroidJUnit4::class)
class AssetSkillManagerTest {

    private lateinit var manager: AssetSkillManager

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        manager = AssetSkillManager(context)
    }

    @Test
    fun loadsAllThreeSkills() {
        val ids = manager.getSkillIds()
        assertEquals(3, ids.size)
        assertTrue("cinematic" in ids)
        assertTrue("portrait" in ids)
        assertTrue("landscape" in ids)
    }

    @Test
    fun manifestContainsAllSkillIds() {
        val manifest = manager.getSkillManifest()
        assertTrue(manifest.contains("cinematic"))
        assertTrue(manifest.contains("portrait"))
        assertTrue(manifest.contains("landscape"))
    }

    @Test
    fun cinematicSkillHasInstructions() {
        val instructions = manager.getSkillInstructions("cinematic")
        assertNotNull(instructions)
        assertTrue(instructions!!.isNotBlank())
    }

    @Test
    fun unknownSkillReturnsNull() {
        assertNull(manager.getSkillInstructions("nonexistent_skill"))
    }

    @Test
    fun allSkillsHaveNonEmptyInstructions() {
        for (id in manager.getSkillIds()) {
            val instructions = manager.getSkillInstructions(id)
            assertNotNull("Skill $id should have instructions", instructions)
            assertTrue("Skill $id instructions should not be blank", instructions!!.isNotBlank())
        }
    }
}
