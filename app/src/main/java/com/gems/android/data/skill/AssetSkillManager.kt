package com.gems.android.data.skill

import android.content.Context
import com.gems.android.domain.skill.SkillManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loads SKILL.md files from assets/skills/<skill_id>/SKILL.md at init time
 * and serves them through the [SkillManager] interface.
 *
 * Each SKILL.md is expected to have YAML-like frontmatter delimited by "---" lines,
 * containing SKILL_ID, TITLE, DESCRIPTION, and KEYWORDS fields, followed by an
 * instructions body.
 */
@Singleton
class AssetSkillManager @Inject constructor(
    @ApplicationContext private val context: Context
) : SkillManager {

    data class SkillEntry(
        val id: String,
        val title: String,
        val description: String,
        val keywords: List<String>,
        val instructions: String
    )

    private val skills: Map<String, SkillEntry> = loadAllSkills()

    // ── SkillManager implementation ───────────────────────────────────────

    override fun getSkillManifest(): String {
        if (skills.isEmpty()) return "No skills available."
        return buildString {
            appendLine("Available Skills:")
            appendLine("─".repeat(40))
            for ((_, entry) in skills) {
                appendLine("• [${entry.id}] ${entry.title}")
                appendLine("  ${entry.description}")
                appendLine("  Keywords: ${entry.keywords.joinToString(", ")}")
                appendLine()
            }
        }.trimEnd()
    }

    override fun getSkillInstructions(skillId: String): String? {
        return skills[skillId]?.instructions
    }

    override fun getSkillIds(): Set<String> = skills.keys

    // ── Internal loading ──────────────────────────────────────────────────

    private fun loadAllSkills(): Map<String, SkillEntry> {
        val result = mutableMapOf<String, SkillEntry>()
        val skillDirs = try {
            context.assets.list("skills") ?: emptyArray()
        } catch (_: Exception) {
            emptyArray()
        }

        for (dir in skillDirs) {
            val path = "skills/$dir/SKILL.md"
            val entry = try {
                val raw = context.assets.open(path).bufferedReader().use { it.readText() }
                parseSkillMd(raw)
            } catch (_: Exception) {
                // Skip directories that don't contain a valid SKILL.md
                null
            }
            if (entry != null) {
                result[entry.id] = entry
            }
        }
        return result
    }

    /**
     * Parses a SKILL.md file with frontmatter delimited by "---" lines.
     *
     * Expected format:
     * ```
     * ---
     * SKILL_ID: value
     * TITLE: value
     * DESCRIPTION: value
     * KEYWORDS: comma, separated, values
     * ---
     *
     * # Instructions
     * ...body text...
     * ```
     */
    companion object {
        /** Visible for testing — no Android dependencies needed. */
        internal fun parseSkillMd(raw: String): SkillEntry? {
        val trimmed = raw.trimStart()
        if (!trimmed.startsWith("---")) return null

        // Find the closing "---" delimiter (skip the opening one)
        val afterFirst = trimmed.indexOf('\n') + 1
        val closingIndex = trimmed.indexOf("\n---", afterFirst)
        if (closingIndex < 0) return null

        val frontmatter = trimmed.substring(afterFirst, closingIndex)
        val body = trimmed.substring(closingIndex + 4).trim() // skip past "\n---"

        val fields = mutableMapOf<String, String>()
        for (line in frontmatter.lines()) {
            val colonIndex = line.indexOf(':')
            if (colonIndex > 0) {
                val key = line.substring(0, colonIndex).trim().uppercase()
                val value = line.substring(colonIndex + 1).trim()
                fields[key] = value
            }
        }

        val id = fields["SKILL_ID"] ?: return null
        val title = fields["TITLE"] ?: id
        val description = fields["DESCRIPTION"] ?: ""
        val keywords = fields["KEYWORDS"]
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()

        return SkillEntry(
            id = id,
            title = title,
            description = description,
            keywords = keywords,
            instructions = body
        )
    }
    }
}
