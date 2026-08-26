package com.shoshiluna.executive.core.external

import com.shoshiluna.executive.core.business.ToolGateway

/** Bridges ToolGateway requests into the existing hardened ExternalAgentGateway. */
class ExternalAgentToolAdapter : ToolGateway.ToolAdapter {
    override suspend fun execute(input: Map<String, String>): String {
        val missionId = input["mission_id"]?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("mission_id is required")
        val senderAgentId = input["sender_agent_id"]?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("sender_agent_id is required")
        val targetAgentId = input["target_agent_id"]?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("target_agent_id is required")
        val purpose = input["purpose"]?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("purpose is required")
        val body = input["body"]?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("body is required")
        val idempotencyKey = input["idempotency_key"]?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("idempotency_key is required")
        val approvalId = input["approval_id"]?.takeIf { it.isNotBlank() }

        val response = ExternalAgentGateway.send(
            ExternalAgentMessage(
                missionId = missionId,
                senderAgentId = senderAgentId,
                targetExternalAgentId = targetAgentId,
                purpose = purpose,
                body = body,
                idempotencyKey = idempotencyKey,
                approvalRequestId = approvalId
            )
        )
        return when (response.status) {
            ExternalMessageStatus.VERIFIED -> response.body.orEmpty()
            ExternalMessageStatus.APPROVAL_REQUIRED -> throw IllegalStateException("External-agent approval required: ${response.body.orEmpty()}")
            ExternalMessageStatus.QUARANTINED -> throw SecurityException("External-agent response quarantined: ${response.error.orEmpty()}")
            else -> throw IllegalStateException(response.error ?: "External-agent call failed: ${response.status}")
        }
    }
}
