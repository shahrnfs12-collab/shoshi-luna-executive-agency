# v1.3 — 3D Cloud Ready

This version adds the Android-side contract and renderer integration for **real rigged SHOSHI + LUNA GLB avatars**.

What is complete now:
- stable asset paths for both avatars
- SceneView/Filament loading layer
- separate 3D animation intent state
- offline model readiness checks
- fail-closed model loading / photoreal fallback contract
- Android Cloud generation/integration specification
- strict identity lock to the approved photoreal master visuals
- unit tests for model-path and animation contracts

What is intentionally NOT faked:
- `shoshi.glb` and `luna.glb` are not included because no real rigged 3D geometry was supplied/generated in this environment.
- The app must not claim FULL 3D until those assets exist and successfully load.
