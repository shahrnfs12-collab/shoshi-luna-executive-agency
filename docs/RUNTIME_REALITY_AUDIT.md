# SHOSHI • LUNA — Runtime Reality Audit

Audit date: 2026-08-26
Branch: fix/v2.4.9.1-full-hardening

## Classification rules

- VERIFIED_REMOTE: a real external service returned a validated runtime response.
- LOCAL_REAL: implemented and executed on-device, with no claim of remote execution.
- ADVISORY_ONLY: AI-generated analysis; it has not browsed, published, contacted, sold, purchased, or transferred.
- SIMULATION: synthetic business/test state only.
- LOCKED: contract or UI exists, but no verified provider is connected.
- CANDIDATE: registered descriptor only; not trusted or operational.

## Findings

| Capability | Truth status | Evidence |
|---|---|---|
| AI chat through owner-configured CUSTOM/OpenRouter route | VERIFIED_REMOTE only after runtime test | OpenAiClient promotes AI only after a usable server response. |
| CODEX-1 current implementation | CANDIDATE / LOCAL WRAPPER | OpenAiResponsesAgentAdapter explicitly states it is an OpenRouter community runtime, not an authenticated OpenAI/Codex cloud session. |
| Revenue SCOUT / VALIDATOR / BUILDER / GROWTH | ADVISORY_ONLY | RevenueAgentWorker generates reports and expressly forbids claims of browsing, outreach, publishing, purchases, transfers, or verified demand. |
| Business pipeline and generated revenue records | SIMULATION by default | BusinessModels defaults Environment to SIMULATION and UI exposes RUN BUSINESS SIMULATION. |
| External research connector | LOCKED | No real provider configured. |
| External CRM connector | LOCKED | No real provider configured. |
| External email connector | LOCKED | No real provider configured. |
| GitHub connector | VERIFIED_REMOTE only after token, repository and real repository response | UI must not show connected before verified response. |
| Digital Heart Twin | LOCAL_REAL | Local capability/evidence topology snapshot only. |
| Voice input | LOCAL_REAL when Android speech runtime is available | AndroidSpeechToTextEngine. |
| Spatial 3D | LOCKED until GLB structure, identity and device runtime are all verified | CharacterFoundry promotion gate. |
| Financial distribution | CALCULATION ONLY until verified net revenue exists | No autonomous transfers; every external transfer remains owner-approved. |

## Required remediation

1. Replace the local CODEX-1 wrapper with a dedicated HTTPS cloud agent.
2. Require signed identity handshake, freshness, nonce replay protection and pinned public-key fingerprint.
3. Show cloud heartbeat, last verified response, protocol version and evidence ID in UI.
4. Never infer TRUSTED from a model saying “READY”.
5. Persist revenue-agent failure reasons and show them in UI.
6. Route long-form agents through openrouter/free with bounded retries and a realistic timeout.
7. Label every screen with VERIFIED_REMOTE, LOCAL_REAL, ADVISORY_ONLY, SIMULATION, LOCKED or CANDIDATE.
8. Keep publishing, outreach, code mutation, purchases and money movement behind explicit owner approval.
9. Do not report a daily revenue target as achieved revenue.
10. Block production claims until device runtime and remote service evidence both pass.
