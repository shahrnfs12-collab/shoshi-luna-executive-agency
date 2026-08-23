# SHOSHI/LUNA v1.5 — STRICT ACCEPTANCE GATE

This file is a binding build contract for Android Cloud. Do not report READY, COMPLETE, VERIFIED, BUILD SUCCESSFUL, 3D READY, AI READY, or ALL TESTS PASS unless every applicable gate below has concrete evidence.

## Non-negotiable execution order
1. Open this repository at its root. Do not rebuild a different sample app and do not replace modules with placeholders.
2. Run `bash tools/strict_verify.sh` exactly as supplied. If it fails, fix the underlying defect and rerun from the start. Never delete or weaken a failing assertion merely to obtain green output.
3. The gate must run `clean testDebugUnitTest lintDebug assembleDebug` and produce a non-empty `app-debug.apk`.
4. Preserve Emergency Halt, PermissionKernel, HumanApprovalGateway, simulation/real boundaries, evidence requirements and audit logging. A build is rejected if a fix bypasses one of these controls.
5. Never convert a missing connector, API key, model, permission, user approval or network response into a fabricated success result. Missing dependencies must be surfaced as NOT READY / WAITING / FAILED.

## 3D hard gate
The following real rigged models must exist and be packaged:
- `app/src/main/assets/models/shoshi.glb`
- `app/src/main/assets/models/luna.glb`

They must represent the approved photoreal master visuals in `res/drawable/shoshi_executive.png` and `res/drawable/luna_executive.png`. Do not substitute a generic female avatar. Each model must be full-body, rigged, Android/Filament compatible, and tested on device/emulator. At minimum verify idle/talk/walk/point/present animation availability or explicitly map the available animation clips in code. If the model generator cannot create faithful GLBs, stop and report BLOCKED rather than substituting art.

## Functional hard gates
- Mission creation, state transitions and Emergency Halt must be tested.
- Pipeline cannot auto-complete a mission merely by entering the final stage; final completion requires review/evidence.
- Council voting must use `CouncilSession.id`, not mission id.
- ToolGateway must execute only registered real adapters; an unconfigured adapter must FAIL, never return simulated output.
- Approval-required tools must remain blocked until an explicit approval request is approved.
- AI must return NOT READY/OFFLINE on missing key/network/API error; no canned answer may masquerade as model output.
- SHOSHI↔LUNA collaboration must enter a waiting/failure state when AI is unavailable.
- 2D images may be used only as fallback visuals and must never be labeled as active 3D.
- TTS may be labeled TTS only. Do not claim microphone STT exists until Android SpeechRecognizer/another verified STT path is implemented and tested.

## Required evidence bundle
Create `verification/` containing:
- `strict_verify.log` (complete stdout/stderr of the gate)
- `test-results/` copied from Gradle test outputs
- `lint-results.html`
- `app-debug.apk`
- `apk-sha256.txt`
- `runtime-checklist.md` with PASS/FAIL and observed evidence for: launch, navigation, mission, halt, council, collaboration-offline, AI-online if key supplied, TTS, Shoshi 3D, Luna 3D.

A screenshot is not sufficient evidence of backend functionality. A source file existing is not proof it is wired to the UI. Every claim must be backed by a test, runtime observation, or produced artifact.
