package com.gems.android.data.skill

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Pure JVM tests for SKILL.md frontmatter parsing. */
class SkillManagerParsingTest {

    private val validSkillMd = """
        ---
        SKILL_ID: cinematic
        TITLE: Cinematic Style
        DESCRIPTION: Generates images with cinematic lighting and film grain
        KEYWORDS: cinematic, film, movie, dramatic
        ---

        # Instructions
        Use dramatic lighting with strong shadows.
        Apply a warm color grade.
    """.trimIndent()

    @Test
    fun `extracts all frontmatter fields`() {
        val entry = AssetSkillManager.parseSkillMd(validSkillMd)

        assertNotNull(entry)
        assertEquals("cinematic", entry!!.id)
        assertEquals("Cinematic Style", entry.title)
        assertEquals("Generates images with cinematic lighting and film grain", entry.description)
        assertEquals(listOf("cinematic", "film", "movie", "dramatic"), entry.keywords)
    }

    @Test
    fun `extracts instructions body`() {
        val entry = AssetSkillManager.parseSkillMd(validSkillMd)

        assertNotNull(entry)
        assert(entry!!.instructions.contains("dramatic lighting"))
        assert(entry.instructions.contains("warm color grade"))
    }

    @Test
    fun `returns null for missing frontmatter delimiter`() {
        assertNull(AssetSkillManager.parseSkillMd("No frontmatter here"))
    }

    @Test
    fun `returns null for missing SKILL_ID`() {
        val noId = """
            ---
            TITLE: Test
            DESCRIPTION: Test desc
            ---
            body
        """.trimIndent()
        assertNull(AssetSkillManager.parseSkillMd(noId))
    }

    @Test
    fun `handles missing optional fields gracefully`() {
        val minimalMd = """
            ---
            SKILL_ID: minimal
            ---
            body text
        """.trimIndent()
        val entry = AssetSkillManager.parseSkillMd(minimalMd)

        assertNotNull(entry)
        assertEquals("minimal", entry!!.id)
        assertEquals("minimal", entry.title) // defaults to id
        assertEquals("", entry.description)
        assertEquals(emptyList<String>(), entry.keywords)
    }

    @Test
    fun `handles keywords with extra whitespace`() {
        val md = """
            ---
            SKILL_ID: test
            KEYWORDS:  foo ,  bar , baz
            ---
            body
        """.trimIndent()
        val entry = AssetSkillManager.parseSkillMd(md)
        assertEquals(listOf("foo", "bar", "baz"), entry!!.keywords)
    }
}
