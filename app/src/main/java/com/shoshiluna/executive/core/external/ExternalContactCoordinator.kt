package com.shoshiluna.executive.core.external

import com.shoshiluna.executive.core.agent.ExecutiveAgent
import com.shoshiluna.executive.core.business.EvidenceCategory
import com.shoshiluna.executive.core.business.EvidenceRegistry
import com.shoshiluna.executive.core.empire.EmpireMissionControl
import com.shoshiluna.executive.core.empire.MissionStatus
import java.util.UUID

/**
 * Converts an executive intent into a reviewable, non-executing contact proposal.
 * An LLM may propose contact, but only explicit user-approved send() can cross the network boundary.
 */
object ExternalContactCoordinator {
    data class ContactProposal(
        val id: String = UUID.randomUUID().toString(),
        val missionId: String,
        val executive: ExecutiveAgent,
        val externalAgentId: String,
        val purpose: String,
        val draftBody: String,
        val evidenceId: String,
        val createdAt: Long = System.currentTimeMillis()
    )

    private val proposals = linkedMapOf<String, ContactProposal>()

    @Synchronized
    fun propose(
        missionId: String,
        executive: ExecutiveAgent,
        externalAgentId: String,
        purpose: String,
        draftBody: String
    ): ContactProposal {
        val mission = EmpireMissionControl.state.value.missions.find { it.id == missionId } ?: error("Mission not found")
        require(mission.status in setOf(MissionStatus.APPROVED, MissionStatus.EXECUTING, MissionStatus.RUNNING)) { "Mission is not authorized for external contact" }
        val external = TrustedExternalAgentRegistry.get(externalAgentId) ?: error("External agent not registered")
        require(external.trustStatus == ExternalTrustStatus.TRUSTED) { "External agent is not trusted" }
        require(purpose.isNotBlank() && draftBody.isNotBlank())
        val evidence = EvidenceRegistry.record(
            EvidenceCategory.ASSUMPTION,
            "External contact proposal prepared for ${external.displayName}: ${purpose.trim()}",
            "ExternalContactCoordinator",
            missionId = missionId
        )
        val proposal = ContactProposal(
            missionId = missionId,
            executive = executive,
            externalAgentId = externalAgentId,
            purpose = purpose.trim(),
            draftBody = draftBody.trim(),
            evidenceId = evidence
        )
        proposals[proposal.id] = proposal
        return proposal
    }

    fun get(id: String): ContactProposal? = synchronized(this) { proposals[id] }
    fun all(): List<ContactProposal> = synchronized(this) { proposals.values.toList() }

    fun toMessage(proposalId: String, approvalRequestId: String? = null): ExternalAgentMessage {
        val p = get(proposalId) ?: error("Unknown contact proposal")
        return ExternalAgentMessage(
            missionId = p.missionId,
            senderAgentId = p.executive.id,
            targetExternalAgentId = p.externalAgentId,
            purpose = p.purpose,
            body = p.draftBody,
            idempotencyKey = "external-contact:${p.id}",
            approvalRequestId = approvalRequestId
        )
    }

    @Synchronized fun resetForTest() { proposals.clear() }
}
