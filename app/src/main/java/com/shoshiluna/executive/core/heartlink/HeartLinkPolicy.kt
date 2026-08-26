package com.shoshiluna.executive.core.heartlink

object HeartLinkPolicy {
    private val forbiddenFragments = setOf(
        "../", "\\..\\", ".jks", ".keystore", "local.properties", "secrets", "api_key", "apikey"
    )

    fun validateProposal(proposal: HeartLinkProposal): HeartLinkDecision {
        if (proposal.objective.isBlank()) return HeartLinkDecision(false, "Objective is required")
        if (proposal.snapshotId.isBlank()) return HeartLinkDecision(false, "Snapshot is required before change")
        if (proposal.operations.isEmpty()) return HeartLinkDecision(false, "At least one bounded operation is required")
        proposal.operations.forEach { operation ->
            if (operation.target.isBlank() || operation.summary.isBlank()) return HeartLinkDecision(false, "Operation target and summary are required")
            if (forbiddenFragments.any { operation.target.contains(it, ignoreCase = true) }) {
                return HeartLinkDecision(false, "Secret/signing paths are outside the agent boundary")
            }
        }
        return HeartLinkDecision(true, "Proposal is structurally bounded")
    }

    fun canExecute(proposal: HeartLinkProposal): HeartLinkDecision {
        val structure = validateProposal(proposal)
        if (!structure.allowed) return structure
        val sensitive = proposal.operations.any { it.tool.risk != HeartLinkRisk.READ_ONLY }
        if (sensitive && proposal.approvalRef.isNullOrBlank()) return HeartLinkDecision(false, "Explicit approval is required")
        if (proposal.operations.any { it.tool.risk == HeartLinkRisk.DESTRUCTIVE }) return HeartLinkDecision(false, "Destructive tools are not exposed")
        return HeartLinkDecision(true, "Approved within bounded tool policy")
    }
}
