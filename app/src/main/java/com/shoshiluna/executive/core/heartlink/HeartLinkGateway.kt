package com.shoshiluna.executive.core.heartlink

import com.shoshiluna.executive.core.business.EvidenceCategory
import com.shoshiluna.executive.core.business.EvidenceRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Fail-closed orchestration journal. Actual file/build tools live behind the private MCP bridge. */
object HeartLinkGateway {
    private val _proposals = MutableStateFlow<Map<String, HeartLinkProposal>>(emptyMap())
    val proposals = _proposals.asStateFlow()

    @Synchronized fun submit(proposal: HeartLinkProposal): Result<HeartLinkProposal> = runCatching {
        val decision = HeartLinkPolicy.validateProposal(proposal)
        require(decision.allowed) { decision.reason }
        require(!_proposals.value.containsKey(proposal.id)) { "Duplicate proposal id" }
        _proposals.value = _proposals.value + (proposal.id to proposal)
        proposal
    }

    @Synchronized fun approve(proposalId: String, approvalRef: String): HeartLinkProposal {
        require(approvalRef.isNotBlank()) { "Approval reference is required" }
        val current = requireNotNull(_proposals.value[proposalId]) { "Unknown proposal" }
        require(current.status == HeartLinkStatus.PROPOSED) { "Only proposed work can be approved" }
        return update(current.copy(status = HeartLinkStatus.APPROVED, approvalRef = approvalRef.trim()))
    }

    @Synchronized fun start(proposalId: String): HeartLinkProposal {
        val current = requireNotNull(_proposals.value[proposalId]) { "Unknown proposal" }
        val decision = HeartLinkPolicy.canExecute(current)
        require(decision.allowed) { decision.reason }
        require(current.status == HeartLinkStatus.APPROVED) { "Proposal is not approved" }
        return update(current.copy(status = HeartLinkStatus.RUNNING))
    }

    @Synchronized fun finish(proposalId: String, verified: Boolean, resultSummary: String): HeartLinkProposal {
        require(resultSummary.isNotBlank())
        val current = requireNotNull(_proposals.value[proposalId]) { "Unknown proposal" }
        require(current.status == HeartLinkStatus.RUNNING) { "Proposal is not running" }
        return if (verified) {
            val evidence = EvidenceRegistry.record(
                EvidenceCategory.VERIFIED_RESULT,
                resultSummary,
                "HEARTLINK",
                correlationId = current.id
            )
            update(current.copy(status = HeartLinkStatus.VERIFIED, evidenceIds = current.evidenceIds + evidence))
        } else update(current.copy(status = HeartLinkStatus.FAILED, error = resultSummary))
    }

    @Synchronized fun markRolledBack(proposalId: String, evidenceRef: String): HeartLinkProposal {
        require(evidenceRef.isNotBlank())
        val current = requireNotNull(_proposals.value[proposalId]) { "Unknown proposal" }
        require(current.status in setOf(HeartLinkStatus.FAILED, HeartLinkStatus.VERIFIED))
        val evidence = EvidenceRegistry.record(EvidenceCategory.VERIFIED_RESULT, "Rollback verified: $evidenceRef", "HEARTLINK", correlationId = current.id)
        return update(current.copy(status = HeartLinkStatus.ROLLED_BACK, evidenceIds = current.evidenceIds + evidence))
    }

    private fun update(value: HeartLinkProposal): HeartLinkProposal {
        _proposals.value = _proposals.value + (value.id to value)
        return value
    }

    @Synchronized fun resetForTest() { _proposals.value = emptyMap() }
}
