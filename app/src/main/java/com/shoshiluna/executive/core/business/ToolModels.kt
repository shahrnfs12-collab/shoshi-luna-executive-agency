package com.shoshiluna.executive.core.business

import java.util.UUID

enum class ToolRiskClass {
    READ_ONLY,
    LOW_RISK_WRITE,
    EXTERNAL_COMMUNICATION,
    CONSEQUENTIAL_WRITE,
    FINANCIAL,
    SECURITY_SENSITIVE
}

enum class ToolExecutionStatus {
    PREPARED,
    APPROVAL_REQUIRED,
    APPROVED,
    EXECUTION_PENDING,
    EXECUTED,
    VERIFIED,
    FAILED,
    CANCELLED
}

data class ToolDefinition(
    val id: String,
    val name: String,
    val riskClass: ToolRiskClass,
    val requiresUserApproval: Boolean = false,
    val timeoutMs: Long = 30000L,
    val retryPolicy: String = "NONE"
)

data class ToolRequest(
    val id: String = UUID.randomUUID().toString(),
    val toolId: String,
    val agentId: String,
    val missionId: String,
    val inputData: Map<String, String>,
    val idempotencyKey: String = UUID.randomUUID().toString(),
    val approvalRequestId: String? = null,
    val status: ToolExecutionStatus = ToolExecutionStatus.PREPARED
)

data class ToolResult(
    val requestId: String,
    val status: ToolExecutionStatus,
    val outputData: String? = null,
    val errorMessage: String? = null,
    val evidenceId: String? = null,
    val auditId: String = UUID.randomUUID().toString()
)
