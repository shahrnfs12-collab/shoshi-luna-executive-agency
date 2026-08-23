# Android Runtime Final Gate v2.1

Run from the project root on the Android machine:

```bash
./gradlew clean
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
```

If a device/emulator is attached:

```bash
./gradlew connectedDebugAndroidTest
```

Manual command test in the **תקשורת / AI Chat** screen:

1. Select SHOSHI.
2. Enter `צרי משימה בדיקת טלפון`.
3. Confirm the reply is labeled `SYSTEM COMMAND` and says a mission was created.
4. Open the Missions tab and confirm `בדיקת טלפון` physically appears.
5. Return to chat and enter `עצור הכל`.
6. Confirm Emergency Halt becomes ACTIVE and the mission displays HALTED.
7. Try `צרי משימה אסור` and confirm it is rejected.
8. Enter `שחרר`; confirm chat refuses to release the halt.
9. Use the Command Center safety button to release Halt.

Manual AI test:

1. Configure a test API key only on a private/debug device.
2. Ask SHOSHI an arbitrary question not present in source.
3. Require `AI: CONNECTED — REAL RESPONSE RECEIVED` only after the response appears.
4. Ask LUNA a different question and verify the agent label and answer path.

Manual voice test:

1. Grant microphone permission.
2. Speak Hebrew to SHOSHI; verify STT transcript -> real AI response -> SHOSHI TTS.
3. Repeat for LUNA and verify a distinct fixed Hebrew voice profile.

Public release blocker:

- Replace direct client-side OpenAI API key usage with a server-side relay before public distribution.
