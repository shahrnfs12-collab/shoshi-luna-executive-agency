package com.shoshiluna.executive.core.agent

import java.util.UUID

enum class MessageType {
    REQUEST,
    RESPONSE,
    PROPOSAL,
    COUNTER_PROPOSAL,
    QUESTION,
    ANSWER,
    DELEGATION,
    RESULT,
    WARNING,
    CONSENSUS,
    DISAGREEMENT,
    SYSTEM
}

enum class MessageStatus {
    PENDING,
    DELIVERED,
    PROCESSING,
    COMPLETED,
    FAILED,
    CANCELLED,
    TIMED_OUT
}

data class InternalMessage(
    val id: String = UUID.randomUUID().toString(),
    val conversationId: String,
    val missionId: String,
    val senderId: String,
    val receiverId: String,
    val type: MessageType,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val status: MessageStatus = MessageStatus.PENDING,
    val correlationId: String? = null,
    val metadata: Map<String, String> = emptyMap()
)

data class Conversation(
    val id: String = UUID.randomUUID().toString(),
    val missionId: String,
    val participants: Set<String>,
    val messages: List<InternalMessage> = emptyList(),
    val isActive: Boolean = true,
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long? = null
)

data class CollaborationResult(
    val missionId: String,
    val conversationId: String,
    val shoshiContribution: String?,
    val lunaContribution: String?,
    val consensusReached: Boolean,
    val finalOutput: String,
    val unresolvedIssues: List<String> = emptyList()
)
