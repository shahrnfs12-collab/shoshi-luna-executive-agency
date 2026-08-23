# Android Cloud — Production Hardening v1.6 (NO SHORTCUTS)

## Mandatory rule
Do not report READY, COMPLETE, VERIFIED or BUILD SUCCESSFUL from static inspection. Execute the gates and attach evidence.

## Forbidden shortcuts
1. Do not delete, skip, weaken, @Ignore, comment out, or rewrite a failing test merely to pass.
2. Do not replace production implementations with mocks/fakes/stubs.
3. Do not change a capability from MISSING/LOCKED/SIMULATION to READY without runtime evidence.
4. Do not create empty/generic GLB files just to satisfy filenames or size checks.
5. Do not suppress lint/compiler/security errors to make the pipeline green.
6. Do not hardcode AI/tool responses.
7. Do not bypass HumanApprovalGateway, PermissionKernel, Emergency Halt or ToolGateway.
8. Do not add cleartext HTTP or secrets/API keys to source, resources, logs or BuildConfig.

## Required command
`bash tools/production_verify.sh`

This command must end with exactly:
`PRODUCTION_TRUTH_GATE=PASS`

Anything else is a failure and must be fixed in production code.

## Required runtime scenarios
- One user request creates exactly one mission/task after recomposition/restart.
- SHOSHI delegates to LUNA; LUNA returns a real provider result; Council resolves; evidence and audit IDs are present.
- Emergency Halt during execution prevents all subsequent mutations/external execution.
- Tool timeout returns FAILED and does not claim execution.
- Missing provider/GLB/STT is displayed as NOT READY, not simulated.
- App process kill/relaunch restores supported persistent state; if not implemented, keep capability DEGRADED and fail release claim.
- Every UI action has a real handler or is visibly disabled with reason.

## 3D acceptance
Both GLBs must be derived from approved master visuals, not generic avatars. Validate skeleton, materials, textures, scale and required animation clips. Inspect inside final APK.

## Evidence package
Place reports under `evidence/v1.6/`: test results, lint results, APK hashes, dependency report, screenshots/video of runtime scenarios, GLB validation report and final capability matrix.
