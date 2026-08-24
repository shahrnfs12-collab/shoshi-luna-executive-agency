package com.shoshiluna.executive.core.agent

import com.shoshiluna.executive.core.empire.EmpireMissionControl
import com.shoshiluna.executive.core.empire.MissionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import com.shoshiluna.executive.core.business.ArtifactFactory
import com.shoshiluna.executive.core.business.ArtifactType
import com.shoshiluna.executive.core.business.EvidenceCategory
import com.shoshiluna.executive.core.business.EvidenceRegistry
import com.shoshiluna.executive.core.security.ApprovalRiskLevel
import com.shoshiluna.executive.core.security.HumanApprovalGateway

/**
 * Executive consultation workflow. No canned "success" responses.
 * If the AI provider is offline, the mission is moved to WAITING_FOR_EXTERNAL_RESPONSE.
 */
object CollaborationEngine {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val results = ConcurrentHashMap<String, CollaborationResult>()
    private val provider: AiProvider = OpenAiProvider()

    fun startConsultation(missionId: String, initiatorId: String, targetId: String, topic: String) {
        if (EmpireMissionControl.state.value.emergencyHalt) return
        scope.launch { runConsultation(missionId, initiatorId, targetId, topic) }
    }

    suspend fun runConsultation(missionId: String, initiatorId: String, targetId: String, topic: String) {
        if (EmpireMissionControl.state.value.emergencyHalt) return
        val mission = EmpireMissionControl.state.value.missions.firstOrNull { it.id == missionId } ?: return
        if (mission.status !in setOf(MissionStatus.RUNNING, MissionStatus.EXECUTING, MissionStatus.WAITING_FOR_EXTERNAL_RESPONSE)) return
        val conversation = CommunicationBus.startConversation(missionId, setOf(initiatorId, targetId))
            val initialRequest = InternalMessage(
                conversationId = conversation.id,
                missionId = missionId,
                senderId = initiatorId,
                receiverId = targetId,
                type = MessageType.REQUEST,
                content = "Executive Consultation Request: $topic"
            )
            if (!CommunicationBus.sendMessage(initialRequest)) {
                EmpireMissionControl.transitionStatus(missionId, MissionStatus.FAILED, "Communication Bus denied request")
                return
            }

            val memory = SovereignMemoryVault.getMissionMemory(missionId) +
                SovereignMemoryVault.getPartition(MemoryPartition.EXECUTIVE_SHARED, targetId)
            val aiResult = provider.chatResult(
                prompt = "Review this mission/topic and give a concrete executive analysis, risks, evidence gaps and next actions: $topic",
                context = memory,
                missionId = missionId,
                agent = if (targetId == ExecutiveAgent.LUNA.id) ExecutiveAgent.LUNA else ExecutiveAgent.SHOSHI
            )
            val reply = when (aiResult) {
                is com.shoshiluna.executive.core.network.AiCallResult.Success -> aiResult.text
                is com.shoshiluna.executive.core.network.AiCallResult.Failure -> "AI ${aiResult.state}: ${aiResult.safeMessage}"
            }
            val offline = aiResult is com.shoshiluna.executive.core.network.AiCallResult.Failure
            val response = InternalMessage(
                conversationId = conversation.id,
                missionId = missionId,
                senderId = targetId,
                receiverId = initiatorId,
                type = MessageType.RESPONSE,
                content = reply
            )
            CommunicationBus.sendMessage(response)

            if (offline) {
                EmpireMissionControl.updatePlanStep(missionId, 2, "WAITING", "AI provider unavailable")
                EmpireMissionControl.transitionStatus(
                    missionId,
                    MissionStatus.WAITING_FOR_EXTERNAL_RESPONSE,
                    "AI provider unavailable; no fabricated consultation result"
                )
                return
            }

            val result = CollaborationResult(
                missionId = missionId,
                conversationId = conversation.id,
                shoshiContribution = if (initiatorId == ExecutiveAgent.SHOSHI.id) topic else reply,
                lunaContribution = if (targetId == ExecutiveAgent.LUNA.id) reply else topic,
                consensusReached = false,
                finalOutput = reply
            )
            results[missionId] = result
            EmpireMissionControl.updatePlanStep(missionId, 2, "COMPLETED", "Counterpart review completed")
            val artifact = ArtifactFactory.createArtifact(
                type = ArtifactType.REPORT,
                agentId = targetId,
                taskId = conversation.id,
                content = reply
            )
            EvidenceRegistry.record(
                category = EvidenceCategory.AI_INFERENCE,
                content = "Executive deliverable created with SHA-256 ${artifact.hash}",
                source = "CollaborationEngine",
                missionId = missionId,
                correlationId = artifact.id
            )
            EmpireMissionControl.updatePlanStep(missionId, 3, "COMPLETED", "Auditable deliverable created")
            EmpireMissionControl.updatePlanStep(missionId, 4, "WAITING_APPROVAL", "Owner review required")
            HumanApprovalGateway.requestApproval(
                action = "אישור התוצר למשימה: $topic",
                agentId = initiatorId,
                risk = ApprovalRiskLevel.MEDIUM,
                reason = "Executive deliverable ${artifact.id} is ready for owner review",
                missionId = missionId
            )
            SovereignMemoryVault.record(
                SovereignMemory(
                    partition = MemoryPartition.MISSION_SCOPED,
                    agentId = targetId,
                    missionId = missionId,
                    content = reply
                )
            )
        EmpireMissionControl.transitionStatus(missionId, MissionStatus.REVIEW, "Executive deliverable ${artifact.id} created; human review required")
    }

    fun getResult(missionId: String): CollaborationResult? = results[missionId]
}
