package com.shoshiluna.executive.core.agent

import com.shoshiluna.executive.core.business.*
import com.shoshiluna.executive.core.empire.EmpireMissionControl
import com.shoshiluna.executive.core.empire.MissionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * ShoshiLuna Agency Orchestrator.
 * Final integrated authority for coordinating SHOSHI and LUNA across the business pipeline.
 */
object AgencyOrchestrator {
    
    private val _pipelineStates = MutableStateFlow<Map<String, PipelineState>>(emptyMap()) // missionId to state
    val pipelineStates = _pipelineStates.asStateFlow()

    @Synchronized
    fun startPipeline(missionId: String): PipelineState? {
        check(!EmpireMissionControl.state.value.emergencyHalt) { "Emergency halt is active; pipeline start is blocked" }
        val mission = EmpireMissionControl.state.value.missions.find { it.id == missionId } ?: return null
        if (mission.status in setOf(MissionStatus.COMPLETED, MissionStatus.FAILED, MissionStatus.CANCELLED, MissionStatus.REJECTED, MissionStatus.HALTED)) return null
        val initialState = PipelineState(missionId = missionId)
        EmpireMissionControl.transitionStatus(missionId, MissionStatus.RUNNING, "Pipeline started")
        _pipelineStates.value = _pipelineStates.value + (missionId to initialState)
        return initialState
    }

    @Synchronized
    fun addTask(missionId: String, task: PipelineTask) {
        check(!EmpireMissionControl.state.value.emergencyHalt) { "Emergency halt is active; task mutation is blocked" }
        val currentState = _pipelineStates.value[missionId] ?: return
        check(!currentState.isHalted) { "Pipeline is halted" }
        require(task.stage == currentState.currentStage) { "Task stage ${task.stage} does not match current stage ${currentState.currentStage}" }
        require(task.dependencies.all { dep -> currentState.tasks.any { it.id == dep && it.status == PipelineStatus.COMPLETED } }) { "Task dependencies are not completed" }
        val updatedTasks = currentState.tasks + task
        _pipelineStates.value = _pipelineStates.value + (missionId to currentState.copy(tasks = updatedTasks))
    }

    @Synchronized
    fun updateTaskStatus(missionId: String, taskId: String, status: PipelineStatus, output: Map<String, Any>? = null) {
        check(!EmpireMissionControl.state.value.emergencyHalt) { "Emergency halt is active; task transition is blocked" }
        val currentState = _pipelineStates.value[missionId] ?: return
        check(!currentState.isHalted) { "Pipeline is halted" }
        require(currentState.tasks.any { it.id == taskId }) { "Unknown taskId: $taskId" }
        val updatedTasks = currentState.tasks.map {
            if (it.id == taskId) it.copy(status = status, outputData = output) else it
        }
        
        val currentStageTasks = updatedTasks.filter { it.stage == currentState.currentStage }
        val stageComplete = currentStageTasks.isNotEmpty() && currentStageTasks.all { it.status == PipelineStatus.COMPLETED || it.status == PipelineStatus.SKIPPED }
        val nextStage = determineNextStage(currentState.currentStage, if (stageComplete) PipelineStatus.COMPLETED else status.takeIf { it != PipelineStatus.COMPLETED } ?: PipelineStatus.IN_PROGRESS)
        
        _pipelineStates.value = _pipelineStates.value + (missionId to currentState.copy(
            tasks = updatedTasks,
            currentStage = nextStage ?: currentState.currentStage
        ))

        val finalStageCompleted = currentState.currentStage == PipelineStage.TREASURY_ALLOCATION &&
            status == PipelineStatus.COMPLETED && updatedTasks.filter { it.stage == PipelineStage.TREASURY_ALLOCATION }.all { it.status == PipelineStatus.COMPLETED }
        if (finalStageCompleted) {
            EmpireMissionControl.transitionStatus(missionId, MissionStatus.REVIEW, "Final pipeline stage completed; human/evidence review required before mission completion")
        }
    }

    private fun determineNextStage(current: PipelineStage, lastTaskStatus: PipelineStatus): PipelineStage? {
        if (lastTaskStatus != PipelineStatus.COMPLETED) return null
        
        val stages = PipelineStage.entries
        val currentIndex = stages.indexOf(current)
        return if (currentIndex < stages.size - 1) stages[currentIndex + 1] else null
    }

    fun haltAll() {
        _pipelineStates.value = _pipelineStates.value.mapValues { it.value.copy(isHalted = true) }
    }

    fun resumeAll() {
        _pipelineStates.value = _pipelineStates.value.mapValues { it.value.copy(isHalted = false) }
    }
}
