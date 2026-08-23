# Gemini / Android Cloud Final Execution Order — v2.2

Do not redesign or delete existing systems. Build and verify this package exactly as received.

1. Run clean build/test/lint/assemble.
2. Fix compile/runtime defects in production code; never weaken tests or gates.
3. Verify WorkingCore text AI on Android with a real non-hardcoded prompt.
4. Verify SHOSHI and LUNA use different actual system identities.
5. Verify microphone -> STT -> selected agent -> AI -> TTS with exactly two fixed Hebrew voice identities.
6. Verify command execution: create mission, list missions, emergency halt, blocked mutation under halt.
7. Verify Room/Treasury/KSP and all existing security gates remain intact.
8. Verify ExternalAgentGateway tests and then configure ONE controlled test remote endpoint only if credentials/endpoint are available.
9. External remote test must prove: trusted registration -> handshake -> approval required -> outbound message -> inbound quarantine -> evidence/audit. Use a harmless sandbox endpoint only.
10. Do not configure real email/CRM/research providers unless credentials are explicitly supplied. Keep them LOCKED_NOT_CONFIGURED otherwise.
11. Do not send secrets, private memory or Treasury data to any remote agent.
12. 2D is production UI. Preserve future Spatial/3D/video presentation roadmap; it is not a release blocker and may never control execution authority.

Required final evidence:
- BUILD SUCCESSFUL output
- test counts/pass/fail/skipped
- lint result
- APK hash
- Android text-chat runtime proof
- Android voice runtime proof
- command runtime proof
- external-agent sandbox runtime proof if configured
- current capability matrix with no unsupported READY claims

No mocks in production. No placeholders. No fake success. No bypass of PermissionKernel, HumanApprovalGateway, Emergency Halt, EvidenceRegistry or Financial Firewall.
