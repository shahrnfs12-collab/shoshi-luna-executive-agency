# SHOSHI + LUNA Deep Audit v2.1

## Truth status

- Command execution core: **locally simulated and PASS** against the actual `EmpireMissionControl` state implementation (with only `AuditTrail` stubbed because the local Kotlin compiler is 1.9 while the Android project uses Kotlin 2.0.21).
- Business lifecycle transition sequence: **locally simulated and PASS**.
- Revenue verification truth gate: **locally simulated and PASS**.
- Treasury spend evidence linkage: **locally simulated and PASS**.
- Zero placeholder gate: **PASS**.
- XML parse: **PASS** (0 parse errors).
- Missing drawable references: **PASS** (0 missing references).
- Full Android Gradle build: **BLOCKED in this environment** because Gradle 8.9 cannot be downloaded (`UnknownHostException: services.gradle.org`).
- Android device runtime for AI/STT/TTS: **NOT VERIFIED HERE**.

## Critical defects found and fixed

1. WorkingCore previously sent every input to AI and did not execute the existing local command engine. Added `CommandExecutionBridge` and wired it before the AI path.
2. Explicit USER commands now mutate real mission state. AI output never routes directly into the command bridge.
3. `release halt` from chat is rejected; Emergency Halt can only be released through the explicit safety UI control.
4. Command replies are visually labeled `SYSTEM COMMAND`, so they cannot be confused with model output.
5. Mission UI allowed illegal `CREATED -> COMPLETED` transitions. Buttons now enable only when `MissionTransitionPolicy.canTransition` returns true.
6. Mission creation button could crash on blank titles. It is now disabled until the title is nonblank.
7. Collaboration button could start on a CREATED mission and later hit an illegal transition. It is now enabled only for RUNNING missions.
8. Business simulation contained illegal transitions (`CREATED -> DISCOVERED`, `RESEARCHING -> APPROVED`, `APPROVED -> COMPLETED`). The full canonical business lifecycle is now explicit and passes a local state-machine simulation.
9. Revenue verification accepted arbitrary nonblank strings as evidence. It now requires an existing `EvidenceRegistry` item already classified `VERIFIED_RESULT`.
10. Treasury funding/spend verification accepted arbitrary evidence strings and could report success for unknown IDs. Both now require a verified registry item and an existing allocation/proposal.
11. Verified treasury spend did not persist the evidence reference on the proposal. `verificationRef` is now stored in `TreasurySpendProposal`.
12. Removed the unused legacy `ChatControlEngine` to avoid two competing command paths.
13. Voice selector remains fixed to exactly two Hebrew voice identities; no user voice catalog is exposed.
14. Command Center voice label no longer claims runtime audibility before device verification.
15. Added a truth status for AI transport security: the current direct API-key mode is suitable for local device testing but not public production release.

## Command simulation performed

Sequence:

1. `צרי משימה בדיקת מערכת`
2. Assert mission count becomes 1.
3. Assert title is `בדיקת מערכת` and owner is SHOSHI.
4. `משימות` and assert result contains the mission.
5. `עצור הכל`.
6. Assert global `emergencyHalt == true` and mission state becomes `HALTED`.
7. Attempt another mission during Halt and assert it is rejected and mission count remains 1.
8. Attempt `שחרר` from chat and assert it is rejected and halt remains active.

Result: `COMMAND_SIMULATION=PASS`.

## Important security blocker before public release

The current Android app can store a user-entered OpenAI API key encrypted at rest and call OpenAI directly. This can work for local/private testing, but provider API secrets must not be shipped/exposed in a client application for public production. A server-side AI relay (or another server-controlled authentication architecture) is still required before public release.

## Runtime tests still required on Android

- Configure AI connection and send a new arbitrary SHOSHI prompt; verify `AI: CONNECTED` only after a real provider response.
- Repeat with LUNA and confirm the distinct system identity.
- Hebrew microphone -> STT -> selected Agent -> AI -> TTS.
- Verify SHOSHI/LUNA fixed voices on the target device.
- Run `testDebugUnitTest`, `lintDebug`, `assembleDebug`, and `connectedDebugAndroidTest` where a device/emulator exists.
