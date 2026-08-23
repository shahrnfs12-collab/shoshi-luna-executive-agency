# SHOSHI + LUNA — Deep Audit & Fix Report v1.9

## Verdict
This package is a corrected static baseline. It is **not** declared runtime-verified in this environment.

### What was actually broken
1. The user-facing Communication tab did not route free-text messages to OpenAI. It displayed internal CollaborationBus conversations only.
2. `ChatControlEngine` treated arbitrary text as an unknown local command; it was not a real user AI chat path.
3. SHOSHI and LUNA shared a generic LLM system identity, so selecting an executive did not actually create two distinct AI identities.
4. AI health could be shown as ready merely because an API key existed; no validated provider response was required.
5. Voice settings exposed the entire device TTS catalog and had no canonical Hebrew-only catalog.
6. TTS state had no utterance completion listener, so the UI could remain stuck in SPEAKING.
7. The old instrumentation smoke test still asserted the obsolete package `com.example.shoshilone`.
8. Phase-6 verification scripts still failed solely because GLB assets were missing, even though 2D has now been designated as the production presentation layer.
9. The Gradle wrapper was 9.5 while AGP was pinned to 8.7.3. The wrapper is now aligned to Gradle 8.9, the compatibility baseline for AGP 8.7.

## Corrections applied
- Added `WorkingCoreChatScreen` as the live user communication surface.
- Added `WorkingCoreViewModel` and `WorkingCoreAiGateway`.
- Communication tab now routes: User -> selected SHOSHI/LUNA -> real provider gateway -> validated response -> UI.
- Added distinct immutable SHOSHI/LUNA system identities.
- Migrated direct OpenAI request path to `/v1/responses` and current configured model `gpt-5.6-luna`.
- API failures are explicit states: API_KEY_MISSING / OFFLINE / RATE_LIMITED / ERROR; no fake assistant response is appended.
- AI capability is promoted to READY only after a validated provider response.
- Added encrypted API-key settings to the real chat screen using the existing encrypted secret manager.
- Wired microphone permission + Android SpeechRecognizer to the same AI gateway; voice requests do not use a separate fake path.
- Voice responses can trigger canonical SHOSHI/LUNA TTS profiles.
- Restricted voice catalog to Hebrew (`he`) voices, with deterministic separate fallback profiles.
- Added TTS utterance progress handling so SPEAKING returns to IDLE.
- Corrected instrumentation package smoke test to `com.shoshiluna.executive`.
- Preserved Treasury/Room/Financial Firewall code; no weakening of Phase-6 financial controls.
- Updated capability registry and verification scripts: 2D is production UI; 3D is deferred and no longer a Phase-6 blocker.
- Aligned build configuration to the previously evidenced AGP 8.7.3 / Kotlin 2.0.21 / KSP 2.0.21-1.0.26 / Room 2.6.1 / SDK 35 baseline.
- Aligned Gradle wrapper to 8.9.

## Added focused tests
- Separate SHOSHI and LUNA system identities.
- Both identities retain hard safety boundaries.
- Selected agent is passed through the WorkingCore gateway.
- Provider failure cannot become a fake assistant response.
- Duplicate Send while a request is active is rejected.

## Local verification performed here
- XML parse: PASS (12 XML files).
- TOML parse: PASS.
- Kotlin bracket/structure sanity: PASS across 100 Kotlin source/test files during the static pass.
- Drawable reference check: PASS.
- Zero-placeholder gate: PASS.
- Manifest includes INTERNET and RECORD_AUDIO.
- Room/Treasury source and @Transaction paths remain physically present.

## Build limitation in this environment
`./gradlew --offline testDebugUnitTest` was attempted after wrapper alignment. This environment cannot reach `services.gradle.org` and has no cached Gradle 8.9 distribution. The attempt therefore stops with `UnknownHostException` before Gradle can execute project tasks. See `BUILD_ATTEMPT_v1.9.txt`.

This is an environment blocker, not evidence that the source compiles. Therefore:
- BUILD = BLOCKED_NOT_VERIFIED_HERE
- AI_CHAT = READY_IN_CODE / RUNTIME_REQUIRED
- VOICE_STT = READY_IN_CODE / RUNTIME_REQUIRED
- VOICE_TTS = READY_IN_CODE / RUNTIME_REQUIRED
- VOICE_CONVERSATION = READY_IN_CODE / RUNTIME_REQUIRED
- TREASURY = preserved; Android Room/runtime tests still required for a fresh v1.9 runtime claim
- 2D_UI = PRODUCTION_UI
- 3D = DEFERRED_FUTURE_SPATIAL_MODULE

## Non-negotiable runtime acceptance test
Do not call this package verified until a real Android build/device run demonstrates:
1. Configure API key in encrypted settings.
2. Select SHOSHI and send a random free-text prompt not present in source.
3. Receive a real provider response and see `AI: CONNECTED — REAL RESPONSE RECEIVED`.
4. Select LUNA and send a different random prompt; verify LUNA uses a distinct system identity.
5. Grant microphone permission.
6. Speak Hebrew -> SpeechRecognizer transcript -> selected executive -> OpenAI -> response -> matching executive TTS.
7. Restart app and verify voice identities persist.
8. Run all unit/lint/build/instrumentation tasks without deleting or weakening tests.
