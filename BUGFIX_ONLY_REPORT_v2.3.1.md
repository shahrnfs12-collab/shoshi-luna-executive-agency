# SHOSHI + LUNA Dream Build v2.3.1 — Bugfix-Only Repair

Scope lock: this pass intentionally adds no product capabilities, no new UI concept, no new architecture and no feature expansion. It only repairs defects and stale tests in Dream Build v2.3.

## Production defects repaired

1. **Status-screen crash risk** — `ShoshiLunaApp` no longer assumes an `AI provider` readiness row always exists; missing readiness data now renders safely as not configured.
2. **Voice lifecycle leak** — the singleton TTS engine is shut down when the root Compose tree is disposed.
3. **Waiting-state state-machine bypass** — `EmpireMissionControl.enterWaitingState` now goes through `MissionTransitionPolicy` instead of mutating mission state directly.
4. **Provider failure masquerading as AI text** — `OpenAiProvider.chat` throws a typed provider exception on failure instead of returning an `AI ERROR...` string as if it were a model response. The obsolete `OpenAiClient.getCompletion` compatibility function that converted failures into response-looking strings was removed because it had no call sites.
5. **Memory promotion lost after restart** — `SovereignMemoryVault.promoteToFact` now persists promoted facts; fact-overwrite trimming now persists the exact same bounded list exposed through StateFlow.
6. **Test reset corrupted agent registration** — `MultiAgentCoordinator.resetForTest` now restores SHOSHI and LUNA to `PermissionKernel`, preventing test-order dependent failures.
7. **External gateway JVM tests crashed on Android-only encrypted storage** — `ExternalOperationJournal` now has an explicit test-only in-memory backend selected only by `resetForTest`; production initialization still uses encrypted Android storage.
8. **WorkingCore test constructor mismatch** — restored the one-gateway constructor expected by existing tests without changing production behavior.
9. **Existing app-name typo** — `shoshi lone` corrected to `ShoshiLuna` in `strings.xml`.

## Stale/incorrect tests repaired

- Communication halt test now creates the mission/conversation before activating Emergency Halt, so it tests block-on-send rather than failing during setup.
- Phase 4.5 tool tests now follow the legal approval state path instead of illegal `CREATED -> APPROVED` transitions.
- ToolGateway idempotency test now uses a real test adapter and verifies one adapter invocation for a repeated idempotency key; it no longer expects the removed simulated `CACHED_RESULT` behavior.
- Tool redaction test now verifies that secret values reach the adapter unchanged while audit output does not expose them.
- Strict pipeline test now advances every pipeline stage in order instead of adding a Treasury task while the current stage is Capability Discovery.
- Voice tests no longer reference the removed multi-voice catalog; they verify the two fixed SHOSHI/LUNA profiles.
- Black Swan test now verifies the existing engine instead of asserting that the capability is absent.
- Waiting-state regression test verifies illegal `CREATED -> WAITING_FOR_EXTERNAL_RESPONSE` is rejected.
- Business/Treasury/Phase-6 tests now reset singleton ledgers before each relevant test, removing test-order state leakage.

## Static verification performed in this environment

- `ZERO_PLACEHOLDER_GATE=PASS`
- XML parse errors: **0**
- Duplicate top-level Kotlin declaration heuristic: **0**
- Legacy package `com.example.shoshilone`: **0 occurrences**
- Stale removed voice symbol `availableVoices`: **0 occurrences**
- Stale simulated ToolGateway result `CACHED_RESULT`: **0 occurrences**
- Manifest contains `INTERNET` and `RECORD_AUDIO`
- Production Kotlin files: **95**
- Unit-test annotations: **92**
- Android-test annotations: **4**

## Build truth

A real Gradle attempt was made with:

```bash
./gradlew --offline clean testDebugUnitTest lintDebug assembleDebug
```

It could not start Gradle because this execution environment cannot resolve `services.gradle.org` and does not have the Gradle 8.9 distribution cached. The exact failure is preserved at:

`verification_evidence_v2.3.1/GRADLE_ATTEMPT_BUGFIX_v2.3.1.txt`

Therefore **BUILD_SUCCESSFUL is NOT claimed here**. Final compile/test/runtime proof must run in Android Studio / Gemini Android environment with the required Gradle distribution and Android SDK available.

## Final scope statement

This is a repair release only. Dream Build v2.3 features and visual design were not expanded in this pass.
