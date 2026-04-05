package com.gems.android.domain.skill

/** Manages SKILL.md files loaded from assets/skills/. */
interface SkillManager {
    /** Returns a formatted manifest of all available skills for the planner. */
    fun getSkillManifest(): String

    /** Returns the skill instructions for a given skill ID, or null if not found. */
    fun getSkillInstructions(skillId: String): String?

    /** Returns the set of all known skill IDs. */
    fun getSkillIds(): Set<String>
}
