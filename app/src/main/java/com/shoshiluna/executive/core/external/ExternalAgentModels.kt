package com.shoshiluna.executive.core.external

import java.net.URI
import java.util.UUID

enum class ExternalTrustStatus { PENDING, TRUSTED, SUSPENDED, REVOKED }
enum class ExternalProtocol { GENERIC_HTTPS_JSON, OPENAI_RESPONSES, OPENROUTER_CHAT, A2A_HTTP, WEBHOOK }
enum class ExternalMessageStatus { PREPARED, APPROVAL_REQUIRED, SENT, RECEIVED, QUARANTINED, VERIFIED, FAILED }

data class ExternalAgentDescriptor(
    val id: String = UUID.randomUUID().toString(),
    val displayName: String,
    val endpoint: String,
    val protocol: ExternalProtocol,
    val trustStatus: ExternalTrustStatus = ExternalTrustStatus.PENDING,
    val publicKeyFingerprint: String? = null,
    val capabilities: Set<String> = emptySet(),
    val createdAt: Long = System.currentTimeMillis()
) {
    fun normalizedEndpoint(): URI = URI(endpoint.trim())
}

data class ExternalAgentMessage(
    val id: String = UUID.randomUUID().toString(),
    val missionId: String,
    val correlationId: String = UUID.randomUUID().toString(),
    val senderAgentId: String,
    val targetExternalAgentId: String,
    val purpose: String,
    val body: String,
    val idempotencyKey: String = UUID.randomUUID().toString(),
    val approvalRequestId: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

data class ExternalAgentResponse(
    val messageId: String,
    val externalAgentId: String,
    val status: ExternalMessageStatus,
    val body: String? = null,
    val evidenceId: String? = null,
    val error: String? = null,
    val receivedAt: Long = System.currentTimeMillis()
)

data class ExternalHandshakeResult(
    val accepted: Boolean,
    val remoteIdentity: String,
    val protocolVersion: String? = null,
    val capabilities: Set<String> = emptySet(),
    val proof: String? = null
)
