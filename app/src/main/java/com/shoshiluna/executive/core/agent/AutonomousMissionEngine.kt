package com.shoshiluna.executive.core.agent

import com.shoshiluna.executive.core.empire.EmpireMission
import com.shoshiluna.executive.core.empire.EmpireMissionControl
import com.shoshiluna.executive.core.empire.MissionStatus

data class MissionWorkItem(
    val order: Int,
    val owner: ExecutiveAgent,
    val description: String,
    val requiresApproval: Boolean = false
)

data class AutonomousMissionReceipt(
    val mission: EmpireMission,
    val plan: List<MissionWorkItem>
)

/**
 * Goal-first adaptive workflow. SHOSHI/LUNA discover a path, research options,
 * select tools/providers, produce a deliverable, critique it, recover from
 * reversible failures, verify evidence, and iterate until the mission can be
 * honestly declared complete. External/financial/destructive actions remain
 * behind ToolGateway and HumanApproval.
 */
object AutonomousMissionEngine {
    fun submitGoal(goal: String, requestedOwner: ExecutiveAgent): AutonomousMissionReceipt {
        val cleanGoal = goal.trim()
        require(cleanGoal.isNotEmpty()) { "Goal is required" }
        check(!EmpireMissionControl.state.value.emergencyHalt) { "Emergency halt is active" }

        val mission = EmpireMissionControl.createMission(cleanGoal, requestedOwner.id)
        val counterpart = if (requestedOwner == ExecutiveAgent.SHOSHI) ExecutiveAgent.LUNA else ExecutiveAgent.SHOSHI
        val plan = listOf(
            MissionWorkItem(1, requestedOwner, "GOAL: infer desired outcome, constraints and measurable success from context; ask only for irreducible missing input"),
            MissionWorkItem(2, counterpart, "RESEARCH LOOP: use permitted memory, browser/research, GitHub, agents and available tools to discover facts, options and dependencies before saying the task cannot be done"),
            MissionWorkItem(3, requestedOwner, "DYNAMIC PLAN: generate task-specific steps and alternatives rather than relying on a fixed recipe; rank paths by expected quality, risk, time and cost"),
            MissionWorkItem(4, counterpart, "TOOL DISCOVERY/ROUTING: identify the best available model, tool, API, library or provider for each subtask; propose a connection when a required capability is genuinely absent"),
            MissionWorkItem(5, requestedOwner, "DELIVERABLE MODE: produce the actual useful artifact/result whenever technically available, not merely instructions describing how it could be produced"),
            MissionWorkItem(6, counterpart, "CRITIC: attack the draft for factual gaps, weak assumptions, missed opportunities, security issues and better approaches; demand revision when material defects remain"),
            MissionWorkItem(7, requestedOwner, "RECOVERY: on reversible failure retry intelligently, then switch tool/provider or re-plan before escalating to the user"),
            MissionWorkItem(8, requestedOwner, "PERSISTENT GOAL: preserve current objective, attempted approaches, failures, evidence, open blockers and next action in mission state/memory"),
            MissionWorkItem(9, counterpart, "SELF-EVALUATION: score goal progress, deliverable quality, evidence, autonomy and verification; never declare done merely because steps ran"),
            MissionWorkItem(10, requestedOwner, "LEARNING: record outcome-based reusable lessons about successful/failed strategies without self-modifying security, approval or financial policy"),
            MissionWorkItem(11, counterpart, "APPROVAL GATE: stop only for irreducible user input or external, financial, credential-sensitive, destructive or irreversible action requiring approval", requiresApproval = true),
            MissionWorkItem(12, requestedOwner, "ITERATE: if Mission Completion Score is below verified-complete threshold, choose the highest-value correction and continue")
        )
        EmpireMissionControl.attachPlan(mission.id, plan.map { "${it.order}|${it.owner.id}|${it.requiresApproval}|${it.description}" })
        EmpireMissionControl.transitionStatus(mission.id, MissionStatus.PLANNED, "Adaptive goal-to-deliverable workflow created")
        EmpireMissionControl.transitionStatus(mission.id, MissionStatus.ASSIGNED, "Research, builder and critic roles assigned")
        EmpireMissionControl.transitionStatus(mission.id, MissionStatus.RUNNING, "Autonomous mission intelligence loop started")
        EmpireMissionControl.updatePlanStep(mission.id, 1, "COMPLETED", "Goal interpreted from available context")
        EmpireMissionControl.updatePlanStep(mission.id, 2, "RUNNING", "Research and path discovery started")
        CollaborationEngine.startConsultation(
            missionId = mission.id,
            initiatorId = requestedOwner.id,
            targetId = counterpart.id,
            topic = "Discover, build, critique and verify the strongest safe path to this goal. Do not wait for unnecessary user detail: $cleanGoal"
        )
        return AutonomousMissionReceipt(
            mission = EmpireMissionControl.state.value.missions.first { it.id == mission.id },
            plan = plan
        )
    }
}
