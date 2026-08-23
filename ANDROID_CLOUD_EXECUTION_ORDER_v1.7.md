# Android Cloud / Android Studio — v1.7 No-Shortcut Execution Order

Do not weaken tests, suppress compiler/lint failures, create dummy GLBs, or mark a blocked capability READY.

## A. Build truth

```bash
./gradlew clean
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
./gradlew connectedDebugAndroidTest
./gradlew assembleRelease
```

Fix production code until these pass. Do not delete/ignore failing tests.

## B. Treasury truth

- Verify Room schema generation.
- Run `TreasuryRoomIntegrationTest` on Android.
- Add a file-backed DB close/reopen test (same DB path, new Room instance).
- Verify requestId unique constraint survives DB reopen.
- Verify approval rows are single-use.
- Verify `EXECUTION_PENDING` is reconciled via `lookupByIdempotencyKey`; never blindly retry an uncertain REAL transfer.
- Verify `EXTERNAL_CONFIRMED` commits from the persisted proposal snapshot, not caller data.
- Keep `REAL_FINANCIAL_EXECUTION=LOCKED_NOT_CONFIGURED` unless a real, explicitly authorized adapter is provided.

## C. Biometric/Keystore truth

Implement and test the UI signing flow:

1. `ApprovalKeyManager.ensureKey(alias)`.
2. `ApprovalKeyManager.newSigningOperation(alias)`.
3. Wrap Signature in `BiometricPrompt.CryptoObject`.
4. After successful biometric authentication, sign exactly `ApprovalPayload.canonicalBytes()`.
5. Base64-encode signature and submit AuthenticatedApproval.
6. `AndroidKeystoreApprovalVerifier` must reject tampering, wrong key, expired proof, changed amount/account/mission/nonce.

No biometric test = Human Approval remains runtime blocked.

## D. Voice truth

- Verify mic button requests RECORD_AUDIO through the Activity Result flow.
- Test denied/permanently-denied states.
- Test SpeechRecognizer partial/final/error/cancel lifecycle.
- Test TTS interruption and cleanup.
- Run microphone → transcript → agent → response → TTS end-to-end.

## E. 3D asset creation and truth gate

Missing required files:

- `app/src/main/assets/models/shoshi.glb`
- `app/src/main/assets/models/luna.glb`

They must be real full-body, rigged, PBR models matching the supplied photorealistic reference images, not generic/dummy avatars.

Required animation clip names:

- Idle
- Talk
- Walk
- Listen
- Point
- Present
- TurnLeft
- TurnRight

Run the v1.7 `Glb3DValidator` on both files. Then load both in the actual SceneView/Filament renderer and verify animation playback, scale, materials, textures and lifecycle.

Visual identity is a separate manual/rendered gate. Binary structure alone cannot pass it.

## F. APK inspection

```bash
unzip -l app/build/outputs/apk/debug/app-debug.apk | grep 'assets/models/'
unzip -l app/build/outputs/apk/release/app-release-unsigned.apk | grep 'assets/models/'
sha256sum app/build/outputs/apk/debug/app-debug.apk
```

Do not issue `PRODUCTION_TRUTH_GATE=PASS` until all required gates have executable evidence.
