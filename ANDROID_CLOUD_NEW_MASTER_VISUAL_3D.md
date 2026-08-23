# NEW APPROVED SHOSHI + LUNA MASTER VISUAL — 3D BUILD CONTRACT

The new official visual master is:
`app/src/main/res/drawable/shoshi_luna_master_visual.png`

This image also replaces the previous default Command Center hero image through:
`app/src/main/res/drawable/command_center.png`

Android/3D build pipeline must use the characters in this new approved image as the identity reference for BOTH production 3D avatars. Previous avatar visual identities are deprecated for new 3D generation.

## Required outputs
- `app/src/main/assets/models/shoshi.glb`
- `app/src/main/assets/models/luna.glb`

SHOSHI: brunette/dark hair, black futuristic executive suit, gold trim, realistic full-body human proportions.
LUNA: blonde hair, silver/white futuristic executive suit, refined metallic/gold details, realistic full-body human proportions.

Both GLBs must be production-ready, PBR textured, humanoid rigged (>=15 valid joints), Android real-time optimized, correctly scaled/oriented, and contain these exact required animations:
`Idle`, `Talk`, `Walk`, `Listen`, `Point`, `Present`, `TurnLeft`, `TurnRight`.

Preferred extras: `Sit`, `Stand`, `Think`, `Agree`, `Disagree`, `CelebrateSubtle`.

Do not use generic avatars, placeholders, 2D planes presented as 3D, renamed stock GLBs, or fabricated validation evidence.

After generation run `Glb3DValidator` against both physical files and render them in Command Center. Compare rendered identity against `shoshi_luna_master_visual.png` before setting SHOSHI_3D/LUNA_3D/VISUAL_IDENTITY_3D to READY. If the environment cannot actually generate/rig the binary models, report `BLOCKED_NOT_VERIFIED` and identify the required external 3D generation/rigging service.
