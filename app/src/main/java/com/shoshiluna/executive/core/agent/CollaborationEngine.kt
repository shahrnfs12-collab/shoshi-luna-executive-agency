package com.shoshiluna.executive.core.agent

import com.shoshiluna.executive.core.empire.EmpireMissionControl
import com.shoshiluna.executive.core.empire.MissionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

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
        scope.launch {
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
                return@launch
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
                EmpireMissionControl.transitionStatus(
                    missionId,
                    MissionStatus.WAITING_FOR_EXTERNAL_RESPONSE,
                    "AI provider unavailable; no fabricated consultation result"
                )
                return@launch
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
            SovereignMemoryVault.record(
                SovereignMemory(
                    partition = MemoryPartition.MISSION_SCOPED,
                    agentId = targetId,
                    missionId = missionId,
                    content = reply
                )
            )
            EmpireMissionControl.transitionStatus(missionId, MissionStatus.REVIEW, "Executive consultation produced analysis; human review required")
        }
    }

    fun getResult(missionId: String): CollaborationResult? = results[missionId]
}
