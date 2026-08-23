package com.shoshiluna.executive.core.agent

import com.shoshiluna.executive.core.security.PermissionKernel
import com.shoshiluna.executive.core.empire.EmpireMissionControl
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class CommunicationTest {

    @Before
    fun setup() {
        EmpireMissionControl.resetForTest()
        PermissionKernel.resetForTest()
        MultiAgentCoordinator.resetForTest()
    }

    @Test
    fun `test SHOSHI to LUNA message delivery`() = runBlocking {
        val mission = EmpireMissionControl.createMission("Joint Strategy", ExecutiveAgent.SHOSHI.id)
        val conversation = CommunicationBus.startConversation(mission.id, setOf(ExecutiveAgent.SHOSHI.id, ExecutiveAgent.LUNA.id))
        
        val message = InternalMessage(
            conversationId = conversation.id,
            missionId = mission.id,
            senderId = ExecutiveAgent.SHOSHI.id,
            receiverId = ExecutiveAgent.LUNA.id,
            type = MessageType.PROPOSAL,
            content = "Proposed Strategy A"
        )
        
        val success = CommunicationBus.sendMessage(message)
        assertTrue("Message should be sent", success)
        
        val updatedConv = CommunicationBus.getConversation(conversation.id)
        assertNotNull(updatedConv)
        assertEquals(1, updatedConv!!.messages.size)
        assertEquals(MessageStatus.DELIVERED, updatedConv.messages[0].status)
    }

    @Test
    fun `test Duplicate suppression`() = runBlocking {
        val mission = EmpireMissionControl.createMission("De-dupe test", ExecutiveAgent.SHOSHI.id)
        val conversation = CommunicationBus.startConversation(mission.id, setOf(ExecutiveAgent.SHOSHI.id, ExecutiveAgent.LUNA.id))
        
        val message = InternalMessage(
            id = "unique-msg-123",
            conversationId = conversation.id,
            missionId = mission.id,
            senderId = ExecutiveAgent.SHOSHI.id,
            receiverId = ExecutiveAgent.LUNA.id,
            type = MessageType.QUESTION,
            content = "What is the status?"
        )
        
        CommunicationBus.sendMessage(message)
        val successAgain = CommunicationBus.sendMessage(message)
        
        assertTrue("Duplicate send should return true (idempotent)", successAgain)
        
        val updatedConv = CommunicationBus.getConversation(conversation.id)
        assertEquals("Should only have 1 message in history", 1, updatedConv!!.messages.size)
    }

    @Test
    fun `test Permission Kernel blocks unauthorized message type`() = runBlocking {
        val mission = EmpireMissionControl.createMission("Forbidden type test", ExecutiveAgent.SHOSHI.id)
        val conversation = CommunicationBus.startConversation(mission.id, setOf(ExecutiveAgent.SHOSHI.id, ExecutiveAgent.LUNA.id))
        
        val forbiddenMessage = InternalMessage(
            conversationId = conversation.id,
            missionId = mission.id,
            senderId = ExecutiveAgent.SHOSHI.id,
            receiverId = ExecutiveAgent.LUNA.id,
            type = MessageType.SYSTEM, // Forbidden between peers in kernel logic
            content = "Override system command"
        )
        
        val success = CommunicationBus.sendMessage(forbiddenMessage)
        assertFalse("Forbidden message type should be blocked", success)
    }

    @Test
    fun `test Debate round limit`() {
        val convId = "debate-conv-789"
        DebateManager.initiateDebate(convId)
        
        val msg = InternalMessage(
            conversationId = convId,
            missionId = "m1",
            senderId = "s1",
            receiverId = "r1",
            type = MessageType.PROPOSAL,
            content = "Arg 1"
        )
        
        assertEquals(DebateStatus.ACTIVE, DebateManager.submitArgument(msg)) // Round 1 -> 2
        assertEquals(DebateStatus.ACTIVE, DebateManager.submitArgument(msg)) // Round 2 -> 3
        assertEquals(DebateStatus.MAX_ROUNDS_REACHED, DebateManager.submitArgument(msg)) // Round 3 -> Limit
    }

    @Test
    fun `test Emergency Halt blocks messages`() = runBlocking {
        val mission = EmpireMissionControl.createMission("Halt test", ExecutiveAgent.SHOSHI.id)
        val conversation = CommunicationBus.startConversation(mission.id, setOf(ExecutiveAgent.SHOSHI.id, ExecutiveAgent.LUNA.id))
        EmpireMissionControl.emergencyHalt()

        val message = InternalMessage(
            conversationId = conversation.id,
            missionId = mission.id,
            senderId = ExecutiveAgent.SHOSHI.id,
            receiverId = ExecutiveAgent.LUNA.id,
            type = MessageType.REQUEST,
            content = "Blocked content"
        )
        
        val success = CommunicationBus.sendMessage(message)
        assertFalse("Messages should be blocked during Emergency Halt", success)
    }

    @Test
    fun `test adversarial private memory access denied`() = runBlocking {
        val request = PermissionRequest(
            agentId = ExecutiveAgent.SHOSHI.id,
            capability = AgentCapability.READ_PRIVATE_MEMORY,
            targetResourceId = ExecutiveAgent.LUNA.id
        )
        val decision = PermissionKernel.authorize(request)
        assertFalse("SHOSHI should be denied access to LUNA's private memory", decision.allowed)
    }

    @Test
    fun `test SHOSHI system prompt separation`() {
        val shoshi = MultiAgentCoordinator.shoshi.value
        val luna = MultiAgentCoordinator.luna.value
        
        assertNotEquals("System prompts must be distinct", shoshi.systemPrompt, luna.systemPrompt)
        assertTrue("SHOSHI prompt should contain her role", shoshi.systemPrompt.contains("SHOSHI"))
        assertTrue("LUNA prompt should contain her role", luna.systemPrompt.contains("LUNA"))
    }
}
