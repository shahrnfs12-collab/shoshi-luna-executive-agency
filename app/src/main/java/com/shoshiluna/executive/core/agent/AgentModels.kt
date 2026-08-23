package com.shoshiluna.executive.core.agent

import java.util.UUID

enum class AgentRole {
    EXECUTIVE_OVERSIGHT,
    EXECUTIVE_STRATEGY,
    SPECIALIST,
    RECRUIT,
    TEMPORARY_AGENT
}

enum class ExecutiveAgent(val id: String) {
    SHOSHI("AGENT-SHOSHI-001"),
    LUNA("AGENT-LUNA-002")
}

enum class AgentCapability {
    READ_SHARED_MEMORY,
    WRITE_SHARED_MEMORY,
    READ_PRIVATE_MEMORY,
    WRITE_PRIVATE_MEMORY,
    RECRUIT_AGENT,
    MANAGE_MISSION,
    COMMUNICATE_EXTERNAL,
    ACCESS_KNOWLEDGE_BASE,
    EXECUTE_TOOL,
    MONITOR_TREASURY,
    PROPOSE_TREASURY_SPEND,
    TREASURY_SPEND
}

data class AgentIdentity(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val role: AgentRole,
    val parentId: String? = null,
    val allowedCapabilities: Set<AgentCapability> = emptySet(),
    val systemPrompt: String = "",
    val personalityTraits: List<String> = emptyList()
)

data class PermissionRequest(
    val requestId: String = UUID.randomUUID().toString(),
    val agentId: String,
    val capability: AgentCapability,
    val missionId: String? = null,
    val targetResourceId: String? = null
)

data class PermissionDecision(
    val allowed: Boolean,
    val reason: String,
    val auditId: String = UUID.randomUUID().toString()
)
