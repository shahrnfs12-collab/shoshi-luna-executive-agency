package com.shoshiluna.executive.core.business

import com.shoshiluna.executive.core.audit.AuditTrail
import com.shoshiluna.executive.core.audit.ExecutiveAudit
import java.util.UUID

/**
 * Service for verifying that allocated treasury funds have physically arrived.
 */
object TreasuryVerificationService {

    fun verifyFunding(allocationId: String, evidenceRef: String, verifierAgentId: String): Boolean {
        if (evidenceRef.isBlank()) return false
        if (!EvidenceRegistry.isVerified(evidenceRef)) return false
        if (TreasuryLedger.allocations.value.none { it.id == allocationId }) return false

        val auditId = UUID.randomUUID().toString()
        TreasuryLedger.updateAllocationStatus(allocationId, TreasuryStatus.FUNDED, evidenceRef)
        
        AuditTrail.append(
            ExecutiveAudit(
                agentId = verifierAgentId,
                action = "VERIFY_TREASURY_FUNDING",
                result = "SUCCESS: Treasury Funding Verified via $evidenceRef",
                permissionDecisionId = auditId
            )
        )
        
        return true
    }

    fun verifySpend(proposalId: String, evidenceRef: String, verifierAgentId: String): Boolean {
        if (evidenceRef.isBlank()) return false
        if (!EvidenceRegistry.isVerified(evidenceRef)) return false
        if (TreasuryLedger.spendProposals.value.none { it.id == proposalId }) return false

        val auditId = UUID.randomUUID().toString()
        TreasuryLedger.updateSpendStatus(proposalId, SpendStatus.VERIFIED, evidenceRef)
        
        AuditTrail.append(
            ExecutiveAudit(
                agentId = verifierAgentId,
                action = "VERIFY_TREASURY_SPEND",
                result = "SUCCESS: Spend Verified via $evidenceRef",
                permissionDecisionId = auditId
            )
        )
        
        return true
    }
}
