# SHOSHI / LUNA Master Visuals — v1.2

This build uses the approved photoreal executive visuals as the canonical 2D identity assets.

- SHOSHI: `res/drawable/shoshi_executive.png`
  - SHA-256: `9008274ee01f7059426d97fad760dda7a9de3b500ec2ac4f74570b3d5640c1e9`
- LUNA: `res/drawable/luna_executive.png`
  - SHA-256: `7e9cc211a5828baf5bc8a392ba785a4337b8ff5069a4839da9ec28a5a2ec4b3e`

## Rendering policy

- Command Center and Presence screens use these master visuals directly.
- Images use `ContentScale.Fit` to avoid face/body cropping.
- Native Filament remains available for future spatial rendering.
- No claim is made that a 2D PNG is a rigged 3D human. Full skeletal 3D activation requires validated GLB/GLTF assets.

## New approved joint visual master
- `res/drawable/shoshi_luna_master_visual.png` is the authoritative new identity reference for both SHOSHI and LUNA and supersedes prior visual identity for future 3D generation.
