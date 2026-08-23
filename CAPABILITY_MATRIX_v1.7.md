# Capability Matrix v1.7 — Static Hardening Truth Source

`PRODUCTION_TRUTH_GATE = BLOCKED`

No runtime-dependent capability is READY merely because code exists.

| Capability | v1.7 status | Evidence / blocker |
|---|---|---|
| Mission/state machine | READY_IN_CODE | Existing transition policies + tests; Gradle runtime verification pending |
| Evidence gate | READY_IN_CODE | SHA-256 evidence paths present; runtime integration pending |
| Emergency Halt | READY_IN_CODE | Existing fail-closed enforcement; process/device tests pending |
| Financial firewall | READY_IN_CODE | REAL path requires ApprovalProofVerifier + ExternalFinancialAdapter |
| Real financial execution | LOCKED_NOT_CONFIGURED | No external financial adapter is included or enabled |
| Financial ledger | READY_IN_CODE / BLOCKED_RUNTIME_VERIFICATION | Room entities/DAO + atomic double entry added; Android Room tests must run |
| Persistent financial idempotency | READY_IN_CODE / BLOCKED_RUNTIME_VERIFICATION | requestId primary key + proposal snapshot + external transaction uniqueness + recovery states |
| Human approval | PARTIAL / BLOCKED_RUNTIME_VERIFICATION | ECDSA verification against AndroidKeyStore implemented; BiometricPrompt signing flow must be proven on-device |
| AI pipeline | BLOCKED_RUNTIME_VERIFICATION | Real OpenAI network path exists; API/network runtime required |
| TTS | BLOCKED_RUNTIME_VERIFICATION | Android TextToSpeech runtime required |
| STT | READY_IN_CODE / BLOCKED_RUNTIME_VERIFICATION | SpeechRecognizer + RECORD_AUDIO permission flow added; device service/microphone test required |
| Voice conversation | PARTIAL / BLOCKED_RUNTIME_VERIFICATION | STT and TTS primitives exist; full UI end-to-end loop still release-gated |
| GLB structural validator | READY_IN_CODE / BLOCKED_REAL_ASSET_VERIFICATION | Strict self-contained GLB structural gate added; not a visual identity/humanoid semantics proof |
| SHOSHI 3D | BLOCKED_NOT_VERIFIED | `assets/models/shoshi.glb` missing |
| LUNA 3D | BLOCKED_NOT_VERIFIED | `assets/models/luna.glb` missing |
| 3D animations | BLOCKED_NOT_VERIFIED | Real GLBs must contain Idle/Talk/Walk/Listen/Point/Present/TurnLeft/TurnRight |
| 3D visual identity | BLOCKED_NOT_VERIFIED | Must render and compare against photorealistic master visuals |
| Command Center | READY_IN_CODE / BLOCKED_RUNTIME_VERIFICATION | Compose screen exists; UI/device wiring audit remains runtime-gated |

## Non-negotiable release truth

The following are required before `PRODUCTION_TRUTH_GATE=PASS`:

1. Gradle clean + unit tests + lint + debug build.
2. Android instrumentation tests, including Room and permissions.
3. Real OpenAI/network test with secure key provisioning if AI is claimed READY.
4. Real TTS/STT test on target device.
5. Real `shoshi.glb` and `luna.glb`, structural validation, renderer load, animation playback and visual identity review.
6. Release APK inspection and SHA-256 evidence.
