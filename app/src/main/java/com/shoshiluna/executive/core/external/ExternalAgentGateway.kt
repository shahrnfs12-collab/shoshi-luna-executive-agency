package com.shoshiluna.executive.core.external

import com.shoshiluna.executive.core.agent.AgentCapability
import com.shoshiluna.executive.core.agent.PermissionRequest
import com.shoshiluna.executive.core.audit.AuditTrail
import com.shoshiluna.executive.core.audit.ExecutiveAudit
import com.shoshiluna.executive.core.empire.EmpireMissionControl
import com.shoshiluna.executive.core.security.ApprovalRiskLevel
import com.shoshiluna.executive.core.security.HumanApprovalGateway
import com.shoshiluna.executive.core.security.PermissionKernel
import java.util.concurrent.ConcurrentHashMap

object ExternalAgentGateway {
    private val adapters = ConcurrentHashMap<ExternalProtocol, ExternalAgentAdapter>()

    fun registerAdapter(adapter: ExternalAgentAdapter) { adapters[adapter.protocol] = adapter }
    fun unregisterAdapter(protocol: ExternalProtocol) { adapters.remove(protocol) }

    suspend fun handshake(agentId: String): Result<ExternalHandshakeResult> {
        val agent = TrustedExternalAgentRegistry.get(agentId) ?: return Result.failure(IllegalArgumentException("Unknown external agent"))
        if (agent.trustStatus == ExternalTrustStatus.REVOKED) return Result.failure(SecurityException("External agent is revoked"))
        val adapter = adapters[agent.protocol] ?: return Result.failure(IllegalStateException("Adapter NOT_CONFIGURED for ${agent.protocol}"))
        return adapter.handshake(agent).mapCatching { result ->
            require(result.accepted) { "External agent rejected handshake" }
            result
        }
    }

    suspend fun send(message: ExternalAgentMessage): ExternalAgentResponse {
        ExternalOperationJournal.get(message.idempotencyKey)?.let { record ->
            return when (record.state) {
                ExternalOperationJournal.State.VERIFIED -> ExternalAgentResponse(message.id, record.externalAgentId, ExternalMessageStatus.VERIFIED, body = record.safeBody, evidenceId = record.evidenceId)
                ExternalOperationJournal.State.QUARANTINED -> ExternalAgentResponse(message.id, record.externalAgentId, ExternalMessageStatus.QUARANTINED, evidenceId = record.evidenceId, error = "Previously quarantined")
                ExternalOperationJournal.State.EXECUTION_PENDING -> failed(message, "Uncertain crash-window state. Automatic resend blocked; reconciliation/user review required")
                ExternalOperationJournal.State.FAILED -> failed(message, "Previous external operation failed; create a new reviewed contact proposal to retry")
            }
        }
        if (EmpireMissionControl.state.value.emergencyHalt) return failed(message, "Emergency Halt Active")
        require(message.body.isNotBlank()) { "External message body is required" }
        require(message.purpose.isNotBlank()) { "External message purpose is required" }
        val target = TrustedExternalAgentRegistry.get(message.targetExternalAgentId) ?: return failed(message, "Unknown external agent")
        if (target.trustStatus != ExternalTrustStatus.TRUSTED) return failed(message, "External agent is not TRUSTED")
        EndpointPolicy.validate(target.endpoint).getOrElse { return failed(message, "Endpoint rejected: ${it.message}") }

        val permission = PermissionKernel.authorize(
            PermissionRequest(
                agentId = message.senderAgentId,
                capability = AgentCapability.COMMUNICATE_EXTERNAL,
                missionId = message.missionId,
                targetResourceId = target.id
            )
        )
        if (!permission.allowed) return failed(message, "Permission denied: ${permission.reason}")

        val approvalId = message.approvalRequestId
        if (approvalId == null) {
            val created = HumanApprovalGateway.requestApproval(
                action = "Contact external agent ${target.displayName}",
                agentId = message.senderAgentId,
                risk = ApprovalRiskLevel.HIGH,
                reason = "Mission ${message.missionId}: ${message.purpose}"
            )
            return ExternalAgentResponse(message.id, target.id, ExternalMessageStatus.APPROVAL_REQUIRED, body = created, error = "Explicit user approval required")
        }
        if (!HumanApprovalGateway.isApproved(approvalId)) {
            return ExternalAgentResponse(message.id, target.id, ExternalMessageStatus.APPROVAL_REQUIRED, body = approvalId, error = "Approval not granted")
        }

        val adapter = adapters[target.protocol] ?: return failed(message, "Adapter NOT_CONFIGURED for ${target.protocol}")
        ExternalOperationJournal.put(ExternalOperationJournal.Record(message.idempotencyKey, target.id, message.missionId, ExternalOperationJournal.State.EXECUTION_PENDING))
        val rawResult = adapter.send(target, message)
        if (rawResult.isFailure) {
            ExternalOperationJournal.put(ExternalOperationJournal.Record(message.idempotencyKey, target.id, message.missionId, ExternalOperationJournal.State.FAILED))
            return failed(message, "Connector failure: ${rawResult.exceptionOrNull()?.javaClass?.simpleName}: ${rawResult.exceptionOrNull()?.message}")
        }
        val raw = rawResult.getOrThrow()
        val inbound = ExternalInboundGateway.quarantineAndInspect(target.id, message.missionId, raw)
        val response = if (inbound.accepted) {
            ExternalAgentResponse(message.id, target.id, ExternalMessageStatus.VERIFIED, body = inbound.safeContent, evidenceId = inbound.evidenceId)
        } else {
            ExternalAgentResponse(message.id, target.id, ExternalMessageStatus.QUARANTINED, evidenceId = inbound.evidenceId, error = inbound.reason)
        }
        AuditTrail.append(ExecutiveAudit(agentId = message.senderAgentId, missionId = message.missionId, action = "EXTERNAL_AGENT_CONTACT:${target.id}", result = response.status.name, permissionDecisionId = permission.auditId))
        val journalState = if (response.status == ExternalMessageStatus.VERIFIED) ExternalOperationJournal.State.VERIFIED else ExternalOperationJournal.State.QUARANTINED
        ExternalOperationJournal.put(ExternalOperationJournal.Record(message.idempotencyKey, target.id, message.missionId, journalState, response.evidenceId, response.body))
        return response
    }

    private fun failed(message: ExternalAgentMessage, reason: String) = ExternalAgentResponse(
        messageId = message.id,
        externalAgentId = message.targetExternalAgentId,
        status = ExternalMessageStatus.FAILED,
        error = reason
    )

    fun resetForTest() { adapters.clear() }
}
