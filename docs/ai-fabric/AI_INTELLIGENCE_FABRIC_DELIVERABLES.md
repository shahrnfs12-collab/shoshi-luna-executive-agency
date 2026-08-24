# SHOSHI • LUNA AI Intelligence Fabric

Verification snapshot: 2026-08-24. Capacity is 1,000 records. This document distinguishes documentation, configuration, connectivity, testing and verification; no provider is treated as live merely because it appears in a catalog.

## 1. Master Registry

The app ships a small documented seed set and two live discovery connectors: the official OpenRouter model catalog and the public Hugging Face Hub catalog. Discovery is bounded by `MAX_MODELS=1000`. Every record carries source, observation time, claim and optional response hash. Runtime status is one of `DISCOVERED`, `DOCUMENTED`, `CONFIGURED`, `CONNECTED`, `TESTED`, `VERIFIED`, `FAILED`.

Seed catalog: OpenAI GPT-5.6 Sol/Terra/Luna; Anthropic Claude Opus 4.7/Sonnet 4.6/Haiku 4.5; Google Gemini 3.7 Flash/3.5 Flash/2.5 Pro; one explicitly unconfigured local-model placeholder. Dynamic catalog entries are not assumed to be callable.

## 2. Domain Ranking

Ranking is produced per task, never as one universal leaderboard. Required capabilities are hard filters. Surviving candidates receive a weighted score: quality/status 24%, specialization 18%, reasoning 12%, latency 10%, reliability 12%, privacy 10%, cost 8%, context 3%, tool support 3%. Unmeasured attributes receive conservative neutral/low priors, not invented scores.

## 3. Architecture

`TaskClassifier → PolicyEngine → ModelRegistry/CapabilityRegistry → MetaRouter → SecureCredentialBroker → ProviderConnector → Verifier/Consensus → Response`

Supporting control-plane components are Evaluation Laboratory, Health Monitor, Cost Controller, tamper-evident Audit Ledger, observability dashboard and admin catalog discovery. Primary plus three fallbacks are chosen. Consensus supports up to five candidates; parallel/debate execution is policy-gated and must not be presented as executed until provider calls actually complete.

## 4. Security Threat Model

Protected assets: provider secrets, prompts, attachments, user identity, sensitive files, money and production systems. Major threats: secret disclosure, prompt injection, malicious tool calls, SSRF, supply-chain/catalog poisoning, data exfiltration, privilege escalation, replay, audit tampering and denial of wallet/service.

Controls: Android encrypted secret storage; per-provider secret keys; reference-only credential broker; TLS provider calls; allowlisted protocol definitions; capability and trust gates; zero-retention requirement for restricted data; attachment inspection; human approval for money/identity/production/sensitive-file actions; emergency halt; bounded failover; cost estimation; response provenance; hash-chained audit. Current ledger is tamper-evident in process, not yet an external WORM store. Production deployment should export signed batches to an append-only remote ledger.

## 5. Routing Policy

1. Classify capability, modality and sensitivity.
2. Determine minimum trust (T1 public, T2 internal/confidential, T3 restricted, T4 privileged, T5 critical action).
3. Reject unconfigured providers, insufficient trust, unsupported capabilities/modalities/context/tools, failed health, forbidden open-policy use and non-zero-retention routes for restricted data.
4. Estimate budget and rank candidates.
5. Select Primary, fallbacks and optional consensus set.
6. Inject the credential only inside the connector call.
7. Fail over only for network, quota/rate-limit and server failures; do not hide authentication or policy failures.
8. Record route and provider outcome with model provenance.

Open-policy models are capped at 10% of the registry and are excluded unless the task explicitly permits them. This pool is primarily for user-authorized local/open-weight inference.

## 6. Benchmark Matrix

The laboratory schema covers reasoning, coding, hallucination, instruction following, Hebrew, English, vision, long context, agentic tool use, latency, uptime, cost efficiency and security behavior. Every score requires a test-case ID and raw-evidence hash. Empty cells mean **not tested**, never zero or pass. Marketing claims are documentation evidence only and cannot create a benchmark score.

Promotion rule: a successful transport call may produce `CONNECTED`; a recorded benchmark may produce `TESTED`; only successful runtime evidence plus the required evaluation evidence may produce `VERIFIED`.

## 7. Provider Health Dashboard

The in-app AI Fabric dashboard shows registry counts by exact status, live catalog-discovery outcomes, latency, exponentially weighted reliability, consecutive failures and last check time. Three consecutive failed calls mark provider health `FAILED`; a later successful call restores connectivity evidence.

## 8. Cost Model

Estimated request cost is `(input tokens / 1,000,000 × input price) + (output tokens / 1,000,000 × output price)`. Unknown price remains unknown and cannot automatically pass a hard budget. Routing favors lower documented cost only after hard capability/security filters. Provider invoices remain authoritative.

## 9. Secrets Management Design

The Router and models see only `CredentialReference(providerId)`. `SecureCredentialBroker` resolves the secret from encrypted device storage inside a short-lived callback at call time. Secrets are not returned, cached by the fabric, placed in prompts, included in audit events or embedded in source. Each provider is isolated. Rotation replaces only that provider's encrypted entry. Production server deployments should use a managed vault, workload identity and short-lived credentials where supported.

## 10. Evidence Report

| Layer | Status at source checkout | Evidence |
|---|---|---|
| Seed model documentation | DOCUMENTED | Official model-page URLs stored per record |
| Hugging Face catalog connector | IMPLEMENTED, not automatically run | Public official Hub API connector |
| OpenRouter catalog connector | IMPLEMENTED, not automatically run | Official model endpoint; requires owner-provided key |
| Provider credentials | Device-dependent | Encrypted local storage; never included in repository |
| Provider API connectivity | NOT CLAIMED GLOBALLY | Becomes CONNECTED only after a successful live response |
| Uniform benchmark results | NOT YET OBSERVED | Schema and evidence gate implemented; no fabricated scores |
| Verified providers/models | NONE IN SOURCE | Verification requires successful call and evaluation evidence |
| Router/fallback policy | IMPLEMENTED | Unit-tested deterministic filtering/scoring |
| Audit ledger | IMPLEMENTED, in-process | Hash-chain verification; external WORM export remains deployment work |

Official discovery and seed sources: OpenAI model documentation, Anthropic model overview, Google Gemini model/pricing documentation, OpenRouter `/api/v1/models`, and Hugging Face Hub `/api/models`. Catalog contents and prices change; the `verifiedAt` timestamp must be refreshed rather than silently treated as current forever.
