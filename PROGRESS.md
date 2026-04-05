# Android GEMS — Implementation Progress

## Group A: LiteRtLmEngine — DONE
- `data/engine/LiteRtLmEngine.kt` — MediaPipe LlmInference wrapper for Gemma 4 E4B
- Singleton, lazy-loaded, Mutex-protected, Gemma chat format prompt construction
- Base64 JPEG vision encoding for multimodal input

## Group B: MobileDiffusionEngine — DONE
- `data/engine/MobileDiffusionEngine.kt` ��� MediaPipe ImageGenerator wrapper
- 512x512 output, lazy init, configurable model directory

## Group C: SkillManager + Assets — DONE
- `data/skill/AssetSkillManager.kt` — loads SKILL.md from assets/skills/
- 3 skills: cinematic, portrait, landscape
- Frontmatter parser for SKILL_ID, TITLE, DESCRIPTION, KEYWORDS

## Group D: AgentMemory + Room — DONE
- `data/memory/AttemptEntity.kt`, `SessionSummaryEntity.kt` — Room entities
- `AgentDao.kt` — DAO with session queries
- `AgentDatabase.kt` — Room database
- `RoomAgentMemory.kt` — saves Bitmaps to internal storage, paths in Room
- `TrajectoryCompressionWorker.kt` — WorkManager job, keeps last 5 sessions

## Group E: Compose UI — DONE
- `ui/theme/Color.kt`, `Theme.kt` — Material 3 with dynamic color
- `ui/MainActivity.kt` — @AndroidEntryPoint, edge-to-edge
- `ui/navigation/NavGraph.kt` — Home ↔ Comparison routes
- `ui/screen/HomeScreen.kt` — prompt input
- `ui/screen/ComparisonScreen.kt` — side-by-side pipeline comparison
- `ui/screen/ComparisonViewModel.kt` — parallel pipeline execution

## Core Agent Loop — DONE
- `domain/agent/AgentOrchestrator.kt` — port of GEMS.py
- `domain/engine/LlmEngine.kt`, `ImageGenEngine.kt` �� interfaces
- `domain/model/AgentModels.kt` — AgentRole, Verification, AttemptRecord, AgentResult
- `domain/skill/SkillManager.kt`, `domain/memory/AgentMemory.kt` — interfaces

## DI Modules — DONE
- `data/di/EngineModule.kt` — LlmEngine + ImageGenEngine bindings
- `data/di/SkillModule.kt` — SkillManager binding
- `data/di/MemoryModule.kt` — Room DB + AgentMemory binding

## Build Status
- `./gradlew assembleDebug` — PASSING
- `./gradlew testDebugUnitTest` — 11/11 PASSING
- `./gradlew connectedDebugAndroidTest` — 13 PASSED, 4 SKIPPED, 0 FAILED

## Device Test Results — Pixel 9 (Android 16, SDK 36, Tensor G4, 11GB RAM)

### Instrumented Tests (2026-04-04)
| Test | Result | Time |
|---|---|---|
| LiteRtLmEngine: instantiates | PASS | 12ms |
| LiteRtLmEngine: path configurable | PASS | 1ms |
| LiteRtLmEngine: loads without OOM | SKIPPED (Gemma model not on device) | — |
| LiteRtLmEngine: returns valid JSON | SKIPPED (Gemma model not on device) | — |
| MobileDiffusionEngine: instantiates | PASS | 32ms |
| MobileDiffusionEngine: 512x512 bitmap | SKIPPED (GPU context_) | — |
| MobileDiffusionEngine: within 2s | SKIPPED (GPU context_) | — |
| RoomAgentMemory: save+load attempt | PASS | 32ms |
| RoomAgentMemory: ordered by iteration | PASS | 170ms |
| RoomAgentMemory: session summary | PASS | 28ms |
| RoomAgentMemory: empty for unknown | PASS | 1ms |
| RoomAgentMemory: compression deletes old | PASS | 155ms |
| AssetSkillManager: loads 3 skills | PASS | 2ms |
| AssetSkillManager: manifest contents | PASS | 1ms |
| AssetSkillManager: cinematic instructions | PASS | 1ms |
| AssetSkillManager: unknown returns null | PASS | 1ms |
| AssetSkillManager: all have instructions | PASS | 2ms |

### Known Issues
- **MobileDiffusion GPU context failure**: MediaPipe Image Generator (deprecated)
  fails with `RET_CHECK failure ... context_` on Pixel 9 / Tensor G4 / Android 16.
  The `StableDiffusionIterateCalculator` cannot create an OpenCL context.
  Tested with MediaPipe versions 0.10.14, 0.10.20, 0.10.21, 0.10.26.1 — same result.
  This is a compatibility issue between the deprecated MediaPipe Image Generator
  and the Tensor G4 GPU driver on Android 16.
- **Gemma model not yet on device**: Requires Hugging Face login to accept license
  for `google/gemma-3n-E4B-it-litert-lm`. File: `gemma-3n-E4B-it-int4.litertlm`.

### Model Files on Device
- MobileDiffusion: 1040 files, 1.9GB at `/data/local/tmp/image_generator/bins/`
- Gemma 4 E4B: Not yet downloaded (requires HuggingFace license acceptance)
