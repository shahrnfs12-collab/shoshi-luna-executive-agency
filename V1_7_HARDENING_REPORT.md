# v1.7 Static Hardening Report

## What was changed

- Added a dedicated Room-backed treasury database with persistent idempotency, approvals, financial transactions and double-entry ledger rows.
- Financial amounts in the new execution firewall use `Long amountMinorUnits`, not floating-point `Double`.
- REAL transfers remain locked unless a controlled `ExternalFinancialAdapter` is configured.
- Added durable execution states and reconciliation. Ambiguous external failures remain `EXECUTION_PENDING`; the engine does not blindly retry.
- Idempotency records store a complete immutable proposal snapshot so recovery never trusts a new UI/agent payload.
- External receipts must match the stored proposal amount/currency/source/destination/proposal ID before local ledger commit.
- Approval proof verification now uses ECDSA verification against an AndroidKeyStore public key. Key creation is user-authentication-bound. Actual BiometricPrompt signing remains runtime-gated.
- Added a strict GLB structural validator using Gson so its JVM tests do not depend on Android's `org.json` stub.
- GLB parsing follows GLB 2.0 chunkLength semantics: JSON chunk length includes padding; no second padding skip is performed.
- Added buffer/bufferView/accessor bounds, texture/image/material references, skin/joint checks, animation structure/type/count checks and local POSITION bounds.
- Added Android SpeechRecognizer engine and Compose runtime RECORD_AUDIO permission flow.
- Added `RECORD_AUDIO` to AndroidManifest.
- Added Room schema export configuration.

## Important limitations that remain

- No real financial adapter is included. REAL financial execution is locked/not configured.
- AndroidKeyStore verifier code cannot be proven without Android runtime + biometric-authenticated signing.
- Room persistence is code-complete but must be executed with instrumentation tests before release claims.
- `shoshi.glb` and `luna.glb` are still missing.
- Structural GLB validation cannot prove that a model visually resembles SHOSHI/LUNA or that its skeleton has semantic humanoid bone naming.
- Full voice conversation must still be exercised on a physical Android audio stack.

`PRODUCTION_TRUTH_GATE = BLOCKED`
