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
 * Converts a broad user goal into a bounded two-executive workflow.
 * The engine is intentionally goal-first: it expects SHOSHI/LUNA to discover a workable path,
 * resolve missing detail from context/tools where possible, and only ask the user when a real
 * blocker or approval gate remains. External writes still remain behind ToolGateway/HumanApproval.
 */
object AutonomousMissionEngine {
    fun submitGoal(goal: String, requestedOwner: ExecutiveAgent): AutonomousMissionReceipt {
        val cleanGoal = goal.trim()
        require(cleanGoal.isNotEmpty()) { "Goal is required" }
        check(!EmpireMissionControl.state.value.emergencyHalt) { "Emergency halt is active" }

        val mission = EmpireMissionControl.createMission(cleanGoal, requestedOwner.id)
        val counterpart = if (requestedOwner == ExecutiveAgent.SHOSHI) ExecutiveAgent.LUNA else ExecutiveAgent.SHOSHI
        val plan = listOf(
            MissionWorkItem(1, requestedOwner, "לנסח את התוצאה הרצויה ומדד הצלחה סביר מתוך המטרה וההקשר הקיים; לא לבקש פרטים שניתן להסיק בבטחה"),
            MissionWorkItem(2, counterpart, "לחקור עצמאית דרכי ביצוע אפשריות באמצעות ידע, זיכרון וכלים זמינים; לזהות חלופות, תלויות וחסמים אמיתיים"),
            MissionWorkItem(3, requestedOwner, "לבחור את המסלול החזק והמעשי ביותר, לציין הנחות עבודה ולבנות תוכנית Goal-to-Plan אופרטיבית"),
            MissionWorkItem(4, requestedOwner, "להתקדם אוטומטית בכל צעד אנליטי/תכנוני/הפיך שאינו דורש אישור, ולייצר תוצר ראשון שימושי במקום להמתין למפרט מושלם"),
            MissionWorkItem(5, counterpart, "לבצע ביקורת נגדית: לחפש דרך טובה יותר, קיצור דרך חוקי, סיכון שלא נלקח בחשבון או הזדמנות לשיפור"),
            MissionWorkItem(6, requestedOwner, "לעצור רק בחסם אמיתי: מידע שאי אפשר להסיק או לאמת, או פעולה חיצונית/כספית/בלתי-הפיכה שמחייבת Approval", requiresApproval = true),
            MissionWorkItem(7, counterpart, "לאמת תוצאה וראיות, להציע iteration הבא ולהמשיך קדימה אם אין חסם מהותי")
        )
        EmpireMissionControl.attachPlan(mission.id, plan.map { "${it.order}|${it.owner.id}|${it.requiresApproval}|${it.description}" })
        EmpireMissionControl.transitionStatus(mission.id, MissionStatus.PLANNED, "Goal-to-plan autonomous workflow created")
        EmpireMissionControl.transitionStatus(mission.id, MissionStatus.ASSIGNED, "Goal decomposition assigned to SHOSHI and LUNA")
        EmpireMissionControl.transitionStatus(mission.id, MissionStatus.RUNNING, "Autonomous path discovery started")
        EmpireMissionControl.updatePlanStep(mission.id, 1, "COMPLETED", "Goal interpreted from available context")
        EmpireMissionControl.updatePlanStep(mission.id, 2, "RUNNING", "Independent path discovery and option search started")
        CollaborationEngine.startConsultation(
            missionId = mission.id,
            initiatorId = requestedOwner.id,
            targetId = counterpart.id,
            topic = "Find the strongest safe path to achieve this broad goal without waiting for unnecessary user detail: $cleanGoal"
        )
        return AutonomousMissionReceipt(
            mission = EmpireMissionControl.state.value.missions.first { it.id == mission.id },
            plan = plan
        )
    }
}
