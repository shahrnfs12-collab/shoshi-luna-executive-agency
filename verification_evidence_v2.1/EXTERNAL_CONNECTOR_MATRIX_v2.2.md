# External Connector Matrix v2.2

| Capability | Code status | Runtime status | Gate |
|---|---|---|---|
| Trusted external-agent registry | READY_IN_CODE | NOT_RUNTIME_VERIFIED | User trust action required |
| HTTPS/A2A generic adapter | READY_IN_CODE | NOT_RUNTIME_VERIFIED | Real HTTPS endpoint required |
| Handshake | READY_IN_CODE | NOT_RUNTIME_VERIFIED | Remote protocol support required |
| Outbound external message | READY_IN_CODE | LOCKED | Approved mission + Human Approval |
| Inbound quarantine | READY_IN_CODE | NOT_RUNTIME_VERIFIED | External response required |
| Prompt-injection screening | READY_IN_CODE | STATIC_TESTABLE | Never treated as sufficient alone for hostile content |
| Research connector | CONTRACT_READY | NOT_CONFIGURED | Real provider/credentials required |
| CRM connector | CONTRACT_READY | NOT_CONFIGURED | Real provider/credentials required |
| Email connector | CONTRACT_READY | NOT_CONFIGURED | Real provider/credentials required |
| Financial external execution | LOCKED | NOT_CONFIGURED | Separate financial adapter + biometric/Keystore approval |

No capability above is reported VERIFIED_RUNTIME without a real sandbox execution trace.
