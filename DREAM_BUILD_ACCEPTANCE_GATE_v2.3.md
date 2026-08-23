# SHOSHI + LUNA DREAM BUILD 10+ — Acceptance Gate v2.3

## Truth Rule
No capability is `VERIFIED_RUNTIME` until it was exercised on an Android device/emulator and produced evidence. A compiling class, green unit test, mock connector or pretty UI is not runtime proof.

## Product Identity
- `SHOSHI_LUNA_2D_UI = PRODUCTION_UI`
- `SHOSHI_LUNA_3D = DEFERRED_FUTURE_SPATIAL_MODULE`
- SHOSHI and LUNA use the approved photoreal master visuals.
- Exactly two fixed Hebrew/Israeli female-oriented TTS profiles are used. There is no end-user voice catalog.

## Gate A — Build Integrity
Required clean commands:
```bash
./gradlew clean
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
```
If device/emulator exists:
```bash
./gradlew connectedDebugAndroidTest
```
No failing/skipped gate tests may be hidden or weakened.

## Gate B — Real AI Chat
1. Configure API key through secure settings only.
2. Select SHOSHI and ask a novel Hebrew question not present in source.
3. Confirm `/v1/responses` request reaches OpenAI and returns a real response.
4. Select LUNA, ask a different novel question, and prove the system instructions differ.
5. Missing key, network failure, 401, 429 and 5xx must produce honest error states — never a local fake answer.
6. AI-connected badge may appear only after a validated provider response.

## Gate C — Executive Memory
1. Ask SHOSHI to remember a non-sensitive preference/context item.
2. Restart app.
3. Ask SHOSHI a related question and verify relevant memory is loaded into her context.
4. Verify LUNA private memory remains isolated unless explicitly shared.
5. Verified knowledge must outrank assumption/inference.

## Gate D — Commands
From chat, execute user-authored commands:
- create mission
- list missions
- emergency halt
- status
Verify actual `EmpireMissionControl` state changes. AI-generated text must never directly enter `CommandExecutionBridge`.
Emergency Halt release remains explicit controlled UI action.

## Gate E — Council / Debate
1. Create mission.
2. Convene Council.
3. Obtain SHOSHI position and LUNA challenge/alternative.
4. Preserve session ID, votes, outcome, dissent and rationale.
5. Council disagreement must not be silently converted into consensus.

## Gate F — Voice
On Android hardware:
- RECORD_AUDIO permission flow
- microphone -> Hebrew SpeechRecognizer transcript
- selected executive -> real AI
- response -> correct fixed TTS profile
- SHOSHI and LUNA voices clearly distinguishable
- restart preserves deterministic profiles
- no 100+ voice selection UI

## Gate G — External Agent Network
Use a controlled sandbox endpoint/agent only.
1. Register candidate.
2. Endpoint policy validation.
3. Explicitly trust candidate.
4. Handshake.
5. Create authorized mission.
6. Prepare contact proposal.
7. Human Approval is required before network send.
8. Send with idempotency key.
9. Inbound response enters quarantine/untrusted-content inspection.
10. Safe content yields evidence and audit record.
11. Prompt-injection payload must be quarantined.
12. Simulated crash-window `EXECUTION_PENDING` must block blind automatic resend.

## Gate H — Financial / Treasury
Regression only unless real connector separately approved.
- REAL financial adapter remains `LOCKED_NOT_CONFIGURED` by default.
- Room/KSP generated code must exist.
- integer minor units for money.
- persistent idempotency.
- double-entry atomic ledger.
- single-use authenticated approval.
- emergency halt overrides execution.
No AI direct-to-money path.

## Gate I — Business Agency
Run a mission through the canonical pipeline without illegal transitions:
Capability Discovery -> Productization -> Market Research -> Opportunity Discovery -> Lead Discovery -> Qualification -> Offer Creation -> Human Approval -> Delivery -> Payment Verification -> Revenue Ledger -> Treasury Allocation.
No `RECEIVED` revenue without verified evidence.
No external outreach without configured connector and approval.

## Gate J — Dream Experience
The main Command Center must visibly show:
- approved SHOSHI + LUNA master visuals
- operational/halt state
- Executive Weather
- Executive Brief / next best action
- live missions
- approval inbox
- Council activity
- evidence/audit status
- trusted external network status
- executive reliability
No decorative READY labels detached from real state.

## Release Verdict
Only if Gates A–J pass where applicable:
- `PHASE6_DREAM_SOFTWARE = VERIFIED_RUNTIME`
- `DREAM_BUILD_2D = READY_FOR_CONTROLLED_BETA`
Public production additionally requires server-side secret handling/relay, telemetry/privacy review and real-world soak testing.
