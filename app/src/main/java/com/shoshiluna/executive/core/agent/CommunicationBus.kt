package com.shoshiluna.executive.core.agent

import com.shoshiluna.executive.core.audit.AuditTrail
import com.shoshiluna.executive.core.audit.ExecutiveAudit
import com.shoshiluna.executive.core.security.PermissionKernel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap

/**
 * ShoshiLuna Executive Communication Bus.
 * Centralized, secure, and auditable routing for agent messages.
 */
object CommunicationBus {
    
    private val _conversations = MutableStateFlow<Map<String, Conversation>>(emptyMap())
    val conversations = _conversations.asStateFlow()

    private val processedMessageIds = ConcurrentHashMap.newKeySet<String>()
    private const val MAX_RETRIES = 2
    private const val TIMEOUT_MS = 5000L

    fun startConversation(missionId: String, participants: Set<String>): Conversation {
        val conversation = Conversation(missionId = missionId, participants = participants)
        _conversations.value = _conversations.value + (conversation.id to conversation)
        return conversation
    }

    suspend fun sendMessage(message: InternalMessage): Boolean {
        // Rule: Idempotency / Duplicate Suppression
        if (processedMessageIds.contains(message.id)) return true

        var retryCount = 0
        var success = false

        while (retryCount <= MAX_RETRIES && !success) {
            success = withTimeoutOrNull(TIMEOUT_MS) {
                attemptDelivery(message)
            } ?: false

            if (!success) {
                retryCount++
                if (retryCount <= MAX_RETRIES) delay(1000)
            }
        }

        if (!success) {
            markMessageFailed(message, MessageStatus.FAILED)
        }

        return success
    }

    private fun attemptDelivery(message: InternalMessage): Boolean {
        // Rule: Authorization via PermissionKernel
        val decision = PermissionKernel.authorizeMessage(message)
        
        AuditTrail.append(
            ExecutiveAudit(
                agentId = message.senderId,
                missionId = message.missionId,
                action = "SEND_MESSAGE [${message.type}]",
                result = if (decision.allowed) "AUTHORIZED" else "DENIED: ${decision.reason}",
                permissionDecisionId = decision.auditId
            )
        )

        if (!decision.allowed) return false

        // Rule: Route Message
        updateConversation(message, MessageStatus.DELIVERED)
        processedMessageIds.add(message.id)
        
        return true
    }

    private fun markMessageFailed(message: InternalMessage, status: MessageStatus) {
        updateConversation(message, status)
    }

    private fun updateConversation(message: InternalMessage, status: MessageStatus) {
        val current = _conversations.value[message.conversationId] ?: return
        val updatedMessage = message.copy(status = status)
        val updatedConversation = current.copy(
            messages = current.messages + updatedMessage
        )
        _conversations.value = _conversations.value + (message.conversationId to updatedConversation)
    }

    fun getConversation(conversationId: String): Conversation? = _conversations.value[conversationId]
}
