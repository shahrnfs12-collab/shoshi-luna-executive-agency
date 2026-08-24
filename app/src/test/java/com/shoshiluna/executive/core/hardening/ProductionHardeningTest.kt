package com.shoshiluna.executive.core.hardening

import com.shoshiluna.executive.core.agent.*
import com.shoshiluna.executive.core.business.*
import com.shoshiluna.executive.core.empire.*
import com.shoshiluna.executive.core.security.PermissionKernel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ProductionHardeningTest {
    @Before fun setup() {
        EmpireMissionControl.resetForTest(); PermissionKernel.resetForTest(); EvidenceRegistry.resetForTest(); RequestDeduplicator.resetForTest()
        PermissionKernel.registerAgent(MultiAgentCoordinator.shoshi.value)
        PermissionKernel.registerAgent(MultiAgentCoordinator.luna.value)
    }
    @After fun cleanup() { EmpireMissionControl.resetForTest(); EvidenceRegistry.resetForTest() }

    @Test fun terminalMissionCannotBeResurrected() {
        val m = EmpireMissionControl.createMission("x", ExecutiveAgent.SHOSHI.id)
        EmpireMissionControl.transitionStatus(m.id, MissionStatus.CANCELLED, "cancel")
        assertThrows(IllegalArgumentException::class.java) { EmpireMissionControl.transitionStatus(m.id, MissionStatus.RUNNING, "illegal") }
    }

    @Test fun pipelineDoesNotAdvanceUntilAllTasksInStageAreDone() {
        val m = EmpireMissionControl.createMission("p", ExecutiveAgent.SHOSHI.id)
        AgencyOrchestrator.startPipeline(m.id)
        val a = PipelineTask(stage = PipelineStage.CAPABILITY_DISCOVERY, agentId = ExecutiveAgent.SHOSHI.id, description = "a")
        val b = PipelineTask(stage = PipelineStage.CAPABILITY_DISCOVERY, agentId = ExecutiveAgent.LUNA.id, description = "b")
        AgencyOrchestrator.addTask(m.id, a); AgencyOrchestrator.addTask(m.id, b)
        AgencyOrchestrator.updateTaskStatus(m.id, a.id, PipelineStatus.COMPLETED)
        assertEquals(PipelineStage.CAPABILITY_DISCOVERY, AgencyOrchestrator.pipelineStates.value[m.id]!!.currentStage)
        AgencyOrchestrator.updateTaskStatus(m.id, b.id, PipelineStatus.COMPLETED)
        assertEquals(PipelineStage.PRODUCTIZATION, AgencyOrchestrator.pipelineStates.value[m.id]!!.currentStage)
    }

    @Test fun duplicateKeyCanOnlyBeClaimedOnce() {
        assertTrue(RequestDeduplicator.claim("same")); assertFalse(RequestDeduplicator.claim("same"))
    }

    @Test fun verifiedToolResultHasMissionScopedEvidence() = runBlocking {
        val m = EmpireMissionControl.createMission("tool", ExecutiveAgent.SHOSHI.id)
        EmpireMissionControl.transitionStatus(m.id, MissionStatus.PLANNED, "tool execution planned")
        EmpireMissionControl.transitionStatus(m.id, MissionStatus.AWAITING_APPROVAL, "explicit approval required")
        EmpireMissionControl.transitionStatus(m.id, MissionStatus.APPROVED, "approved for test execution")
        val def = ToolDefinition("real-read", "real", ToolRiskClass.READ_ONLY, false, timeoutMs = 1000)
        ToolGateway.registerTool(def, ToolGateway.ToolAdapter { "ok" })
        val r = ToolGateway.executeTool(ToolRequest(toolId=def.id, agentId=ExecutiveAgent.SHOSHI.id, missionId=m.id, inputData=mapOf("q" to "secret-but-required-by-tool"), idempotencyKey="e2e-key"))
        assertEquals(ToolExecutionStatus.VERIFIED, r.status)
        assertNotNull(r.evidenceId); assertTrue(EvidenceRegistry.exists(r.evidenceId!!))
        assertEquals(1, EvidenceRegistry.getItemsForMission(m.id).size)
        ToolGateway.unregisterTool(def.id)
    }

    @Test fun readinessIsFailClosedForUnknownCapability() {
        assertEquals(CapabilityStatus.MISSING, CapabilityRegistry.get("does-not-exist").status)
        assertFalse(CapabilityRegistry.canExecute("does-not-exist"))
    }
}
