package com.shoshiluna.executive.core.dream

import com.shoshiluna.executive.core.agent.CouncilSession
import com.shoshiluna.executive.core.business.EvidenceItem
import com.shoshiluna.executive.core.empire.EmpireMission
import com.shoshiluna.executive.core.empire.MissionStatus
import com.shoshiluna.executive.core.security.ApprovalRequest

data class ExecutiveBrief(
    val headline: String,
    val operationalState: String,
    val priorities: List<String>,
    val risks: List<String>,
    val nextBestAction: String
)

object ExecutiveBriefEngine {
    private val terminal = setOf(
        MissionStatus.COMPLETED, MissionStatus.FAILED, MissionStatus.REJECTED,
        MissionStatus.CANCELLED, MissionStatus.HALTED
    )
    private val waiting = setOf(
        MissionStatus.WAITING, MissionStatus.AWAITING_APPROVAL, MissionStatus.WAITING_FOR_USER,
        MissionStatus.WAITING_FOR_APPROVAL, MissionStatus.WAITING_FOR_EXTERNAL_RESPONSE,
        MissionStatus.WAITING_FOR_DATA, MissionStatus.WAITING_FOR_DEPENDENCY, MissionStatus.PAUSED
    )

    fun build(
        missions: List<EmpireMission>,
        approvals: Collection<ApprovalRequest>,
        councilSessions: Collection<CouncilSession>,
        evidence: Collection<EvidenceItem>,
        emergencyHalt: Boolean
    ): ExecutiveBrief {
        val active = missions.filter { it.status !in terminal }
        val blocked = active.filter { it.status in waiting }
        val pendingApprovals = approvals.filter { it.userDecision == null }
        val liveCouncil = councilSessions.filter { it.isActive }
        val verifiedEvidence = evidence.count { it.category.name == "VERIFIED_RESULT" }

        val risks = buildList {
            if (emergencyHalt) add("Emergency Halt is active; execution is frozen.")
            if (pendingApprovals.isNotEmpty()) add("${pendingApprovals.size} sensitive action(s) await explicit approval.")
            if (blocked.isNotEmpty()) add("${blocked.size} active mission(s) are waiting on user/data/external dependency.")
            if (active.isNotEmpty() && verifiedEvidence == 0) add("Active work has no VERIFIED_RESULT evidence yet.")
        }
        val priorities = active
            .sortedWith(compareBy<EmpireMission> { it.status !in waiting }.thenBy { it.createdAt })
            .take(3)
            .map { "${it.title} • ${it.status}" }

        val next = when {
            emergencyHalt -> "Resolve the incident, review audit evidence, then explicitly release the halt."
            pendingApprovals.isNotEmpty() -> "Review the highest-risk pending approval before allowing further external/sensitive execution."
            blocked.isNotEmpty() -> "Unblock the oldest waiting mission by satisfying its required condition."
            liveCouncil.isNotEmpty() -> "Resolve the active Council disagreement and record the decision rationale."
            active.isNotEmpty() -> "Advance the highest-priority mission while preserving evidence and approval gates."
            else -> "Create a concrete mission in Chat or Mission Control and assign SHOSHI or LUNA as owner."
        }

        val headline = when {
            emergencyHalt -> "Agency halted — protect state before resuming."
            active.isEmpty() -> "Command Center ready for the next mission."
            blocked.isNotEmpty() -> "Agency active with ${blocked.size} blocked dependency point(s)."
            else -> "Agency executing ${active.size} active mission(s)."
        }

        return ExecutiveBrief(
            headline = headline,
            operationalState = if (emergencyHalt) "HALTED" else "OPERATIONAL",
            priorities = priorities,
            risks = risks,
            nextBestAction = next
        )
    }
}
