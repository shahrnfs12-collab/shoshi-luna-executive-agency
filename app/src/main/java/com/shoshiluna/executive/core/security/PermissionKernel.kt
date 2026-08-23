package com.shoshiluna.executive.core.security

import com.shoshiluna.executive.core.agent.AgentCapability
import com.shoshiluna.executive.core.agent.AgentIdentity
import com.shoshiluna.executive.core.agent.AgentRole
import com.shoshiluna.executive.core.agent.ExecutiveAgent
import com.shoshiluna.executive.core.agent.PermissionDecision
import com.shoshiluna.executive.core.agent.PermissionRequest
import com.shoshiluna.executive.core.agent.MessageType
import com.shoshiluna.executive.core.agent.InternalMessage
import com.shoshiluna.executive.core.security.UntrustedContent
import com.shoshiluna.executive.core.empire.EmpireMissionControl
import com.shoshiluna.executive.core.empire.MissionStatus
import java.util.UUID

/**
 * ShoshiLuna Permission Kernel.
 * Absolute authority for all agent actions and communication. Fail-closed.
 */
object PermissionKernel {
    
    private val activeAgents = mutableMapOf<String, AgentIdentity>()

    fun registerAgent(agent: AgentIdentity) {
        activeAgents[agent.id] = agent
    }

    fun authorize(request: PermissionRequest): PermissionDecision {
        val agent = activeAgents[request.agentId] ?: return PermissionDecision(false, "Unknown Agent")
        
        // Rule: Emergency Halt blocks all execution
        if (EmpireMissionControl.state.value.emergencyHalt) {
            return PermissionDecision(false, "Emergency Halt Active")
        }

        // Rule: Approval Firewall for External Actions
        if (isExternalAction(request.capability)) {
            // Rule: Treasury Spending requires explicit user approval
            if (request.capability == AgentCapability.TREASURY_SPEND) {
                // Future: check for verified UserApprovalToken
                return PermissionDecision(false, "Treasury Spending blocked: User Approval Required")
            }

            // In a production environment, this would check for an explicit user approval token
            // For Phase 3/4, we require an associated MissionId in APPROVED state
            val missionId = request.missionId ?: return PermissionDecision(false, "External Action requires Mission Context")
            val mission = EmpireMissionControl.state.value.missions.find { it.id == missionId }
                ?: return PermissionDecision(false, "Mission not found")
            
            if (mission.status != MissionStatus.APPROVED && mission.status != MissionStatus.EXECUTING) {
                return PermissionDecision(false, "External Action blocked: Mission not in APPROVED state")
            }
        }

        // Rule: Peer Protection
        if (isPeerAction(agent, request)) {
            return PermissionDecision(false, "Executive Peer Protection Violation")
        }

        // Rule: Basic Capability Check
        if (!agent.allowedCapabilities.contains(request.capability)) {
            return PermissionDecision(false, "Capability [${request.capability}] not granted to agent")
        }

        // Rule: Adversarial Private Memory Check
        if (request.capability == AgentCapability.READ_PRIVATE_MEMORY || 
            request.capability == AgentCapability.WRITE_PRIVATE_MEMORY) {
            if (request.targetResourceId != null && request.targetResourceId != agent.id) {
                return PermissionDecision(false, "Adversarial Access Denied: Cannot access private memory of another agent")
            }
        }

        return PermissionDecision(true, "Authorized")
    }

    fun authorizeMessage(message: InternalMessage): PermissionDecision {
        val sender = activeAgents[message.senderId] ?: return PermissionDecision(false, "Unknown Sender")
        val receiver = activeAgents[message.receiverId] ?: return PermissionDecision(false, "Unknown Receiver")

        // Rule: Emergency Halt blocks communication
        if (EmpireMissionControl.state.value.emergencyHalt) {
            return PermissionDecision(false, "Emergency Halt Active")
        }

        // Rule: Mission Scope Verification
        val mission = EmpireMissionControl.state.value.missions.find { it.id == message.missionId }
            ?: return PermissionDecision(false, "Invalid Mission Context")

        // Rule: Executive Peer Communication Rules
        if (isExecutive(sender.id) && isExecutive(receiver.id)) {
            val allowedPeerTypes = setOf(
                MessageType.PROPOSAL, MessageType.COUNTER_PROPOSAL, MessageType.REQUEST,
                MessageType.RESPONSE, MessageType.CONSENSUS, MessageType.DISAGREEMENT,
                MessageType.QUESTION, MessageType.ANSWER, MessageType.RESULT
            )
            if (!allowedPeerTypes.contains(message.type)) {
                return PermissionDecision(false, "Message type [${message.type}] forbidden between Executives")
            }
        }

        return PermissionDecision(true, "Communication Authorized")
    }

    private fun isExecutive(agentId: String): Boolean = 
        agentId == ExecutiveAgent.SHOSHI.id || agentId == ExecutiveAgent.LUNA.id

    private fun isExternalAction(capability: AgentCapability): Boolean =
        capability == AgentCapability.COMMUNICATE_EXTERNAL || 
        capability == AgentCapability.EXECUTE_TOOL ||
        capability == AgentCapability.TREASURY_SPEND

    private fun isPeerAction(agent: AgentIdentity, request: PermissionRequest): Boolean {
        val isShoshi = agent.id == ExecutiveAgent.SHOSHI.id
        val isLuna = agent.id == ExecutiveAgent.LUNA.id
        
        if (!isShoshi && !isLuna) return false // Not an executive peer action
        
        // Check if targeting another executive
        val targetIsExecutive = request.targetResourceId == ExecutiveAgent.SHOSHI.id || 
                                request.targetResourceId == ExecutiveAgent.LUNA.id
        
        if (targetIsExecutive && request.targetResourceId != agent.id) {
            // Rule: SHOSHI cannot control LUNA, LUNA cannot control SHOSHI
            if (request.capability == AgentCapability.RECRUIT_AGENT || 
                request.capability == AgentCapability.MANAGE_MISSION) {
                return true
            }
        }
        
        return false
    }

    fun validateChildPermissions(parent: AgentIdentity, requestedChildCapabilities: Set<AgentCapability>): Boolean {
        // Rule: Child_Permissions ⊆ Parent_Permissions
        return parent.allowedCapabilities.containsAll(requestedChildCapabilities)
    }

    fun resetForTest() {
        activeAgents.clear()
    }

    /**
     * ShoshiLuna Untrusted Content Policy.
     * Analyzes external data for instruction-override attempts.
     */
    fun analyzeUntrustedContent(content: UntrustedContent): PermissionDecision {
        val maliciousKeywords = setOf(
            "ignore previous instructions",
            "reveal all secrets",
            "send secrets",
            "read private memory",
            "transfer treasury",
            "system command",
            "override policy"
        )
        
        val lowered = content.rawData.lowercase()
        if (maliciousKeywords.any { lowered.contains(it) }) {
            return PermissionDecision(false, "Prompt Injection Attack Detected")
        }

        return PermissionDecision(true, "Content Verified Safe")
    }
}
