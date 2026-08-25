package com.shoshiluna.executive.core.agent

import com.shoshiluna.executive.core.security.ApprovalRiskLevel
import com.shoshiluna.executive.core.security.HumanApprovalGateway
import java.util.UUID

enum class CapabilityBuildStatus { PROPOSED, AWAITING_APPROVAL, APPROVED, REJECTED, BUILDING, VERIFYING, VERIFIED, FAILED }

data class CapabilityBuildProposal(
    val id: String = UUID.randomUUID().toString(),
    val missionId: String?,
    val requestingAgent: ExecutiveAgent,
    val capabilityName: String,
    val gap: String,
    val whyNeeded: String,
    val plannedChanges: List<String>,
    val expectedToolsOrDependencies: List<String>,
    val verificationPlan: List<String>,
    val riskLevel: ApprovalRiskLevel,
    val approvalRequestId: String,
    val status: CapabilityBuildStatus = CapabilityBuildStatus.AWAITING_APPROVAL
)

/**
 * Plans missing capabilities but never self-modifies silently.
 * Any capability extension must be explained to the owner and explicitly approved first.
 */
object CapabilityBuildPlanner {
    @Volatile private var proposals: Map<String, CapabilityBuildProposal> = emptyMap()

    @Synchronized
    fun propose(
        missionId: String?,
        agent: ExecutiveAgent,
        capabilityName: String,
        gap: String,
        whyNeeded: String,
        plannedChanges: List<String>,
        expectedToolsOrDependencies: List<String> = emptyList(),
        verificationPlan: List<String> = listOf("Unit tests", "Lint", "Build", "Runtime verification"),
        riskLevel: ApprovalRiskLevel = ApprovalRiskLevel.MEDIUM
    ): CapabilityBuildProposal {
        require(capabilityName.isNotBlank()) { "Capability name is required" }
        require(gap.isNotBlank()) { "Capability gap is required" }
        require(whyNeeded.isNotBlank()) { "Reason is required" }
        require(plannedChanges.isNotEmpty()) { "Planned changes are required" }

        val action = buildString {
            append("Build missing capability: ").append(capabilityName)
            if (missionId != null) append(" for mission ").append(missionId)
        }
        val reason = buildString {
            append("Capability gap: ").append(gap).append(". ")
            append("Why needed: ").append(whyNeeded).append(". ")
            append("Planned changes: ").append(plannedChanges.joinToString(" | "))
            if (expectedToolsOrDependencies.isNotEmpty()) {
                append(". Tools/dependencies: ").append(expectedToolsOrDependencies.joinToString(", "))
            }
            append(". Verification: ").append(verificationPlan.joinToString(" -> "))
        }
        val approvalId = HumanApprovalGateway.requestApproval(
            action = action,
            agentId = agent.id,
            risk = riskLevel,
            reason = reason
        )
        val proposal = CapabilityBuildProposal(
            missionId = missionId,
            requestingAgent = agent,
            capabilityName = capabilityName.trim(),
            gap = gap.trim(),
            whyNeeded = whyNeeded.trim(),
            plannedChanges = plannedChanges,
            expectedToolsOrDependencies = expectedToolsOrDependencies,
            verificationPlan = verificationPlan,
            riskLevel = riskLevel,
            approvalRequestId = approvalId
        )
        proposals = proposals + (proposal.id to proposal)
        return proposal
    }

    @Synchronized
    fun get(id: String): CapabilityBuildProposal? = proposals[id]

    @Synchronized
    fun all(): List<CapabilityBuildProposal> = proposals.values.toList()

    @Synchronized
    fun canBuild(id: String): Boolean {
        val proposal = proposals[id] ?: return false
        return HumanApprovalGateway.isApproved(proposal.approvalRequestId)
    }

    @Synchronized
    fun markStatus(id: String, status: CapabilityBuildStatus) {
        val proposal = proposals[id] ?: return
        if (status in setOf(CapabilityBuildStatus.BUILDING, CapabilityBuildStatus.VERIFYING, CapabilityBuildStatus.VERIFIED) && !canBuild(id)) {
            throw IllegalStateException("Capability build is not approved")
        }
        proposals = proposals + (id to proposal.copy(status = status))
    }
}
