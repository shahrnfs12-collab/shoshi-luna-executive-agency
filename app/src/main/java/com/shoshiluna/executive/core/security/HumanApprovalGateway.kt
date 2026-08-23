package com.shoshiluna.executive.core.security

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

enum class ApprovalRiskLevel {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

data class ApprovalRequest(
    val id: String = UUID.randomUUID().toString(),
    val requestedAction: String,
    val requestingAgentId: String,
    val riskLevel: ApprovalRiskLevel,
    val reason: String,
    val timestamp: Long = System.currentTimeMillis(),
    var userDecision: Boolean? = null,
    var executionResult: String? = null
)

/**
 * ShoshiLuna Human Approval Gateway.
 * Absolute firewall for sensitive autonomous actions.
 */
object HumanApprovalGateway {
    
    private val _requests = MutableStateFlow<Map<String, ApprovalRequest>>(emptyMap())
    val requests = _requests.asStateFlow()

    @Synchronized
    fun requestApproval(
        action: String,
        agentId: String,
        risk: ApprovalRiskLevel,
        reason: String
    ): String {
        val request = ApprovalRequest(
            requestedAction = action,
            requestingAgentId = agentId,
            riskLevel = risk,
            reason = reason
        )
        _requests.value = _requests.value + (request.id to request)
        return request.id
    }

    @Synchronized
    fun provideDecision(requestId: String, decision: Boolean) {
        val request = _requests.value[requestId] ?: return
        _requests.value = _requests.value + (requestId to request.copy(userDecision = decision))
    }

    @Synchronized
    fun isApproved(requestId: String): Boolean {
        return _requests.value[requestId]?.userDecision == true
    }

    @Synchronized
    fun resetForTest() { _requests.value = emptyMap() }
}
