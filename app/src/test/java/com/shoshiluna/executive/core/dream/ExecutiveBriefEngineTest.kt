package com.shoshiluna.executive.core.dream

import com.shoshiluna.executive.core.agent.CouncilSession
import com.shoshiluna.executive.core.business.EvidenceCategory
import com.shoshiluna.executive.core.business.EvidenceItem
import com.shoshiluna.executive.core.empire.EmpireMission
import com.shoshiluna.executive.core.empire.MissionStatus
import com.shoshiluna.executive.core.security.ApprovalRequest
import com.shoshiluna.executive.core.security.ApprovalRiskLevel
import org.junit.Assert.assertTrue
import org.junit.Test

class ExecutiveBriefEngineTest {
    @Test
    fun haltDominatesNextAction() {
        val brief = ExecutiveBriefEngine.build(
            missions = listOf(EmpireMission(title = "Mission", ownerId = "A", status = MissionStatus.RUNNING)),
            approvals = emptyList(),
            councilSessions = emptyList(),
            evidence = emptyList(),
            emergencyHalt = true
        )
        assertTrue(brief.operationalState == "HALTED")
        assertTrue(brief.nextBestAction.contains("incident", ignoreCase = true))
    }

    @Test
    fun pendingApprovalBecomesPriority() {
        val approval = ApprovalRequest(
            requestedAction = "External contact",
            requestingAgentId = "A",
            riskLevel = ApprovalRiskLevel.HIGH,
            reason = "test"
        )
        val brief = ExecutiveBriefEngine.build(
            missions = listOf(EmpireMission(title = "Mission", ownerId = "A", status = MissionStatus.RUNNING)),
            approvals = listOf(approval),
            councilSessions = emptyList(),
            evidence = listOf(EvidenceItem(category = EvidenceCategory.VERIFIED_RESULT, content = "ok", source = "test")),
            emergencyHalt = false
        )
        assertTrue(brief.nextBestAction.contains("approval", ignoreCase = true))
    }
}
