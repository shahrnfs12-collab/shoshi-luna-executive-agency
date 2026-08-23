# Capability Matrix v1.6 — Truth Source

No capability may be presented as READY solely because a class, screen or button exists.

| Capability | Code status | Release claim allowed only when |
|---|---|---|
| Mission lifecycle | HARDENED | state-machine tests pass |
| Emergency Halt | HARDENED | halt regression tests pass |
| Council | CODE PRESENT | integration consensus test passes |
| SHOSHI↔LUNA collaboration | PARTIAL | live provider + end-to-end evidence passes |
| AI provider | LOCKED | real configured provider returns validated response |
| External tools | LOCKED | real adapter + permission + timeout + evidence passes |
| Memory | DEGRADED | persistence/restart test passes on device |
| TTS | CODE PRESENT | device audition test passes |
| STT | MISSING | microphone→intent→agent→response test passes |
| SHOSHI 3D | MISSING | validated shoshi.glb + skeleton + animations + APK packaging |
| LUNA 3D | MISSING | validated luna.glb + skeleton + animations + APK packaging |
| Business/Revenue | SIMULATION | remains explicitly SIMULATION without verified real-world connectors |
| Financial execution | LOCKED | must never become autonomous; human approval and financial firewall required |

`READY` is forbidden when the corresponding evidence artifact is absent.
