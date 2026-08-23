# GEMINI / ANDROID CLOUD — DREAM BUILD FINAL EXECUTION ORDER v2.3

Work on the supplied v2.3 project. Do not restart, rewrite from scratch or remove institutional controls.

## Non-negotiable objective
Prove the existing Dream Build works end-to-end on Android. Fix actual build/runtime defects until the relevant acceptance gate passes. Do not replace production implementations with mocks or hardcoded success.

## 1. Build from clean state
Run:
```bash
./gradlew clean
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
```
Then, if Android device/emulator is available:
```bash
./gradlew connectedDebugAndroidTest
```
Return exact task output, test counts, failures/skips and new APK SHA-256.

## 2. Validate new Dream Command Center
Verify the new 6-destination navigation is usable and not overcrowded:
- Center
- Chat
- Missions
- Council
- Business
- More
`More` contains Agents, Pipeline, External Network, Memory, Presence.
Fix any Compose/compiler/runtime issue; do not revert to the old cluttered 8-item navigation unless unavoidable and documented.

## 3. Real AI runtime
Use secure API-key settings. The model policy intentionally targets `gpt-5.6-sol` for Dream quality. Confirm current API availability before runtime use. Test novel prompts to both executives. Prove distinct system instructions and no fake fallback.

## 4. Persistent executive memory
Verify WorkingCore now loads private + shared + verified memory into agent context and records successful user/AI turns into the correct private partition. Restart test. Ensure private partitions do not leak across executives.

## 5. Command runtime
From Chat:
- `צרי משימה בדיקת Dream Build`
- `משימות`
- `עצור הכל`
Verify real state changes. AI output itself must never be reinterpreted as a command.

## 6. Council runtime
Create mission, convene Council, execute SHOSHI/LUNA position flow. If the current Council UI/manual buttons do not yet invoke real AI positions, implement the minimal safe AI-backed Council coordinator while preserving Council session IDs, evidence and no autonomous sensitive action.

## 7. Voice runtime
Exactly two fixed profiles only. Verify microphone -> STT -> selected executive -> real AI -> TTS. No voice catalog. Report actual Android TTS voice IDs selected on that device, but keep UI fixed to SHOSHI/LUNA only.

## 8. External agent sandbox
Connect one controlled HTTPS/A2A sandbox agent. Prove candidate registry, trust, handshake, approval-required send, idempotency, inbound quarantine, evidence/audit and prompt-injection blocking. Do not use production credentials.

## 9. Treasury / business regression
Re-run all Phase 6/Room/KSP/Treasury tests. Do not enable real money. Business pipeline may use simulation/sandbox unless a real approved connector exists.

## 10. Product film evidence
After runtime gates pass, capture the exact runtime sequences in `DREAM_BUILD_CINEMATIC_STORYBOARD_v2.3.md`. If video-generation/editing tools are unavailable, return the raw screen recordings/screenshots and shot manifest; do not fabricate completed video evidence.

## Final report
Return:
- changed files
- exact commands run
- test/lint/build results
- APK path + SHA-256
- Android runtime evidence for AI, command, memory, Council, voice
- external sandbox evidence
- security/approval evidence
- remaining blockers
- capability matrix using only `VERIFIED_RUNTIME`, `READY_IN_CODE`, `BLOCKED_RUNTIME_VERIFICATION`, `LOCKED_NOT_CONFIGURED`, `DEFERRED`

Never claim “perfect”, “production ready” or “10/10” solely from static inspection. The Dream standard is high precisely because claims must be backed by runtime evidence.
