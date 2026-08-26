package com.shoshiluna.executive.core.business

import java.util.UUID

enum class EvidenceCategory {
    FACT,
    EXTERNAL_EVIDENCE,
    USER_PROVIDED,
    ESTIMATE,
    ASSUMPTION,
    AI_INFERENCE,
    PROJECTION,
    VERIFIED_RESULT
}

enum class Environment {
    REAL,
    SIMULATION
}

enum class OpportunityStatus {
    DISCOVERED,
    RESEARCHING,
    EVALUATING,
    VALIDATED,
    PRIORITIZED,
    PLANNING,
    APPROVED,
    REJECTED
}

data class Opportunity(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val problem: String,
    val targetCustomer: String,
    val proposedSolution: String,
    val monetizationModel: String,
    val scoring: Map<String, Int> = emptyMap(),
    val confidence: Int = 0,
    val status: OpportunityStatus = OpportunityStatus.DISCOVERED,
    val environment: Environment = Environment.SIMULATION,
    val evidenceIds: List<String> = emptyList(),
    val originatingAgentId: String,
    val missionId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

enum class LeadStatus {
    DISCOVERED,
    QUALIFIED,
    CONTACT_READY,
    CONTACTED,
    RESPONDED,
    PROPOSAL,
    NEGOTIATING,
    WON,
    LOST,
    ARCHIVED
}

data class Lead(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val contactInfo: String,
    val opportunityId: String,
    val status: LeadStatus = LeadStatus.DISCOVERED,
    val environment: Environment = Environment.SIMULATION,
    val auditRefs: List<String> = emptyList(),
    val missionId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

enum class RevenueStatus {
    PROJECTED,
    PIPELINE,
    COMMITTED,
    INVOICED,
    RECEIVED,
    SIMULATED
}

enum class TreasuryStatus {
    ALLOCATED,
    FUNDED
}

data class TreasuryAllocation(
    val id: String = UUID.randomUUID().toString(),
    val calculationPeriod: String? = null,
    val sourceRevenueIds: List<String>,
    val sourceCostIds: List<String>,
    val verifiedGrossRevenue: Double,
    val verifiedCosts: Double,
    val verifiedNetProfit: Double,
    val allocationRate: Double = ProfitDistributionPolicy.DEVELOPMENT_RATE,
    val allocationAmount: Double,
    val distribution: ProfitDistributionBreakdown,
    val status: TreasuryStatus = TreasuryStatus.ALLOCATED,
    val environment: Environment = Environment.SIMULATION,
    val createdAt: Long = System.currentTimeMillis(),
    val auditId: String = UUID.randomUUID().toString(),
    val verificationRef: String? = null
)

/**
 * Owner-defined distribution of VERIFIED NET PROFIT.
 * Destination aliases are deliberately non-sensitive: routing details never belong in AI context.
 */
object ProfitDistributionPolicy {
    const val BANK_RATE = 0.40
    const val DIGITAL_WALLET_RATE = 0.40
    const val DEVELOPMENT_RATE = 0.20
    const val BANK_DESTINATION_ALIAS = "OWNER_BANK"
    const val DIGITAL_WALLET_DESTINATION_ALIAS = "OWNER_DIGITAL_WALLET"
    const val DEVELOPMENT_DESTINATION_ALIAS = "SHOSHI_LUNA_DEVELOPMENT"

    init {
        check(BANK_RATE + DIGITAL_WALLET_RATE + DEVELOPMENT_RATE == 1.0) {
            "Profit distribution must total 100%"
        }
    }
}

data class ProfitDistributionBreakdown(
    val verifiedNetProfit: Double,
    val bankAmount: Double,
    val digitalWalletAmount: Double,
    val developmentAmount: Double,
    val bankDestinationAlias: String = ProfitDistributionPolicy.BANK_DESTINATION_ALIAS,
    val digitalWalletDestinationAlias: String = ProfitDistributionPolicy.DIGITAL_WALLET_DESTINATION_ALIAS,
    val developmentDestinationAlias: String = ProfitDistributionPolicy.DEVELOPMENT_DESTINATION_ALIAS,
    val requiresOwnerApproval: Boolean = true
) {
    val totalAmount: Double get() = bankAmount + digitalWalletAmount + developmentAmount
}

data class TreasurySpendProposal(
    val id: String = UUID.randomUUID().toString(),
    val missionId: String,
    val requesterAgentId: String,
    val amount: Double,
    val description: String,
    val status: SpendStatus = SpendStatus.PROPOSED,
    val timestamp: Long = System.currentTimeMillis(),
    val auditId: String = UUID.randomUUID().toString(),
    val verificationRef: String? = null
)

enum class SpendStatus {
    PROPOSED,
    APPROVED,
    EXECUTION_PENDING,
    EXECUTED,
    VERIFIED,
    REJECTED
}

data class RevenueRecord(
    val id: String = UUID.randomUUID().toString(),
    val amount: Double,
    val currency: String = "USD",
    val status: RevenueStatus,
    val missionId: String,
    val environment: Environment = Environment.SIMULATION,
    val timestamp: Long = System.currentTimeMillis(),
    val verificationRef: String? = null,
    val originatingAgentId: String? = null
)

data class CostRecord(
    val id: String = UUID.randomUUID().toString(),
    val amount: Double,
    val currency: String = "USD",
    val category: String, // infrastructure, tools, etc.
    val missionId: String,
    val environment: Environment = Environment.SIMULATION,
    val timestamp: Long = System.currentTimeMillis(),
    val verified: Boolean = false
)

enum class ExperimentStatus {
    SIMULATED,
    PLANNED,
    APPROVED,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class BusinessExperiment(
    val id: String = UUID.randomUUID().toString(),
    val hypothesis: String,
    val evidence: List<String> = emptyList(),
    val assumptions: List<String> = emptyList(),
    val successMetric: String,
    val failureMetric: String,
    val costEstimate: Double = 0.0,
    val expectedDuration: String? = null,
    val riskLevel: String = "LOW",
    val approvalRequirement: String = "OWNER",
    val observation: String? = null,
    val outcome: String? = null,
    val lesson: String? = null,
    val status: ExperimentStatus = ExperimentStatus.PLANNED,
    val environment: Environment = Environment.SIMULATION,
    val missionId: String? = null
)

data class BusinessHypothesis(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val evidenceIds: List<String> = emptyList(),
    val confidence: Int = 0
)

data class MarketResearch(
    val id: String = UUID.randomUUID().toString(),
    val topic: String,
    val findings: String,
    val sourceRefs: List<String> = emptyList()
)

data class CompetitorProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val strengths: List<String> = emptyList(),
    val weaknesses: List<String> = emptyList(),
    val marketShare: String? = null
)

data class BusinessProposal(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val content: String,
    val targetLeadId: String,
    val valueProposition: String,
    val pricing: String,
    val version: Int = 1
)

data class EvidenceItem(
    val id: String = UUID.randomUUID().toString(),
    val category: EvidenceCategory,
    val content: String,
    val source: String,
    val missionId: String? = null,
    val correlationId: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

data class Assumption(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val risk: String = "MEDIUM"
)

data class BusinessMetric(
    val key: String,
    val value: Double,
    val unit: String = ""
)

data class BusinessDecision(
    val id: String = UUID.randomUUID().toString(),
    val missionId: String,
    val decision: String,
    val reason: String,
    val agentId: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class ApprovalRequest(
    val id: String = UUID.randomUUID().toString(),
    val missionId: String,
    val action: String,
    val requesterAgentId: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class AgentAssignment(
    val id: String = UUID.randomUUID().toString(),
    val missionId: String,
    val agentId: String,
    val task: String
)
