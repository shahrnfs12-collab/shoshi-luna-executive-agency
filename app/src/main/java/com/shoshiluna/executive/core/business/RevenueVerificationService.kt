package com.shoshiluna.executive.core.business

import com.shoshiluna.executive.core.audit.AuditTrail
import com.shoshiluna.executive.core.audit.ExecutiveAudit
import java.util.UUID

/**
 * Boundary service for verifying received payments.
 * Ensures AI agents cannot fabricate revenue success.
 */
object RevenueVerificationService {

    fun verifyAndPost(recordId: String, evidenceRef: String, verifierAgentId: String): Boolean {
        if (evidenceRef.isBlank()) return false
        if (!EvidenceRegistry.isVerified(evidenceRef)) return false
        if (RevenueLedger.records.value.none { it.id == recordId }) return false

        val auditId = UUID.randomUUID().toString()
        RevenueLedger.updateStatus(recordId, RevenueStatus.RECEIVED, evidenceRef)
        
        AuditTrail.append(
            ExecutiveAudit(
                agentId = verifierAgentId,
                action = "VERIFY_REVENUE",
                result = "SUCCESS: Revenue Verified via $evidenceRef",
                permissionDecisionId = auditId
            )
        )
        
        return true
    }
}
