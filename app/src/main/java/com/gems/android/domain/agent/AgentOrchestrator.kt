package com.gems.android.domain.agent

import android.graphics.Bitmap
import android.util.Log
import com.gems.android.data.engine.LiteRtLmEngine
import com.gems.android.data.engine.SdCppEngine
import com.gems.android.domain.engine.LlmEngine
import com.gems.android.domain.memory.AgentMemory
import com.gems.android.domain.model.AgentResult
import com.gems.android.domain.model.AgentRole
import com.gems.android.domain.model.AttemptRecord
import com.gems.android.domain.model.Verification
import com.gems.android.domain.skill.SkillManager
import kotlinx.coroutines.delay
import org.json.JSONArray
import java.util.UUID
import javax.inject.Inject

private const val TAG = "AgentOrchestrator"
private const val DEFAULT_maxIterations = 2
private const val COOLDOWN_MS = 500L

/**
 * Android port of GEMS.py — Planner → Generator → Verifier → Refiner loop.
 *
 * On mobile, LLM and image gen both use GPU and can't coexist in memory.
 * This orchestrator manages GPU memory by closing one engine before using the other.
 */
class AgentOrchestrator @Inject constructor(
    private val llm: LlmEngine,
    private val imageGen: SdCppEngine,
    private val skillManager: SkillManager,
    private val memory: AgentMemory
) {
    /** Progress callback for UI updates. */
    interface ProgressListener {
        fun onStatus(message: String)
        fun onRoundImage(round: Int, image: Bitmap, prompt: String, score: Float?)
    }

    var progressListener: ProgressListener? = null
    var maxIterations: Int = DEFAULT_maxIterations

    private fun status(msg: String) {
        Log.d(TAG, msg)
        progressListener?.onStatus(msg)
    }

    /** Free image gen GPU memory so LLM can use it. */
    private fun prepareForLlm() {
        imageGen.close()
        System.gc()
        Thread.sleep(1000) // wait for Vulkan GPU memory to be fully released
    }

    /** Free LLM GPU memory so image gen can use it. */
    private fun prepareForImageGen() {
        (llm as? LiteRtLmEngine)?.close()
        System.gc()
        Thread.sleep(500)
    }

    /**
     * Run the GEMS agent loop, minimizing GPU model switches.
     *
     * Strategy: batch all LLM calls together, then switch to image gen once.
     * Round 1: LLM(plan+decompose) → switch → ImageGen(generate) → switch → LLM(verify+summarize+refine)
     * Round 2: switch → ImageGen(generate) → switch → LLM(verify)
     * = 4 GPU switches total (vs N switches if interleaved naively)
     */
    suspend fun run(prompt: String): AgentResult {
        val startTime = System.currentTimeMillis()
        val sessionId = UUID.randomUUID().toString()

        // ============================================================
        // LLM PHASE 1: Plan + Decompose (one GPU load)
        // ============================================================
        status("Loading LLM for planning...")
        prepareForLlm()

        status("Planning and routing skills...")
        val plannedPrompt = plan(prompt)
        var currentPrompt = plannedPrompt

        status("Decomposing requirements...")
        val questions = decompose(prompt)
        status("Found ${questions.size} requirements")

        if (questions.isEmpty()) {
            status("Generating image directly...")
            prepareForImageGen()
            val image = imageGen.generate(prompt)
            return AgentResult(image, prompt, 0f, 0,
                System.currentTimeMillis() - startTime)
        }

        var bestImage: Bitmap? = null
        var maxPassedCount = -1
        val attemptHistory = mutableListOf<AttemptRecord>()

        for (i in 1..maxIterations) {
            status("=== Round $i/$maxIterations ===")

            // ============================================================
            // IMAGE GEN PHASE: Generate image (one GPU switch)
            // ============================================================
            status("Loading image generator (round $i)...")
            prepareForImageGen()
            status("Generating image...")
            val image = imageGen.generate(currentPrompt)
            status("Image generated!")

            if (bestImage == null) bestImage = image

            // ============================================================
            // LLM PHASE: Verify + (Summarize + Refine if not last) (one GPU switch)
            // ============================================================
            status("Loading LLM for verification...")
            prepareForLlm()

            status("Verifying ${questions.size} requirements...")
            val verifications = verifyImage(image, questions)
            val passed = verifications.filter { it.passed }.map { it.question }
            val failed = verifications.filter { !it.passed }.map { it.question }

            if (passed.size > maxPassedCount) {
                maxPassedCount = passed.size
                bestImage = image
            }

            val score = passed.size.toFloat() / questions.size
            status("Score: ${(score * 100).toInt()}% (${passed.size}/${questions.size})")
            progressListener?.onRoundImage(i, image, currentPrompt, score)

            verifications.forEach { v ->
                Log.d(TAG, "  ${if (v.passed) "PASS" else "FAIL"} ${v.question}")
            }

            if (failed.isEmpty()) {
                status("All requirements met!")
                memory.saveAttempt(sessionId,
                    AttemptRecord(i, currentPrompt, "All passed", passed, failed, image))
                return AgentResult(image, currentPrompt, 1f, i,
                    System.currentTimeMillis() - startTime)
            }

            // Still in LLM phase — summarize + refine WITHOUT switching GPU
            if (i < maxIterations) {
                status("Analyzing failures and refining prompt...")
                val experience = summarizeExperience(
                    currentPrompt, passed, failed,
                    attemptHistory.lastOrNull()?.experience ?: "First attempt",
                    attemptHistory, image
                )

                val record = AttemptRecord(i, currentPrompt, experience, passed, failed, image)
                attemptHistory.add(record)
                memory.saveAttempt(sessionId, record)

                delay(COOLDOWN_MS)

                val refined = refinePrompt(prompt, attemptHistory)
                currentPrompt = refined.first
                status("Refined prompt: $currentPrompt")
                // LLM stays loaded — next iteration starts with ImageGen switch
            } else {
                memory.saveAttempt(sessionId,
                    AttemptRecord(i, currentPrompt, "Max iterations", passed, failed, image))
            }
        }

        val score = maxPassedCount.toFloat() / questions.size
        status("Done! Score: ${(score * 100).toInt()}%")
        return AgentResult(bestImage!!, currentPrompt, score, maxIterations,
            System.currentTimeMillis() - startTime)
    }

    // --- Planner ---
    // Skip skill routing on mobile to save time — directly use the original prompt.
    // Skill routing adds 2 LLM calls (~4s) and rarely triggers for simple prompts.
    private suspend fun plan(originalPrompt: String): String {
        return originalPrompt
    }

    // --- Decomposer ---
    private suspend fun decompose(prompt: String): List<String> {
        val task = buildString {
            append("Analyze the user's image generation prompt. ")
            append("Break it down into specific visual requirements. ")
            append("For each requirement, write a question answerable with yes or no. ")
            append("YOU MUST RESPOND ONLY WITH A JSON ARRAY OF STRINGS. ")
            append("Example: [\"Is there a cat?\", \"Is the cat black?\"]\n\n")
            append("User Prompt: $prompt")
        }

        val response = llm.think(
            userPrompt = task,
            systemPrompt = systemPromptFor(AgentRole.DECOMPOSER)
        ).trim()

        return parseJsonArray(response)
    }

    // --- Verifier (multimodal — sends image to Gemma 4 for visual verification) ---
    private suspend fun verifyImage(image: Bitmap, questions: List<String>): List<Verification> {
        return questions.map { question ->
            val query = "Look at this image. Answer with ONLY 'yes' or 'no': $question"
            val answer = try {
                llm.think(
                    userPrompt = query,
                    systemPrompt = systemPromptFor(AgentRole.VERIFIER),
                    images = listOf(image),
                ).lowercase().trim()
            } catch (e: Exception) {
                Log.e(TAG, "Verification failed for: $question", e)
                "error"
            }
            val passed = "yes" in answer && "no" !in answer
            Log.d(TAG, "Verify: \"$question\" -> $answer (${if (passed) "PASS" else "FAIL"})")
            Verification(question, answer, passed)
        }
    }

    // --- Experience Summarizer ---
    private suspend fun summarizeExperience(
        currentPrompt: String, passed: List<String>, failed: List<String>,
        currentThought: String, previousExperiences: List<AttemptRecord>,
        image: Bitmap
    ): String {
        val prevExpStr = if (previousExperiences.isEmpty()) "None (First round)"
        else previousExperiences.joinToString("\n") { "Round ${it.iteration}: ${it.experience}" }

        val task = buildString {
            append("Task: Summarize the experience of the current image generation attempt.\n")
            append("Prompt used: $currentPrompt\n")
            append("Passed: ${passed.joinToString(", ").ifEmpty { "None" }}\n")
            append("Failed: ${failed.joinToString(", ").ifEmpty { "None" }}\n")
            append("Previous experiences: $prevExpStr\n")
            append("Write a concise summary under 100 words of what to improve.")
        }

        return llm.think(
            userPrompt = task,
            systemPrompt = systemPromptFor(AgentRole.EXPERIENCE_SUMMARIZER),
        ).trim()
    }

    // --- Refiner ---
    private suspend fun refinePrompt(
        originalPrompt: String, history: List<AttemptRecord>
    ): Pair<String, String> {
        val historyLog = history.joinToString("\n") { record ->
            buildString {
                append("Attempt ${record.iteration}:\n")
                append("- Experience: ${record.experience}\n")
                append("- Prompt: ${record.prompt}\n")
                append("- Failed: ${record.failed.joinToString(", ").ifEmpty { "None" }}\n")
            }
        }

        val task = buildString {
            append("Refine the image generation prompt based on previous attempts.\n")
            append("Original Intent: $originalPrompt\n\n")
            append("--- ATTEMPT HISTORY ---\n$historyLog\n")
            append("Rewrite a comprehensive prompt that:\n")
            append("1. Reinforces failed requirements.\n")
            append("2. Maintains successful requirements.\n")
            append("3. Uses clear, descriptive language.\n\n")
            append("Return ONLY the prompt text itself.")
        }

        val refined = llm.think(
            userPrompt = task,
            systemPrompt = systemPromptFor(AgentRole.REFINER),
        ).trim()

        val thought = history.lastOrNull()?.experience ?: ""
        return Pair(refined, thought)
    }

    // --- Helpers ---
    private fun systemPromptFor(role: AgentRole): String = when (role) {
        AgentRole.PLANNER -> "You are a strategic planning agent. Route requests to skills or return NONE."
        AgentRole.DECOMPOSER -> "You are a requirements agent. Break prompts into yes/no questions. Respond only in JSON."
        AgentRole.VERIFIER -> "You are a verification agent. Answer only 'yes' or 'no'."
        AgentRole.EXPERIENCE_SUMMARIZER -> "You are a summarization agent. Be concise, under 100 words."
        AgentRole.REFINER -> "You are a prompt refinement agent. Rewrite prompts to fix failures."
    }

    private fun parseJsonArray(response: String): List<String> {
        val arrayPattern = Regex("""\[.*]""", RegexOption.DOT_MATCHES_ALL)
        val match = arrayPattern.find(response)
        if (match != null) {
            try {
                val jsonArray = JSONArray(match.value)
                return (0 until jsonArray.length())
                    .map { jsonArray.getString(it) }
                    .filter { it.isNotBlank() }
            } catch (_: Exception) { }
        }
        return response.lines().map { it.trim() }.filter { '?' in it }
    }
}
