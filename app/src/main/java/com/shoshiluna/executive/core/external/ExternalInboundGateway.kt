package com.shoshiluna.executive.core.external

import com.shoshiluna.executive.core.business.EvidenceCategory
import com.shoshiluna.executive.core.business.EvidenceRegistry
import com.shoshiluna.executive.core.security.PermissionKernel
import com.shoshiluna.executive.core.security.TrustLevel
import com.shoshiluna.executive.core.security.UntrustedContent

object ExternalInboundGateway {
    data class InboundResult(
        val accepted: Boolean,
        val safeContent: String? = null,
        val evidenceId: String? = null,
        val reason: String? = null
    )

    fun quarantineAndInspect(sourceAgentId: String, missionId: String, rawBody: String): InboundResult {
        val source = TrustedExternalAgentRegistry.get(sourceAgentId)
            ?: return InboundResult(false, reason = "Unknown external source")
        if (source.trustStatus != ExternalTrustStatus.TRUSTED) return InboundResult(false, reason = "External source is not trusted")
        val content = UntrustedContent(rawData = rawBody, source = "external-agent:${source.id}", trustLevel = TrustLevel.EXTERNAL_UNTRUSTED)
        val decision = PermissionKernel.analyzeUntrustedContent(content)
        if (!decision.allowed) {
            val evidence = EvidenceRegistry.record(EvidenceCategory.ASSUMPTION, "External content quarantined: ${decision.reason}", content.source, missionId)
            return InboundResult(false, evidenceId = evidence, reason = decision.reason)
        }
        val evidence = EvidenceRegistry.record(EvidenceCategory.VERIFIED_RESULT, "External response accepted after untrusted-content inspection", content.source, missionId)
        return InboundResult(true, content.getSafeRepresentation(), evidence, null)
    }
}
