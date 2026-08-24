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
 * Converts an explicit user goal into a bounded two-executive workflow.
 * External writes are deliberately excluded from the automatic portion; those
 * remain behind ToolGateway and HumanApprovalGateway.
 */
object AutonomousMissionEngine {
    fun submitGoal(goal: String, requestedOwner: ExecutiveAgent): AutonomousMissionReceipt {
        val cleanGoal = goal.trim()
        require(cleanGoal.isNotEmpty()) { "Goal is required" }
        check(!EmpireMissionControl.state.value.emergencyHalt) { "Emergency halt is active" }

        val mission = EmpireMissionControl.createMission(cleanGoal, requestedOwner.id)
        val counterpart = if (requestedOwner == ExecutiveAgent.SHOSHI) ExecutiveAgent.LUNA else ExecutiveAgent.SHOSHI
        val plan = listOf(
            MissionWorkItem(1, requestedOwner, "הגדרת התוצאה, המגבלות ומדדי ההצלחה"),
            MissionWorkItem(2, counterpart, "בדיקת עובדות, סיכונים, חלופות ותלויות"),
            MissionWorkItem(3, requestedOwner, "בניית תוצר ותוכנית ביצוע מבוססת ראיות"),
            MissionWorkItem(4, counterpart, "בקרת איכות והצגת פעולות רגישות לאישור", requiresApproval = true)
        )
        EmpireMissionControl.attachPlan(mission.id, plan.map { "${it.order}|${it.owner.id}|${it.requiresApproval}|${it.description}" })
        EmpireMissionControl.transitionStatus(mission.id, MissionStatus.PLANNED, "Autonomous work plan created")
        EmpireMissionControl.transitionStatus(mission.id, MissionStatus.ASSIGNED, "Work divided between SHOSHI and LUNA")
        EmpireMissionControl.transitionStatus(mission.id, MissionStatus.RUNNING, "Bounded autonomous analysis started")
        EmpireMissionControl.updatePlanStep(mission.id, 1, "COMPLETED", "Goal and success criteria captured")
        EmpireMissionControl.updatePlanStep(mission.id, 2, "RUNNING", "Counterpart review started")
        CollaborationEngine.startConsultation(
            missionId = mission.id,
            initiatorId = requestedOwner.id,
            targetId = counterpart.id,
            topic = cleanGoal
        )
        return AutonomousMissionReceipt(
            mission = EmpireMissionControl.state.value.missions.first { it.id == mission.id },
            plan = plan
        )
    }
}
