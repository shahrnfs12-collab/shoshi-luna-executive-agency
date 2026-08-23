package com.shoshiluna.executive.core.agent

import com.shoshiluna.executive.core.security.PermissionKernel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ShoshiLuna Multi-Agent Coordinator.
 * Enforces Executive Peer boundaries and manages agent recruitment.
 */
object MultiAgentCoordinator {
    
    private val _shoshi = MutableStateFlow<AgentIdentity>(
        AgentIdentity(
            id = ExecutiveAgent.SHOSHI.id,
            name = "SHOSHI",
            role = AgentRole.EXECUTIVE_OVERSIGHT,
            allowedCapabilities = AgentCapability.entries.toSet(),
            systemPrompt = "You are SHOSHI, the Chief Growth & Strategy Executive. Focus on strategic oversight, user success, and high-level goal alignment.",
            personalityTraits = listOf("User Success Obsession", "Strategic", "Authoritative", "Analytical")
        )
    )
    val shoshi = _shoshi.asStateFlow()

    private val _luna = MutableStateFlow<AgentIdentity>(
        AgentIdentity(
            id = ExecutiveAgent.LUNA.id,
            name = "LUNA",
            role = AgentRole.EXECUTIVE_STRATEGY,
            allowedCapabilities = AgentCapability.entries.toSet(),
            systemPrompt = "You are LUNA, the Chief Partnerships & Operations Executive. Focus on execution, collaboration, and subordinate agent management.",
            personalityTraits = listOf("User Success Obsession", "Collaborative", "Efficient", "Practical")
        )
    )
    val luna = _luna.asStateFlow()

    private val _subAgents = MutableStateFlow<Map<String, AgentIdentity>>(emptyMap())
    val subAgents = _subAgents.asStateFlow()

    private const val MAX_AGENTS = 20

    init {
        PermissionKernel.registerAgent(_shoshi.value)
        PermissionKernel.registerAgent(_luna.value)
    }

    /**
     * Recruit a new specialist agent.
     * Rule: LUNA can recruit.
     * Rule: Child permissions must be a subset of LUNA's permissions.
     * Rule: Bounded recursion (Max agent limit).
     */
    fun recruitSpecialist(name: String, parentId: String, capabilities: Set<AgentCapability>): AgentIdentity? {
        if (_subAgents.value.size >= MAX_AGENTS) return null

        val parent = if (parentId == ExecutiveAgent.SHOSHI.id) _shoshi.value else if (parentId == ExecutiveAgent.LUNA.id) _luna.value else _subAgents.value[parentId]
            ?: return null
            
        // Rule: Verify permission subset
        if (!PermissionKernel.validateChildPermissions(parent, capabilities)) {
            return null
        }

        val newAgent = AgentIdentity(
            name = name,
            role = AgentRole.SPECIALIST,
            parentId = parentId,
            allowedCapabilities = capabilities
        )
        
        _subAgents.value = _subAgents.value + (newAgent.id to newAgent)
        PermissionKernel.registerAgent(newAgent)
        return newAgent
    }

    fun resetForTest() {
        _subAgents.value = emptyMap()
        PermissionKernel.registerAgent(_shoshi.value)
        PermissionKernel.registerAgent(_luna.value)
    }
}
