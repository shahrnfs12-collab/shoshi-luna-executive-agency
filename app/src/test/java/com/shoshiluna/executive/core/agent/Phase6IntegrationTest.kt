package com.shoshiluna.executive.core.agent

import com.shoshiluna.executive.core.business.*
import com.shoshiluna.executive.core.empire.EmpireMissionControl
import com.shoshiluna.executive.core.engine.BlackSwanEngine
import com.shoshiluna.executive.core.engine.BlackSwanEvent
import com.shoshiluna.executive.core.engine.AnomalyType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class Phase6IntegrationTest {

    @Before
    fun setup() {
        EmpireMissionControl.resetForTest()
        EmpireMissionControl.releaseHalt()
        RevenueLedger.resetForTest()
        TreasuryLedger.resetForTest()
    }

    @Test
    fun `test Pipeline Flow - Discovery to Revenue`() = runBlocking {
        val mission = EmpireMissionControl.createMission("Growth Plan", ExecutiveAgent.SHOSHI.id)
        val state = AgencyOrchestrator.startPipeline(mission.id)
        assertNotNull(state)
        assertEquals(PipelineStage.CAPABILITY_DISCOVERY, state!!.currentStage)

        // Complete first stage
        val taskId = "t1"
        AgencyOrchestrator.addTask(mission.id, PipelineTask(id = taskId, stage = PipelineStage.CAPABILITY_DISCOVERY, agentId = "SHOSHI", description = "Discovery"))
        AgencyOrchestrator.updateTaskStatus(mission.id, taskId, PipelineStatus.COMPLETED)
        
        val updated = AgencyOrchestrator.pipelineStates.value[mission.id]
        assertEquals(PipelineStage.PRODUCTIZATION, updated?.currentStage)
    }

    @Test
    fun `test Black Swan Response - Severe Anomaly Trigger Halt`() {
        val event = BlackSwanEvent(
            type = AnomalyType.MARKET_COLLAPSE,
            severity = 0.9f,
            evidence = "Market crash detected"
        )
        BlackSwanEngine.reportAnomaly(event)
        
        assertTrue("Severe Black Swan must trigger Emergency Halt", EmpireMissionControl.state.value.emergencyHalt)
    }

    @Test
    fun `test Fraud Detection - Duplicate Payment Reference`() {
        val record1 = RevenueRecord(amount = 100.0, status = RevenueStatus.RECEIVED, missionId = "m1", verificationRef = "REF-123")
        RevenueLedger.addRecord(record1)
        
        val record2 = RevenueRecord(amount = 100.0, status = RevenueStatus.RECEIVED, missionId = "m2", verificationRef = "REF-123")
        val isValid = FraudAnomalyEngine.auditRevenueRecord(record2)
        
        assertFalse("Duplicate payment reference must be rejected by Fraud Engine", isValid)
    }

    @Test
    fun `test Agent Reputation Tracking`() {
        val shoshiId = ExecutiveAgent.SHOSHI.id
        AgentReputationEngine.recordTaskOutcome(shoshiId, success = true, quality = 0.9f)
        AgentReputationEngine.recordTaskOutcome(shoshiId, success = false, quality = 0.5f)
        
        val score = AgentReputationEngine.getScore(shoshiId)
        assertTrue("Reputation score should reflect success/failure and quality", score > 0 && score < 1)
    }

    @Test
    fun `test Artifact Factory Consistency`() {
        val artifact = ArtifactFactory.createArtifact(
            ArtifactType.PROPOSAL,
            "SHOSHI",
            "task-1",
            "Draft Proposal Content"
        )
        
        assertNotNull(artifact.hash)
        assertTrue(ArtifactFactory.artifacts.contains(artifact))
    }
}
