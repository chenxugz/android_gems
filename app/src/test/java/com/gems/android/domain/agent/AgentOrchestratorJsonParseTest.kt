package com.gems.android.domain.agent

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the JSON array parsing logic used by AgentOrchestrator.decompose().
 * Extracted as pure function to test without Android dependencies.
 */
class AgentOrchestratorJsonParseTest {

    @Test
    fun `parses clean JSON array`() {
        val input = """["Is there a cat?", "Is the cat black?"]"""
        val result = parseJsonArray(input)
        assertEquals(listOf("Is there a cat?", "Is the cat black?"), result)
    }

    @Test
    fun `extracts JSON array from surrounding text`() {
        val input = """Here are the questions: ["Is it sunny?", "Is there a mountain?"] done."""
        val result = parseJsonArray(input)
        assertEquals(listOf("Is it sunny?", "Is there a mountain?"), result)
    }

    @Test
    fun `falls back to line splitting when no JSON array`() {
        val input = "1. Is it red?\n2. Is it round?\nNo questions here."
        val result = parseJsonArray(input)
        assertEquals(listOf("1. Is it red?", "2. Is it round?"), result)
    }

    @Test
    fun `returns empty for blank input`() {
        assertTrue(parseJsonArray("").isEmpty())
    }

    @Test
    fun `handles malformed JSON gracefully`() {
        val input = """["broken", unterminated"""
        val result = parseJsonArray(input)
        // Should fall back to line-split; no '?' in this line so empty
        assertTrue(result.isEmpty())
    }

    /** Mirror of AgentOrchestrator.parseJsonArray for testing. */
    private fun parseJsonArray(response: String): List<String> {
        val arrayPattern = Regex("""\[.*]""", RegexOption.DOT_MATCHES_ALL)
        val match = arrayPattern.find(response)
        if (match != null) {
            try {
                val jsonArray = JSONArray(match.value)
                return (0 until jsonArray.length())
                    .map { jsonArray.getString(it) }
                    .filter { it.isNotBlank() }
            } catch (_: Exception) {
                // fall through
            }
        }
        return response.lines()
            .map { it.trim() }
            .filter { '?' in it }
    }
}
