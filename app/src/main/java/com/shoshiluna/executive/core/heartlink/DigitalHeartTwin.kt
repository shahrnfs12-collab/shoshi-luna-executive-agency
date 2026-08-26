package com.shoshiluna.executive.core.heartlink

import com.shoshiluna.executive.core.business.EvidenceCategory
import com.shoshiluna.executive.core.business.EvidenceRegistry
import com.shoshiluna.executive.core.external.ExternalTrustStatus
import com.shoshiluna.executive.core.external.TrustedExternalAgentRegistry
import com.shoshiluna.executive.core.hardening.CapabilityRegistry
import com.shoshiluna.executive.core.hardening.CapabilityStatus

data class DigitalHeartSnapshot(
    val capturedAt: Long,
    val readyCapabilities: Int,
    val blockedCapabilities: Int,
    val degradedCapabilities: Int,
    val trustedExternalAgents: Int,
    val verifiedEvidence: Int,
    val capabilityDetails: Map<String, String>
)

/** Compact truth map exposed to CODEX-1; no credentials, signing keys or raw secret values. */
object DigitalHeartTwin {
    fun snapshot(now: Long = System.currentTimeMillis()): DigitalHeartSnapshot {
        val capabilities = CapabilityRegistry.snapshot()
        val agents = TrustedExternalAgentRegistry.agents.value.values
        val evidence = EvidenceRegistry.items.value.values
        return DigitalHeartSnapshot(
            capturedAt = now,
            readyCapabilities = capabilities.count { it.status == CapabilityStatus.READY },
            blockedCapabilities = capabilities.count { it.status in setOf(CapabilityStatus.LOCKED, CapabilityStatus.MISSING) },
            degradedCapabilities = capabilities.count { it.status == CapabilityStatus.DEGRADED },
            trustedExternalAgents = agents.count { it.trustStatus == ExternalTrustStatus.TRUSTED },
            verifiedEvidence = evidence.count { it.category == EvidenceCategory.VERIFIED_RESULT },
            capabilityDetails = capabilities.associate { it.id to "${it.status}: ${it.detail}" }
        )
    }
}
