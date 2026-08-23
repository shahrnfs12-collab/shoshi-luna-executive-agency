package com.shoshiluna.executive.core.engine

import com.shoshiluna.executive.core.audit.AuditTrail
import com.shoshiluna.executive.core.audit.ExecutiveAudit
import com.shoshiluna.executive.core.empire.EmpireMissionControl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

enum class AnomalyType {
    MARKET_COLLAPSE,
    CONNECTOR_COMPROMISE,
    FINANCIAL_ANOMALY,
    STRATEGY_COLLAPSE,
    DATA_DEGRADATION
}

data class BlackSwanEvent(
    val id: String = UUID.randomUUID().toString(),
    val type: AnomalyType,
    val severity: Float,
    val evidence: String,
    val timestamp: Long = System.currentTimeMillis()
)

/** Fail-closed anomaly response. Severity is normalized to [0, 1]. */
object BlackSwanEngine {
    private const val HARD_HALT_THRESHOLD = 0.80f

    private val _activeEvents = MutableStateFlow<List<BlackSwanEvent>>(emptyList())
    val activeEvents = _activeEvents.asStateFlow()

    @Synchronized
    fun reportAnomaly(event: BlackSwanEvent) {
        require(event.severity in 0f..1f) { "severity must be between 0.0 and 1.0" }
        require(event.evidence.isNotBlank()) { "Black-swan evidence is required" }

        _activeEvents.value = _activeEvents.value + event
        if (event.severity >= HARD_HALT_THRESHOLD) {
            EmpireMissionControl.emergencyHalt()
        }

        AuditTrail.append(
            ExecutiveAudit(
                agentId = "SYSTEM",
                action = "BLACK_SWAN_DETECTED",
                result = "TYPE=${event.type}; SEVERITY=${event.severity}; HALT=${event.severity >= HARD_HALT_THRESHOLD}",
                permissionDecisionId = event.id
            )
        )
    }

    @Synchronized
    fun clearEvents() {
        _activeEvents.value = emptyList()
        AuditTrail.append(ExecutiveAudit(agentId = "SYSTEM", action = "BLACK_SWAN_EVENTS", result = "CLEARED"))
    }
}
