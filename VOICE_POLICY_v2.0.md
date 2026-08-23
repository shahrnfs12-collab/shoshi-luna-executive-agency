# SHOSHI + LUNA Fixed Voice Policy v2.0

Production UI exposes exactly two agent voice identities and no voice picker.

- SHOSHI: Hebrew/Israel locale (`he-IL`), fixed higher feminine-oriented profile, clear/confident cadence.
- LUNA: Hebrew/Israel locale (`he-IL`), distinct fixed feminine-oriented profile, calmer cadence.
- Hebrew voices are selected deterministically from installed Android TTS voices. No random selection.
- If only one Hebrew base voice exists, both agents use the same base engine voice with distinct fixed pitch/rate profiles.
- The former Advanced Voice Settings / voice catalog screen has been removed from production navigation.
- Android's standard `Voice` API does not expose reliable gender metadata, therefore female identity must be runtime-audited on the target device/TTS engine. If the installed Hebrew voices do not sound female, the production TTS provider must be replaced with a provider offering explicit fixed female Hebrew voice IDs.
