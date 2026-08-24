package com.shoshiluna.executive.core.empire

import com.shoshiluna.executive.core.audit.AuditTrail
import com.shoshiluna.executive.core.audit.ExecutiveAudit
import com.shoshiluna.executive.core.business.Environment
import com.shoshiluna.executive.core.hardening.MissionTransitionPolicy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

enum class MissionStatus {
    CREATED, PLANNED, ASSIGNED, RUNNING, WAITING, REVIEW, COMPLETED, FAILED, HALTED,
    DISCOVERED, RESEARCHING, EVALUATING, AWAITING_APPROVAL, APPROVED, EXECUTING, VALIDATING, REJECTED, CANCELLED,
    WAITING_FOR_USER, WAITING_FOR_APPROVAL, WAITING_FOR_EXTERNAL_RESPONSE, WAITING_FOR_DATA, WAITING_FOR_DEPENDENCY, PAUSED
}

data class EmpireMission(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val ownerId: String,
    val status: MissionStatus = MissionStatus.CREATED,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val auditId: String = UUID.randomUUID().toString(),
    val environment: Environment = Environment.SIMULATION,
    val waitingSince: Long? = null,
    val requiredCondition: String? = null,
    val resumePolicy: String? = null,
    val correlationId: String? = null,
    val taskDependencies: List<String> = emptyList(),
    val workPlan: List<String> = emptyList()
)

data class EmpireState(
    val emergencyHalt: Boolean = false,
    val missions: List<EmpireMission> = emptyList(),
    val activeCollaborations: Map<String, String> = emptyMap()
)

/**
 * Central mission lifecycle controller.
 * State is in-memory in this baseline; persistence must be added before claiming crash-safe recovery.
 */
object EmpireMissionControl {
    private val _state = MutableStateFlow(EmpireState())
    val state: StateFlow<EmpireState> = _state.asStateFlow()
    @Volatile private var store: EmpireMissionStore? = null

    @Synchronized
    fun initialize(context: android.content.Context) {
        if (store != null) return
        store = EmpireMissionStore(context).also { _state.value = it.load() }
    }

    @Synchronized
    fun emergencyHalt() {
        _state.value = _state.value.copy(
            emergencyHalt = true,
            missions = _state.value.missions.map { mission ->
                if (mission.status in terminalStatuses) mission
                else mission.copy(status = MissionStatus.HALTED, updatedAt = System.currentTimeMillis())
            }
        )
        persist()
        audit("SYSTEM", null, "EMERGENCY_HALT", "ACTIVATED")
    }

    @Synchronized
    fun releaseHalt() {
        _state.value = _state.value.copy(emergencyHalt = false)
        persist()
        audit("SYSTEM", null, "EMERGENCY_HALT", "RELEASED")
    }

    @Synchronized
    fun createMission(title: String, ownerId: String, environment: Environment = Environment.SIMULATION): EmpireMission {
        check(!_state.value.emergencyHalt) { "Emergency halt is active; mission creation is blocked" }
        val cleanTitle = title.trim()
        val cleanOwner = ownerId.trim()
        require(cleanTitle.isNotEmpty()) { "Mission title is required" }
        require(cleanOwner.isNotEmpty()) { "Mission ownerId is required" }

        val mission = EmpireMission(title = cleanTitle, ownerId = cleanOwner, environment = environment)
        _state.value = _state.value.copy(missions = listOf(mission) + _state.value.missions)
        persist()
        audit(cleanOwner, mission.id, "MISSION_CREATED", "${mission.status}; environment=${mission.environment}")
        return mission
    }

    @Synchronized
    fun attachPlan(missionId: String, workPlan: List<String>) {
        require(workPlan.isNotEmpty()) { "Mission work plan is required" }
        var found = false
        _state.value = _state.value.copy(missions = _state.value.missions.map { mission ->
            if (mission.id == missionId) {
                found = true
                check(mission.status == MissionStatus.CREATED) { "Plan can only be attached to a created mission" }
                mission.copy(workPlan = workPlan.toList(), updatedAt = System.currentTimeMillis())
            } else mission
        })
        require(found) { "Unknown missionId: $missionId" }
        persist()
        audit("SYSTEM", missionId, "MISSION_PLAN_ATTACHED", "steps=${workPlan.size}")
    }

    @Synchronized
    fun transitionStatus(missionId: String, nextStatus: MissionStatus, reason: String) {
        require(missionId.isNotBlank()) { "missionId is required" }
        require(reason.isNotBlank()) { "Transition reason is required" }
        if (_state.value.emergencyHalt && nextStatus != MissionStatus.HALTED) {
            throw IllegalStateException("Emergency halt is active; state transition is blocked")
        }

        var found = false
        var owner = "SYSTEM"
        _state.value = _state.value.copy(
            missions = _state.value.missions.map { mission ->
                if (mission.id == missionId) {
                    found = true
                    owner = mission.ownerId
                    MissionTransitionPolicy.requireAllowed(mission.status, nextStatus)
                    mission.copy(
                        status = nextStatus,
                        updatedAt = System.currentTimeMillis(),
                        waitingSince = if (nextStatus in waitingStatuses) mission.waitingSince else null,
                        requiredCondition = if (nextStatus in waitingStatuses) mission.requiredCondition else null
                    )
                } else mission
            }
        )
        require(found) { "Unknown missionId: $missionId" }
        persist()
        audit(owner, missionId, "MISSION_STATUS", "$nextStatus; reason=${reason.trim()}")
    }

    @Synchronized
    fun enterWaitingState(missionId: String, status: MissionStatus, condition: String, correlationId: String? = null) {
        check(!_state.value.emergencyHalt) { "Emergency halt is active; waiting transition is blocked" }
        require(status in waitingStatuses) { "Status $status is not a waiting status" }
        require(condition.isNotBlank()) { "Waiting condition is required" }

        var found = false
        var owner = "SYSTEM"
        _state.value = _state.value.copy(
            missions = _state.value.missions.map { mission ->
                if (mission.id == missionId) {
                    found = true
                    owner = mission.ownerId
                    MissionTransitionPolicy.requireAllowed(mission.status, status)
                    mission.copy(
                        status = status,
                        waitingSince = System.currentTimeMillis(),
                        requiredCondition = condition.trim(),
                        correlationId = correlationId,
                        updatedAt = System.currentTimeMillis()
                    )
                } else mission
            }
        )
        require(found) { "Unknown missionId: $missionId" }
        persist()
        audit(owner, missionId, "MISSION_WAITING", "$status; condition=${condition.trim()}")
    }

    @Synchronized
    fun resetForTest() {
        _state.value = EmpireState()
    }

    private fun audit(agentId: String, missionId: String?, action: String, result: String) {
        AuditTrail.append(
            ExecutiveAudit(
                agentId = agentId,
                missionId = missionId,
                action = action,
                result = result
            )
        )
    }

    private fun persist() { store?.save(_state.value) }

    private val waitingStatuses = setOf(
        MissionStatus.WAITING,
        MissionStatus.AWAITING_APPROVAL,
        MissionStatus.WAITING_FOR_USER,
        MissionStatus.WAITING_FOR_APPROVAL,
        MissionStatus.WAITING_FOR_EXTERNAL_RESPONSE,
        MissionStatus.WAITING_FOR_DATA,
        MissionStatus.WAITING_FOR_DEPENDENCY,
        MissionStatus.PAUSED
    )

    private val terminalStatuses = setOf(
        MissionStatus.COMPLETED,
        MissionStatus.FAILED,
        MissionStatus.REJECTED,
        MissionStatus.CANCELLED,
        MissionStatus.HALTED
    )
}
