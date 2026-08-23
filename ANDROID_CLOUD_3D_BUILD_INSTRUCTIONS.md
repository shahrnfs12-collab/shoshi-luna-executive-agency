# Android Cloud task — SHOSHI + LUNA true 3D

## Objective
Complete the existing Android project with two **true rigged 3D digital humans** based on the approved master visuals already bundled in the project. Do not redesign their identity and do not substitute generic avatars.

## Identity lock
- SHOSHI master: `app/src/main/res/drawable/shoshi_executive.png`
- LUNA master: `app/src/main/res/drawable/luna_executive.png`
- Keep face, hair, glasses (Luna), black/gold executive styling, proportions and overall identity consistent with those references.

## Required output assets
Create:
- `app/src/main/assets/models/shoshi.glb`
- `app/src/main/assets/models/luna.glb`

The files must satisfy `app/src/main/assets/models/README_DROP_GLB_HERE.md`.

## Required Android integration
The code-side integration is already prepared in:
- `core/presentation/Avatar3DConfig.kt`
- `core/presentation/Executive3DState.kt`
- `core/presentation/SpatialRenderer.kt`

Do not route rendering code into treasury, execution, financial, security or permission layers. 3D is presentation-only.

## Acceptance tests
1. Project builds with `./gradlew assembleDebug`.
2. Both GLBs load offline from app assets.
3. No internet is required to render the avatars.
4. SHOSHI and LUNA appear as different correctly identified models.
5. Full body is visible without clipping at default camera framing.
6. Idle animation loops without crash.
7. Talk/Walk/Point/Present/Listen/TurnLeft/TurnRight clips are discoverable or cleanly mapped.
8. Missing model causes photoreal fallback, not crash and not a fake '3D ready' status.
9. Emergency Halt / audit / authorization behavior remains unchanged.
10. Renderer cannot issue trades, transfers, payments, withdrawals or external financial actions.

## Non-negotiable truth rule
Never label a 2D image, billboard or depth/parallax effect as a completed 3D avatar. `3D READY` is allowed only after both GLB assets are present and successfully load.
