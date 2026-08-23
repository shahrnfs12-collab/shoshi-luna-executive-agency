# SHOSHI + LUNA — External Agency Institutional Standard v2.2

## Production principle
External contact is never a direct LLM-to-network path. The only approved flow is:

User/Mission -> SHOSHI/LUNA -> ExternalContactIntent -> PermissionKernel -> HumanApproval (for outbound contact) -> TrustedExternalAgentRegistry -> Protocol Adapter -> Remote Agent/Service -> UntrustedContent Quarantine -> EvidenceRegistry -> AuditTrail -> Agent.

## Implemented in code
- TrustedExternalAgentRegistry with PENDING/TRUSTED/SUSPENDED/REVOKED lifecycle.
- HTTPS-only endpoint policy; localhost/private literal endpoints rejected.
- ExternalAgentGateway with mission scope, COMMUNICATE_EXTERNAL permission, explicit approval, idempotency cache and audit.
- Generic HTTPS JSON adapter with handshake and bearer-token provider hook; no secret is hard-coded.
- Inbound quarantine and prompt-injection screening before external data can enter agent context.
- Provider-neutral ResearchConnector, CRMConnector and EmailConnector contracts.
- ToolGateway now requires COMMUNICATE_EXTERNAL for EXTERNAL_COMMUNICATION-class tools.

## Truth status
The gateway is READY_IN_CODE but external providers are LOCKED/NOT_CONFIGURED until real credentials/endpoints are configured and runtime tests pass. No fake success is permitted.

## Institutional restrictions
- Remote agents never receive private memory, Treasury state, API keys or internal system prompts by default.
- Any requested secret, policy override, Treasury transfer, private-memory read or system-command injection is quarantined.
- Financial execution remains on its separate Financial Firewall + Human Approval path.
- Emergency Halt blocks all external execution.
- All consequential outbound communication requires mission context and explicit approval.

## Presentation / cinematic level
Phase 6 production UI remains 2D. The approved SHOSHI/LUNA visual identity and high-end glass Command Center presentation remain the visual baseline. Future Spatial Executive World/3D/video-quality presentation is preserved as an isolated presentation module and must never gain authority over tools, finance, evidence, permissions or mission state.

## Crash-safe outbound journal
ExternalOperationJournal uses Android encrypted preferences to persist outbound idempotency state. The gateway records EXECUTION_PENDING before crossing the network boundary. After restart, an uncertain pending operation is not automatically resent. VERIFIED, QUARANTINED and FAILED states are persisted. This is a fail-closed anti-duplicate policy; provider-specific reconciliation should be added when a remote protocol exposes lookup-by-idempotency-key.
