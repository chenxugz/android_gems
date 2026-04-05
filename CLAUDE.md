# GEMS Android

## Project Goal
Port GEMS (Agent-Native Multimodal Generation with Memory and Skills)
to run fully on-device on Android.

## Source Architecture — Read These First
- Paper: https://arxiv.org/abs/2603.28088
- GitHub: https://github.com/lcqysl/GEMS
- Key files to understand:
  - agent/GEMS.py         ← core agent loop logic to port
  - agent/base_agent.py   ← base interfaces
  - agent/skills/         ← SKILL.md format we replicate in assets/skills/

## How GEMS Works (summary for context)
GEMS has three pillars:
1. Agent Loop — Planner → Generator → Verifier → Refiner closed loop
2. Agent Memory — persistent hierarchical trajectory compression
3. Agent Skill — on-demand SKILL.md domain expertise loading

Original stack: Kimi-K2.5 (MLLM) + Z-Image-Turbo (image generator)
Our Android stack: Gemma 4 E4B (MLLM) + MobileDiffusion (image generator)

## Our Android Stack
- Language: Kotlin only, minSdk 26, targetSdk 35
- UI: Jetpack Compose + Material 3
- DI: Hilt
- DB: Room (Agent Memory persistence)
- Async: Coroutines + Flow only (no LiveData)
- LLM: LiteRT-LM running Gemma 4 E4B (replaces Kimi-K2.5)
- Image gen: MediaPipe Image Generator running MobileDiffusion (replaces Z-Image-Turbo)

## Module Mapping (GEMS → Android)
| GEMS (Python)         | Android (Kotlin)            |
|-----------------------|-----------------------------|
| GEMS.py agent loop    | AgentOrchestrator.kt        |
| base_agent.py         | BaseAgent.kt                |
| Agent Memory          | AgentMemory.kt + Room DB    |
| SkillManager          | SkillManager.kt             |
| agent/skills/*.md     | assets/skills/*/SKILL.md    |
| Kimi-K2.5 server      | LiteRtLmEngine.kt           |
| Z-Image-Turbo server  | MobileDiffusionEngine.kt    |

## Architecture Rules
- Clean Architecture: UI / Domain / Data layers strictly separated
- AgentOrchestrator lives in Domain layer only
- No business logic in Composables
- ViewModels expose StateFlow only
- All LLM calls return structured JSON — always parse with fallback retry
- Never hold full trajectory in RAM; persist to Room after each step
- Max 2 agent loop iterations (mobile constraint vs GEMS's 3)
- MobileDiffusion output Bitmap fed back into Gemma 4 vision input for Verifier

## Key Constraints (mobile vs server)
- Single model instance only — role-switch Gemma 4 via system prompt injection
  (GEMS runs separate server processes; we cannot afford multiple model loads)
- Gemma 4 E4B supports 128K context — use it freely for memory history
- Compress trajectories older than 5 sessions in Room DB for fast retrieval,
  not because of model context limits but to keep DB reads efficient
- Thermal: 500ms cooldown between agent loop passes
- Image output: 512x512 (MobileDiffusion limit)

## Comparison Milestone
Build ComparisonScreen.kt as a standalone Compose screen.
Run both pipelines in parallel using two coroutines:
- pipelineA: prompt → MobileDiffusionEngine → Bitmap
- pipelineB: prompt → AgentOrchestrator → Bitmap

Display results side by side with:
- Both generated images at equal size
- Generation time per pipeline
- Original prompt
- GEMS refined prompt produced by the Planner
- Verifier score from the final iteration

This screen is the primary validation of the agent loop's value.
Do not gate it behind the full UI — it must work as a standalone screen
so the agent loop improvement is visible and testable independently of
the rest of the app.

## Parallel Development Guidelines
Decompose the implementation into a dependency graph before starting.
Identify which modules have no dependencies on each other and spawn
parallel sub-agents to implement them simultaneously.

Dependency graph for this project:

```
LiteRtLmEngine.kt ──────────────────────────┐
                                             ▼
MobileDiffusionEngine.kt ───────────► AgentOrchestrator.kt ──► ComparisonScreen.kt
                                             ▼
BaseAgent.kt ────────────────────────► AgentMemory.kt ─────► AgentViewModel.kt
                                             ▼
SkillManager.kt + assets/skills/ ──────────► AgentOrchestrator.kt
```

Modules that can be built in parallel (no mutual dependency):
- Group A: LiteRtLmEngine.kt + LiteRtLmEngine tests
- Group B: MobileDiffusionEngine.kt + MobileDiffusionEngine tests
- Group C: SkillManager.kt + all SKILL.md assets
- Group D: AgentMemory.kt + Room schema + WorkManager compression job
- Group E: Jetpack Compose UI scaffolding (screens, nav graph, theme)

Spawn one sub-agent per group. Each sub-agent should:
- Work only within its assigned files and module boundaries
- Run ./gradlew :<module>:build on completion to verify
- Commit its work independently with a scoped commit message
- Write its result to PROGRESS.md under its group label

Only start AgentOrchestrator.kt and ComparisonScreen.kt after
Groups A, B, C, and D have all committed successfully, as these
have hard dependencies on all four.

## Real Device Test Plan
All tests must be executed on a physical Android device via ADB.
Do not rely solely on the emulator — emulators cannot accurately
simulate GPU/NPU inference, thermal throttling, or RAM pressure.

### Unit Tests (run on emulator or host JVM)
Write and run these for pure logic with no hardware dependency:
- SkillManager: correct SKILL.md parsing and keyword routing
- AgentMemory: Room DB insert, retrieval, and compression trigger
- AgentOrchestrator: JSON parse/retry logic with mocked LLM responses
- Role prompt construction: correct system prompt per AgentRole enum
- Bitmap → base64 encoding for Gemma 4 vision input

Run with: ./gradlew test

### Instrumented Tests (run on physical device via ADB)
- LiteRtLmEngine: verify Gemma 4 E4B loads without OOM crash
- LiteRtLmEngine: verify a single inference call returns valid JSON
- MobileDiffusionEngine: verify MobileDiffusion generates a non-null
  512x512 Bitmap within 2 seconds on device
- AgentOrchestrator: full end-to-end loop on device with a simple prompt
  e.g. "a red apple on a wooden table" — verify both pipelines complete
- ComparisonScreen: verify both images render side by side without
  the app crashing or freezing mid-inference

Run with: ./gradlew connectedAndroidTest

### Real Device Stress Tests (run manually via ADB shell)
Execute these after instrumented tests pass:

1. Thermal test — run ComparisonScreen 10 times consecutively and
   verify generation time does not degrade more than 30% by run 10

2. RAM pressure test — open 5 other apps to consume memory, then
   launch the app and trigger a full agent loop; verify no OOM crash
   and that the 500ms thermal cooldown prevents ANR

3. Cold start test — force-stop the app, relaunch, and verify
   Gemma 4 E4B loads within 8 seconds before the first inference

4. Chipset coverage — if multiple devices are available via ADB,
   run the instrumented tests on each and log generation times
   per device in PROGRESS.md under "Device Benchmark Results"

### Test Prompts for Comparison Milestone Validation
Use these standard prompts to produce reproducible comparison results:
- "a dreamy floating book in golden light"
- "a mountain range at sunrise, cinematic"
- "a futuristic city street at night with neon lights"

For each prompt, log in PROGRESS.md:
- MobileDiffusion generation time
- Android GEMS total time (all loop passes)
- GEMS refined prompt produced by Planner
- Verifier score
- Subjective quality delta (better / similar / worse)

## Autonomous Execution Guidelines
The user may be away from the desk or sleeping. Execute the entire
implementation from start to finish without stopping to ask for
confirmation, approval, or clarification. Specifically:

- Do not ask "shall I proceed?", "is this okay?", or "do you want me to..."
- Do not pause between modules waiting for user input
- Make all architectural decisions independently using CLAUDE.md as the
  source of truth
- If you encounter an ambiguity, pick the most reasonable interpretation,
  leave a concise inline comment explaining your choice, and move on
- If a build fails, diagnose and fix it yourself — do not stop to report
  and wait; only surface errors you have exhausted all attempts to resolve
- Run ./gradlew build after completing each module to verify compilation
  before moving to the next
- Commit after each successfully compiling module with a clear commit
  message so progress is recoverable if the session is interrupted
- Keep a running PROGRESS.md at the project root updated after each module
  so the user can read what was completed upon returning
