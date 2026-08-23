# SHOSHI/LUNA capability matrix — audited status

Status meanings: WORKING-IN-CODE = concrete implementation present but still requires Gradle/runtime proof; PARTIAL = meaningful implementation exists but external/runtime prerequisite remains; NOT READY = missing required implementation or asset; SIMULATION = intentionally simulation-only and must never be represented as real-world execution.

| Capability | Status before Android Cloud verification | Acceptance requirement |
|---|---|---|
| Mission control | WORKING-IN-CODE | unit test + runtime create/transition |
| Emergency Halt | WORKING-IN-CODE | prove creation/transitions/pipeline mutation blocked |
| SHOSHI/LUNA AI | PARTIAL | real API call path exists; key/network/API success must be verified |
| SHOSHI↔LUNA collaboration | PARTIAL | real provider path; verify offline waiting + online response |
| Council voting | WORKING-IN-CODE | session-id bug fixed; consensus test must pass |
| Debate | PARTIAL | bounded rounds exist; no semantic AI debate evaluator is claimed |
| Memory partitions | WORKING-IN-CODE | verify persistence/isolation tests and restart behavior |
| Business simulation | SIMULATION | remain visibly tagged SIMULATION |
| Revenue/Treasury ledgers | PARTIAL | verify invariants/tests; real money movement is not implemented |
| External tools | PARTIAL | real adapter boundary added; each concrete connector needs a registered tested adapter |
| Human approval | WORKING-IN-CODE | approval-required tool must remain blocked before approval |
| Audit / redaction | WORKING-IN-CODE | tests + inspect logs for secret leakage |
| TTS voice | PARTIAL | Android TTS runtime test; distinct profiles/voices |
| Microphone STT | NOT READY | implement SpeechRecognizer/verified provider + RECORD_AUDIO + runtime permission + tests |
| 2D photoreal presence | WORKING-IN-CODE | verify resources render |
| 3D SHOSHI | NOT READY | faithful rigged `shoshi.glb`, packaged and runtime-tested |
| 3D LUNA | NOT READY | faithful rigged `luna.glb`, packaged and runtime-tested |
| 3D animations | NOT READY until GLBs | verify clips and controller-to-renderer wiring |
| Autonomous real-world revenue generation | NOT READY | requires concrete external connectors, approvals, compliance and evidence; do not claim it exists |
