package com.shoshiluna.executive.core.external

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object TrustedExternalAgentRegistry {
    const val CODEX_ONE_ID = "codex-1-openai"
    private val _agents = MutableStateFlow<Map<String, ExternalAgentDescriptor>>(emptyMap())
    val agents = _agents.asStateFlow()

    /** Installs the owner's first real external-agent candidate without claiming it is connected. */
    @Synchronized
    fun bootstrapCodexOne() {
        if (_agents.value.containsKey(CODEX_ONE_ID)) return
        val candidate = ExternalAgentDescriptor(
            id = CODEX_ONE_ID,
            displayName = "CODEX-1 • First Recruited Agent",
            endpoint = "https://api.openai.com/v1/responses",
            protocol = ExternalProtocol.OPENAI_RESPONSES,
            trustStatus = ExternalTrustStatus.PENDING,
            capabilities = setOf(
                "deep-research", "strategic-analysis", "coding", "debugging", "build-and-test",
                "security-review", "evidence-verification", "tool-design", "cost-benefit-analysis",
                "opportunity-discovery", "technical-writing", "hebrew-english", "agent-orchestration"
            )
        )
        _agents.value = _agents.value + (candidate.id to candidate)
    }

    @Synchronized
    fun registerCandidate(descriptor: ExternalAgentDescriptor): Result<ExternalAgentDescriptor> {
        require(descriptor.displayName.isNotBlank())
        EndpointPolicy.validate(descriptor.endpoint).getOrThrow()
        require(_agents.value.values.none { it.endpoint == descriptor.endpoint && it.id != descriptor.id }) { "Endpoint already registered" }
        val pending = descriptor.copy(trustStatus = ExternalTrustStatus.PENDING)
        _agents.value = _agents.value + (pending.id to pending)
        return Result.success(pending)
    }

    @Synchronized
    fun markTrusted(agentId: String, fingerprint: String?): ExternalAgentDescriptor {
        val current = _agents.value[agentId] ?: error("Unknown external agent")
        require(current.trustStatus != ExternalTrustStatus.REVOKED) { "Revoked agent cannot be trusted" }
        val updated = current.copy(
            trustStatus = ExternalTrustStatus.TRUSTED,
            publicKeyFingerprint = fingerprint?.takeIf { it.isNotBlank() }
        )
        _agents.value = _agents.value + (agentId to updated)
        return updated
    }

    @Synchronized fun suspend(agentId: String) = updateStatus(agentId, ExternalTrustStatus.SUSPENDED)
    @Synchronized fun revoke(agentId: String) = updateStatus(agentId, ExternalTrustStatus.REVOKED)
    fun get(agentId: String): ExternalAgentDescriptor? = _agents.value[agentId]
    fun trusted(): List<ExternalAgentDescriptor> = _agents.value.values.filter { it.trustStatus == ExternalTrustStatus.TRUSTED }

    private fun updateStatus(agentId: String, status: ExternalTrustStatus) {
        val current = _agents.value[agentId] ?: error("Unknown external agent")
        _agents.value = _agents.value + (agentId to current.copy(trustStatus = status))
    }

    @Synchronized fun resetForTest() { _agents.value = emptyMap() }
}
