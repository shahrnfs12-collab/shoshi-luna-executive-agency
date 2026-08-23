# Android Runtime Execution Order v1.9 — no shortcuts

Work from this exact project. Do not restart/rewrite it and do not disable KSP/Room/tests.

## Build environment
Use JDK 17, Android SDK 35, AGP 8.7.3 and Gradle 8.9 as pinned by the project.

## Mandatory commands
```bash
./gradlew clean
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
```

If a device/emulator is available:
```bash
./gradlew connectedDebugAndroidTest
```

Then install `app-debug.apk` on a real Android device.

## AI runtime gate
1. Open Communication / AI Chat.
2. If no key is configured, the UI must say API KEY MISSING. No assistant response may appear.
3. Save a valid OpenAI API key through the encrypted settings UI. Never put the key in source/logs/reports.
4. Select SHOSHI and ask an arbitrary Hebrew question not present in the code.
5. Verify an actual network response and the runtime banner becomes CONNECTED only after the valid response.
6. Select LUNA and ask an unrelated prompt. Verify LUNA's system identity, not only avatar/voice, changed.
7. Test bad key (401), rate limit (429 if reproducible), offline network, and timeout. Each must show an error/offline state and no fake assistant message.

## Voice runtime gate
1. Tap microphone with permission absent: Android permission request must appear.
2. Grant permission.
3. Speak Hebrew.
4. Verify visible STT transcript.
5. Verify transcript routes to the selected executive through the same AI gateway.
6. Verify real provider response.
7. Verify SHOSHI uses SHOSHI voice profile and LUNA uses LUNA voice profile.
8. Verify TTS state returns from SPEAKING to IDLE.
9. Restart and verify voice identity persistence.

## Phase 6 preservation
Do not weaken or remove:
- Treasury Room/KSP persistence
- idempotency
- double-entry transaction
- ECDSA/AndroidKeystore approval verifier
- Human Approval
- Financial Firewall
- Emergency Halt
- state-machine rules

## Presentation rule
`SHOSHI_LUNA_2D_UI=PRODUCTION_UI`
`SHOSHI_LUNA_3D=DEFERRED_FUTURE_SPATIAL_MODULE`
Missing GLB files must not fail the Phase-6 software gate.

## Completion evidence
Return exact Gradle output, test counts, lint result, APK SHA-256, Android screenshots/logcat with secrets redacted, and AI/voice runtime evidence. Never claim a task ran if it did not.
