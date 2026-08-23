# SHOSHI + LUNA 3D model drop zone

Android Cloud must produce and place exactly these two files here:

- `shoshi.glb`
- `luna.glb`

Do not rename them and do not replace either model with a 2D billboard.

## Mandatory model requirements

- glTF 2.0 binary (`.glb`)
- full body, photoreal PBR materials
- likeness locked to the approved master visuals already in `res/drawable/shoshi_executive.png` and `res/drawable/luna_executive.png`
- correct black/gold executive suits and identity details
- humanoid skeleton / skinning
- neutral A- or T-pose bind pose
- meter-based scale, feet on ground plane, Y-up
- optimized for Android real-time rendering
- target <= 80k triangles per avatar, <= 4 material slots where practical
- 2K textures preferred, 4K only for face if memory budget permits
- no embedded external network dependencies

## Required animation clips

Use these exact names when possible:

1. `Idle`
2. `Talk`
3. `Walk`
4. `Point`
5. `Present`
6. `Listen`
7. `TurnLeft`
8. `TurnRight`

Facial rig / morph targets should include blink and basic visemes when supported.
