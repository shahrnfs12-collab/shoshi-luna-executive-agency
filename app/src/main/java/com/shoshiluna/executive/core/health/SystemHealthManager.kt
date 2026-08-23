package com.shoshiluna.executive.core.health

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

enum class EngineStatus {
    IDLE, INITIALIZING, RUNNING, ONLINE, WARNING, ERROR, DEGRADED, OFFLINE, UNKNOWN
}

data class EngineHealth(
    val status: EngineStatus,
    val lastUpdate: Long = System.currentTimeMillis(),
    val consecutiveFailures: Int = 0,
    val lastErrorMessage: String? = null,
    val metadata: Map<String, String> = emptyMap()
)

data class AgencyDiagnostics(
    val missionsCreated: Int = 0,
    val missionsCompleted: Int = 0,
    val missionsFailed: Int = 0,
    val activeAgents: Int = 0,
    val lastError: String? = null,
    val recentActivity: List<String> = emptyList()
)

object SystemHealthManager {
    private val _engineHealths = MutableStateFlow<Map<String, EngineHealth>>(mapOf(
        "AI_PROVIDER" to EngineHealth(EngineStatus.UNKNOWN, metadata = mapOf("Provider" to "OpenAI")),
        "DATABASE" to EngineHealth(EngineStatus.INITIALIZING),
        "MEMORY" to EngineHealth(EngineStatus.ONLINE),
        "MISSION_ENGINE" to EngineHealth(EngineStatus.ONLINE)
    ))
    val engineHealths: StateFlow<Map<String, EngineHealth>> = _engineHealths.asStateFlow()

    private val _diagnostics = MutableStateFlow(AgencyDiagnostics())
    val diagnostics: StateFlow<AgencyDiagnostics> = _diagnostics.asStateFlow()

    private val healthMap = ConcurrentHashMap<String, EngineHealth>(_engineHealths.value)

    fun updateHealth(engineName: String, status: EngineStatus, errorMessage: String? = null, metadata: Map<String, String> = emptyMap()) {
        val current = healthMap[engineName]
        val newFailures = if (status == EngineStatus.ERROR) (current?.consecutiveFailures ?: 0) + 1 else 0
        
        val newHealth = EngineHealth(
            status = status,
            lastUpdate = System.currentTimeMillis(),
            consecutiveFailures = newFailures,
            lastErrorMessage = errorMessage,
            metadata = current?.metadata?.plus(metadata) ?: metadata
        )
        healthMap[engineName] = newHealth
        _engineHealths.value = healthMap.toMap()
        
        if (status == EngineStatus.ERROR) {
            addActivity("SYSTEM ERROR [$engineName]: $errorMessage")
        }
    }

    fun addActivity(message: String) {
        val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())
        _diagnostics.value = _diagnostics.value.copy(
            recentActivity = (listOf("[$time] $message") + _diagnostics.value.recentActivity).take(50)
        )
    }

    fun getHealth(engineName: String): EngineHealth = healthMap[engineName] ?: EngineHealth(EngineStatus.IDLE)
}
