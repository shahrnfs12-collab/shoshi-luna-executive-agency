package com.shoshiluna.executive.core.empire

import com.shoshiluna.executive.core.audit.AuditTrail
import com.shoshiluna.executive.core.audit.ExecutiveAudit
import java.util.UUID

/**
 * ShoshiLuna Resume Engine.
 * Handles idempotent mission resumption when waiting conditions are met.
 */
object ResumeEngine {

    fun attemptResume(missionId: String, conditionMet: String, actorId: String): Boolean {
        val mission = EmpireMissionControl.state.value.missions.find { it.id == missionId } ?: return false
        
        // Rule: Resume only if mission is in a WAITING state
        val isWaiting = mission.status in setOf(
            MissionStatus.WAITING_FOR_USER,
            MissionStatus.WAITING_FOR_APPROVAL,
            MissionStatus.WAITING_FOR_EXTERNAL_RESPONSE,
            MissionStatus.WAITING_FOR_DATA,
            MissionStatus.WAITING_FOR_DEPENDENCY
        )
        if (!isWaiting) return false

        // Rule: Verify required condition
        if (mission.requiredCondition != null && mission.requiredCondition != conditionMet) {
            return false
        }

        // Rule: Idempotency - already handled by state check above, but we log the transition
        val auditId = UUID.randomUUID().toString()
        EmpireMissionControl.transitionStatus(missionId, MissionStatus.RUNNING, "Resume condition met: $conditionMet")
        
        AuditTrail.append(
            ExecutiveAudit(
                agentId = actorId,
                missionId = missionId,
                action = "RESUME_MISSION",
                result = "SUCCESS: $conditionMet",
                permissionDecisionId = auditId
            )
        )

        return true
    }
}
