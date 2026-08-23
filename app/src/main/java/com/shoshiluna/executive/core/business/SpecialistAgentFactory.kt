package com.shoshiluna.executive.core.business

import com.shoshiluna.executive.core.agent.AgentCapability
import com.shoshiluna.executive.core.agent.AgentIdentity
import com.shoshiluna.executive.core.agent.AgentRole
import com.shoshiluna.executive.core.agent.MultiAgentCoordinator

/**
 * Factory for creating task-specific specialist agents.
 * Enforces: Child Permissions ⊆ Parent Permissions.
 */
object SpecialistAgentFactory {

    fun createMarketResearcher(parentId: String, missionId: String): AgentIdentity? {
        val capabilities = setOf(
            AgentCapability.READ_SHARED_MEMORY,
            AgentCapability.ACCESS_KNOWLEDGE_BASE,
            AgentCapability.EXECUTE_TOOL
        )
        return MultiAgentCoordinator.recruitSpecialist("Market Research Agent", parentId, capabilities)
    }

    fun createCompetitorIntelligenceAgent(parentId: String, missionId: String): AgentIdentity? {
        val capabilities = setOf(
            AgentCapability.READ_SHARED_MEMORY,
            AgentCapability.ACCESS_KNOWLEDGE_BASE,
            AgentCapability.EXECUTE_TOOL
        )
        return MultiAgentCoordinator.recruitSpecialist("Competitor Intelligence Agent", parentId, capabilities)
    }

    fun createProductAgent(parentId: String, missionId: String): AgentIdentity? {
        val capabilities = setOf(
            AgentCapability.READ_SHARED_MEMORY,
            AgentCapability.WRITE_SHARED_MEMORY,
            AgentCapability.EXECUTE_TOOL
        )
        return MultiAgentCoordinator.recruitSpecialist("Product Agent", parentId, capabilities)
    }

    fun createValidationAgent(parentId: String, missionId: String): AgentIdentity? {
        val capabilities = setOf(
            AgentCapability.READ_SHARED_MEMORY,
            AgentCapability.ACCESS_KNOWLEDGE_BASE,
            AgentCapability.EXECUTE_TOOL
        )
        return MultiAgentCoordinator.recruitSpecialist("Validation Agent", parentId, capabilities)
    }

    fun createLeadResearcher(parentId: String, missionId: String): AgentIdentity? {
        val capabilities = setOf(
            AgentCapability.READ_SHARED_MEMORY,
            AgentCapability.COMMUNICATE_EXTERNAL, // Scoped to leads
            AgentCapability.EXECUTE_TOOL
        )
        return MultiAgentCoordinator.recruitSpecialist("Lead Research Agent", parentId, capabilities)
    }
    
    fun createProposalAgent(parentId: String, missionId: String): AgentIdentity? {
        val capabilities = setOf(
            AgentCapability.READ_SHARED_MEMORY,
            AgentCapability.WRITE_SHARED_MEMORY,
            AgentCapability.EXECUTE_TOOL
        )
        return MultiAgentCoordinator.recruitSpecialist("Proposal Agent", parentId, capabilities)
    }

    fun createAnalyticsAgent(parentId: String, missionId: String): AgentIdentity? {
        val capabilities = setOf(
            AgentCapability.READ_SHARED_MEMORY,
            AgentCapability.ACCESS_KNOWLEDGE_BASE,
            AgentCapability.EXECUTE_TOOL
        )
        return MultiAgentCoordinator.recruitSpecialist("Analytics Agent", parentId, capabilities)
    }
}
