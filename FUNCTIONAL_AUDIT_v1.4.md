# SHOSHI / LUNA Functional Audit v1.4

## What was actually broken
- `OpenAiProvider` returned a canned string and never called the network client. FIXED.
- `TitanEngine` never initialized `OpenAiClient`. FIXED.
- SHOSHI↔LUNA collaboration used a one-second delay and a canned success response. FIXED: it now calls the AI provider and moves to REVIEW, or WAITING_FOR_EXTERNAL_RESPONSE if unavailable.
- Business engine remains simulation unless verified external revenue/cost evidence is provided. This is intentional and is no longer represented as autonomous real-world revenue.
- 3D code existed but `shoshi.glb` / `luna.glb` are not in the project. Runtime now reports NOT READY instead of implying full 3D exists.
- Morph-target expressions/blinks are not implemented until compatible rigged GLBs exist.
- Voice output (TTS) is real Android TTS. Speech-to-intent is not production STT and must not be represented as such.
- Backup was a placeholder. FIXED for non-secret agency memory snapshot.

## Build verification status
This environment could not execute Gradle because `gradle-9.5.0-bin.zip` is not locally cached and outbound DNS/network is blocked (`UnknownHostException: services.gradle.org`). Therefore this package is statically audited and repaired, but NOT falsely labeled BUILD SUCCESSFUL. Android Cloud/Android Studio must run:

`./gradlew clean testDebugUnitTest assembleDebug --stacktrace`

## Required assets / configuration
1. `app/src/main/assets/models/shoshi.glb`
2. `app/src/main/assets/models/luna.glb`
3. An OpenAI API key configured via the encrypted `ApiKeyManager` path or, preferably, a server-side relay for production.

## Acceptance gates
- No canned AI response.
- No fake agent consensus.
- Emergency Halt blocks mission creation/transitions.
- 3D is READY only when both GLBs are packaged.
- Real-world money/revenue is never claimed from simulation data.
- A successful Android Cloud build is required before APK delivery.
