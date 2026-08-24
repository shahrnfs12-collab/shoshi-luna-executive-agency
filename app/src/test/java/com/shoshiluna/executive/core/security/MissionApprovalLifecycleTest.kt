package com.shoshiluna.executive.core.security

import com.shoshiluna.executive.core.empire.EmpireMissionControl
import com.shoshiluna.executive.core.empire.MissionStatus
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class MissionApprovalLifecycleTest {
    @Before fun setup() {
        EmpireMissionControl.resetForTest()
        HumanApprovalGateway.resetForTest()
    }

    @After fun cleanup() {
        HumanApprovalGateway.resetForTest()
        EmpireMissionControl.resetForTest()
    }

    @Test fun `approving linked deliverable completes reviewed mission`() {
        val mission = EmpireMissionControl.createMission("verified deliverable", "SHOSHI")
        EmpireMissionControl.transitionStatus(mission.id, MissionStatus.RUNNING, "test execution")
        EmpireMissionControl.transitionStatus(mission.id, MissionStatus.REVIEW, "deliverable ready")
        val approvalId = HumanApprovalGateway.requestApproval(
            "approve deliverable", "SHOSHI", ApprovalRiskLevel.MEDIUM, "owner review", mission.id
        )

        HumanApprovalGateway.provideDecision(approvalId, true)

        assertEquals(MissionStatus.COMPLETED, EmpireMissionControl.state.value.missions.single().status)
    }

    @Test fun `rejecting linked deliverable rejects reviewed mission`() {
        val mission = EmpireMissionControl.createMission("rejected deliverable", "LUNA")
        EmpireMissionControl.transitionStatus(mission.id, MissionStatus.RUNNING, "test execution")
        EmpireMissionControl.transitionStatus(mission.id, MissionStatus.REVIEW, "deliverable ready")
        val approvalId = HumanApprovalGateway.requestApproval(
            "approve deliverable", "LUNA", ApprovalRiskLevel.MEDIUM, "owner review", mission.id
        )

        HumanApprovalGateway.provideDecision(approvalId, false)

        assertEquals(MissionStatus.REJECTED, EmpireMissionControl.state.value.missions.single().status)
    }
}
