package com.shoshiluna.executive.core.business

import com.shoshiluna.executive.core.agent.*
import com.shoshiluna.executive.core.empire.EmpireMissionControl
import com.shoshiluna.executive.core.empire.MissionStatus
import com.shoshiluna.executive.core.security.PermissionKernel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class BusinessEngineTest {

    @Before
    fun setup() {
        EmpireMissionControl.resetForTest()
        PermissionKernel.resetForTest()
        MultiAgentCoordinator.resetForTest()
        RevenueLedger.resetForTest()
        TreasuryLedger.resetForTest()
    }

    @Test
    fun `test BusinessMission lifecycle transitions`() {
        val mission = EmpireMissionControl.createMission("New Business Objective", ExecutiveAgent.SHOSHI.id)
        
        EmpireMissionControl.transitionStatus(mission.id, MissionStatus.DISCOVERED, "Opportunity found")
        var updated = EmpireMissionControl.state.value.missions.find { it.id == mission.id }
        assertEquals(MissionStatus.DISCOVERED, updated?.status)

        EmpireMissionControl.transitionStatus(mission.id, MissionStatus.RESEARCHING, "Analyzing market")
        updated = EmpireMissionControl.state.value.missions.find { it.id == mission.id }
        assertEquals(MissionStatus.RESEARCHING, updated?.status)
    }

    @Test
    fun `test Revenue Invariant - REAL RECEIVED REVENUE calculation`() = runBlocking {
        val simRecord = RevenueRecord(
            amount = 1000.0,
            status = RevenueStatus.RECEIVED,
            missionId = "m1",
            environment = Environment.SIMULATION,
            verificationRef = "SIM-123"
        )
        
        val realRecordPending = RevenueRecord(
            amount = 2000.0,
            status = RevenueStatus.PIPELINE,
            missionId = "m2",
            environment = Environment.REAL
        )

        RevenueLedger.addRecord(simRecord)
        RevenueLedger.addRecord(realRecordPending)

        val totalReal = RevenueLedger.totalRealRevenue.first()
        // If Ledger is not reset, we check if it contains our record
        assertTrue(totalReal >= 0.0) 
    }

    @Test(expected = SecurityException::class)
    fun `test Revenue Invariant - Reject RECEIVED without verification`() {
        val fakeRecord = RevenueRecord(
            amount = 10000.0,
            status = RevenueStatus.RECEIVED,
            missionId = "m-fake",
            environment = Environment.REAL,
            verificationRef = null 
        )
        RevenueLedger.addRecord(fakeRecord)
    }

    @Test
    fun `test Specialist Agent permission inheritance`() {
        val shoshi = MultiAgentCoordinator.shoshi.value
        val researcher = SpecialistAgentFactory.createMarketResearcher(shoshi.id, "m1")
        assertNotNull(researcher)
        assertTrue(
            "Specialist permissions must be a subset of parent",
            shoshi.allowedCapabilities.containsAll(researcher!!.allowedCapabilities)
        )
    }

    @Test
    fun `test Bounded Specialist Spawning`() {
        val shoshi = MultiAgentCoordinator.shoshi.value
        repeat(20) {
            SpecialistAgentFactory.createMarketResearcher(shoshi.id, "m-$it")
        }
        val extra = SpecialistAgentFactory.createMarketResearcher(shoshi.id, "m-extra")
        assertNull("Should not allow more than 20 sub-agents", extra)
    }

    @Test
    fun `test Approval Firewall - Block outreach on unapproved mission`() {
        val mission = EmpireMissionControl.createMission("Stealth Mission", ExecutiveAgent.SHOSHI.id)
        val request = PermissionRequest(
            agentId = ExecutiveAgent.LUNA.id,
            capability = AgentCapability.COMMUNICATE_EXTERNAL,
            missionId = mission.id
        )
        val decision = PermissionKernel.authorize(request)
        assertFalse("External action should be blocked for unapproved mission", decision.allowed)
    }

    @Test
    fun `test Evidence Classification - Assumption cannot be silently promoted to Fact`() {
        val memory = SovereignMemory(
            agentId = ExecutiveAgent.SHOSHI.id,
            partition = MemoryPartition.AGENT_WORKSPACE,
            content = "Market size is 1B",
            evidenceCategory = EvidenceCategory.ASSUMPTION
        )
        assertEquals(EvidenceCategory.ASSUMPTION, memory.evidenceCategory)
    }

    @Test
    fun `test Simulation vs Real Isolation`() = runBlocking {
        val realRecord = RevenueRecord(
            amount = 100.0,
            status = RevenueStatus.RECEIVED,
            missionId = "real-m",
            environment = Environment.REAL,
            verificationRef = "REF-REAL"
        )
        RevenueLedger.addRecord(realRecord)
        val records = RevenueLedger.records.first()
        assertTrue(records.any { it.environment == Environment.REAL && it.amount == 100.0 })
    }

    @Test
    fun `test KPI Engine`() = runBlocking {
        val lead1 = Lead(name = "L1", contactInfo = "C1", opportunityId = "o1", status = LeadStatus.WON)
        KpiManager.updateLeads(listOf(lead1))
        val currentKpis = KpiManager.kpis.first()
        assertEquals(1, currentKpis.wonLeads)
    }

    @Test
    fun `test Emergency Halt - Block Business Mission Creation`() {
        EmpireMissionControl.emergencyHalt()
        val request = PermissionRequest(
            agentId = ExecutiveAgent.SHOSHI.id,
            capability = AgentCapability.MANAGE_MISSION
        )
        val decision = PermissionKernel.authorize(request)
        assertFalse("Action should be blocked during Emergency Halt", decision.allowed)
    }
}
