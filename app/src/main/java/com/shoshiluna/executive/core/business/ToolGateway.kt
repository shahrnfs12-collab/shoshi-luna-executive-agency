package com.shoshiluna.executive.core.business

import com.shoshiluna.executive.core.agent.AgentCapability
import com.shoshiluna.executive.core.agent.PermissionRequest
import com.shoshiluna.executive.core.audit.AuditTrail
import com.shoshiluna.executive.core.audit.ExecutiveAudit
import com.shoshiluna.executive.core.empire.EmpireMissionControl
import com.shoshiluna.executive.core.security.HumanApprovalGateway
import com.shoshiluna.executive.core.security.PermissionKernel
import com.shoshiluna.executive.core.security.ApprovalRiskLevel
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.withTimeout

/** Real connector boundary. No simulated-success path exists here. */
object ToolGateway {
    fun interface ToolAdapter { suspend fun execute(input: Map<String, String>): String }

    private val toolRegistry = ConcurrentHashMap<String, ToolDefinition>()
    private val adapters = ConcurrentHashMap<String, ToolAdapter>()
    private val resultCache = ConcurrentHashMap<String, ToolResult>()

    fun registerTool(definition: ToolDefinition, adapter: ToolAdapter? = null) {
        toolRegistry[definition.id] = definition
        if (adapter != null) adapters[definition.id] = adapter
    }

    fun unregisterTool(toolId: String) {
        toolRegistry.remove(toolId); adapters.remove(toolId)
    }

    fun registeredTools(): List<ToolDefinition> = toolRegistry.values.sortedBy { it.name }
    fun hasRuntimeAdapter(toolId: String): Boolean = adapters.containsKey(toolId)

    /** Clears all process-local registrations and cached results between isolated test runs. */
    fun resetForTest() {
        toolRegistry.clear()
        adapters.clear()
        resultCache.clear()
    }

    suspend fun executeTool(request: ToolRequest): ToolResult {
        resultCache[request.idempotencyKey]?.let { return it.copy(requestId = request.id) }
        if (EmpireMissionControl.state.value.emergencyHalt) return failed(request, "Emergency Halt Active")
        val tool = toolRegistry[request.toolId] ?: return failed(request, "Unknown Tool")
        val requiredCapability = if (tool.riskClass == ToolRiskClass.EXTERNAL_COMMUNICATION) AgentCapability.COMMUNICATE_EXTERNAL else AgentCapability.EXECUTE_TOOL
        val permission = PermissionKernel.authorize(PermissionRequest(agentId = request.agentId, capability = requiredCapability, missionId = request.missionId, targetResourceId = request.toolId))
        audit(request, tool, if (permission.allowed) "AUTHORIZED" else "DENIED: ${permission.reason}", permission.auditId)
        if (!permission.allowed) return failed(request, "Permission Denied: ${permission.reason}")

        if (tool.requiresUserApproval) {
            val approvalId = request.approvalRequestId
            if (approvalId == null) {
                val created = HumanApprovalGateway.requestApproval(
                    action = "Execute tool ${tool.name}", agentId = request.agentId,
                    risk = riskLevel(tool.riskClass), reason = "External tool execution for mission ${request.missionId}"
                )
                return ToolResult(request.id, ToolExecutionStatus.APPROVAL_REQUIRED, outputData = created, errorMessage = "Explicit user approval required")
            }
            if (!HumanApprovalGateway.isApproved(approvalId)) return ToolResult(request.id, ToolExecutionStatus.APPROVAL_REQUIRED, outputData = approvalId, errorMessage = "Approval not granted")
        }

        val adapter = adapters[tool.id] ?: return failed(request, "Tool adapter NOT CONFIGURED; execution not attempted")
        require(request.idempotencyKey.isNotBlank()) { "idempotencyKey is required" }
        // Never redact/modify the payload before connector execution. Redaction is for logs only.
        val result = runCatching { withTimeout(tool.timeoutMs) { adapter.execute(request.inputData) } }
            .fold(
                onSuccess = { output ->
                    val evidenceId = EvidenceRegistry.record(EvidenceCategory.VERIFIED_RESULT, "Tool ${tool.id} completed", "ToolGateway:${tool.id}", missionId = request.missionId, correlationId = request.idempotencyKey)
                    ToolResult(request.id, ToolExecutionStatus.VERIFIED, outputData = output, evidenceId = evidenceId)
                },
                onFailure = { failed(request, "Connector error: ${it.javaClass.simpleName}: ${it.message ?: "unknown"}") }
            )
        if (result.status == ToolExecutionStatus.VERIFIED) resultCache[request.idempotencyKey] = result
        return result
    }

    private fun failed(request: ToolRequest, reason: String) = ToolResult(request.id, ToolExecutionStatus.FAILED, errorMessage = reason)
    private fun audit(request: ToolRequest, tool: ToolDefinition, result: String, decisionId: String?) = AuditTrail.append(
        ExecutiveAudit(agentId = request.agentId, missionId = request.missionId, action = "TOOL_EXECUTION: ${tool.name}", result = result, permissionDecisionId = decisionId)
    )
    private fun riskLevel(risk: ToolRiskClass) = when (risk) {
        ToolRiskClass.READ_ONLY -> ApprovalRiskLevel.LOW
        ToolRiskClass.LOW_RISK_WRITE -> ApprovalRiskLevel.MEDIUM
        ToolRiskClass.EXTERNAL_COMMUNICATION, ToolRiskClass.CONSEQUENTIAL_WRITE -> ApprovalRiskLevel.HIGH
        ToolRiskClass.FINANCIAL, ToolRiskClass.SECURITY_SENSITIVE -> ApprovalRiskLevel.CRITICAL
    }
}
