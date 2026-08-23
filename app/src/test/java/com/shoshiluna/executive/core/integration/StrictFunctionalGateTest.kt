package com.shoshiluna.executive.core.integration

import com.shoshiluna.executive.core.agent.*
import com.shoshiluna.executive.core.business.*
import com.shoshiluna.executive.core.empire.EmpireMissionControl
import com.shoshiluna.executive.core.empire.MissionStatus
import com.shoshiluna.executive.core.security.PermissionKernel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class StrictFunctionalGateTest {
    @Before fun setup() {
        EmpireMissionControl.resetForTest()
        PermissionKernel.resetForTest()
        PermissionKernel.registerAgent(MultiAgentCoordinator.shoshi.value)
        PermissionKernel.registerAgent(MultiAgentCoordinator.luna.value)
    }
    @After fun cleanup() { EmpireMissionControl.resetForTest() }

    @Test fun councilUsesSessionIdAndResolvesConsensus() {
        val mission = EmpireMissionControl.createMission("council", ExecutiveAgent.SHOSHI.id)
        val session = CouncilEngine.startSession(mission.id, "agenda", setOf(ExecutiveAgent.SHOSHI.id, ExecutiveAgent.LUNA.id))
        assertTrue(CouncilEngine.castVote(session.id, ExecutiveAgent.SHOSHI.id, "APPROVE"))
        assertTrue(CouncilEngine.castVote(session.id, ExecutiveAgent.LUNA.id, "APPROVE"))
        assertEquals(CouncilOutcome.CONSENSUS, CouncilEngine.sessions.value[session.id]?.outcome)
    }

    @Test fun finalPipelineStageGoesToReviewNotAutoComplete() {
        val mission = EmpireMissionControl.createMission("pipeline", ExecutiveAgent.SHOSHI.id)
        AgencyOrchestrator.startPipeline(mission.id)

        PipelineStage.entries.forEach { stage ->
            assertEquals(stage, AgencyOrchestrator.pipelineStates.value[mission.id]?.currentStage)
            val task = PipelineTask(
                stage = stage,
                agentId = ExecutiveAgent.SHOSHI.id,
                description = if (stage == PipelineStage.TREASURY_ALLOCATION) "final verify" else stage.name
            )
            AgencyOrchestrator.addTask(mission.id, task)
            AgencyOrchestrator.updateTaskStatus(mission.id, task.id, PipelineStatus.COMPLETED)
        }

        assertEquals(MissionStatus.REVIEW, EmpireMissionControl.state.value.missions.first { it.id == mission.id }.status)
    }

    @Test fun unconfiguredToolFailsInsteadOfSimulatingSuccess() = runBlocking {
        val mission = EmpireMissionControl.createMission("tool", ExecutiveAgent.SHOSHI.id)
        EmpireMissionControl.transitionStatus(mission.id, MissionStatus.PLANNED, "planned for tool use")
        EmpireMissionControl.transitionStatus(mission.id, MissionStatus.AWAITING_APPROVAL, "approval required")
        EmpireMissionControl.transitionStatus(mission.id, MissionStatus.APPROVED, "test approval state")
        val tool = ToolDefinition("read-test", "Read Test", ToolRiskClass.READ_ONLY, requiresUserApproval = false)
        ToolGateway.registerTool(tool)
        val result = ToolGateway.executeTool(ToolRequest(toolId = tool.id, agentId = ExecutiveAgent.SHOSHI.id, missionId = mission.id, inputData = emptyMap()))
        assertEquals(ToolExecutionStatus.FAILED, result.status)
        assertTrue(result.errorMessage.orEmpty().contains("NOT CONFIGURED"))
        ToolGateway.unregisterTool(tool.id)
    }

    @Test fun emergencyHaltBlocksPipelineMutation() {
        val mission = EmpireMissionControl.createMission("halt", ExecutiveAgent.SHOSHI.id)
        AgencyOrchestrator.startPipeline(mission.id)
        EmpireMissionControl.emergencyHalt()
        assertThrows(IllegalStateException::class.java) {
            AgencyOrchestrator.addTask(mission.id, PipelineTask(stage = PipelineStage.CAPABILITY_DISCOVERY, agentId = ExecutiveAgent.SHOSHI.id, description = "blocked"))
        }
    }
}
