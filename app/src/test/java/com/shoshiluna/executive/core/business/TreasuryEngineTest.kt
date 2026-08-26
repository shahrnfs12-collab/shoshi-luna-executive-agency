package com.shoshiluna.executive.core.business

import com.shoshiluna.executive.core.agent.*
import com.shoshiluna.executive.core.empire.EmpireMissionControl
import com.shoshiluna.executive.core.security.PermissionKernel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class TreasuryEngineTest {

    @Before
    fun setup() {
        MultiAgentCoordinator.resetForTest()
        EmpireMissionControl.resetForTest()
        EmpireMissionControl.releaseHalt()
        RevenueLedger.resetForTest()
        TreasuryLedger.resetForTest()
    }

    @Test
    fun `test Treasury Allocation - owner 40 40 20 rule`() = runBlocking {
        val revenue = RevenueRecord(
            amount = 10000.0,
            status = RevenueStatus.RECEIVED,
            missionId = "m1",
            environment = Environment.REAL,
            verificationRef = "REAL-123"
        )
        val cost = CostRecord(
            amount = 2000.0,
            category = "Ads",
            missionId = "m1",
            environment = Environment.REAL,
            verified = true
        )
        
        val allocation = TreasuryLedger.performAllocation(listOf(revenue), listOf(cost), Environment.REAL)
        
        assertNotNull(allocation)
        assertEquals(8000.0, allocation!!.verifiedNetProfit, 0.0)
        assertEquals(1600.0, allocation.allocationAmount, 0.0)
        assertEquals(3200.0, allocation.distribution.bankAmount, 0.0)
        assertEquals(3200.0, allocation.distribution.digitalWalletAmount, 0.0)
        assertEquals(1600.0, allocation.distribution.developmentAmount, 0.0)
        assertEquals(8000.0, allocation.distribution.totalAmount, 0.0)
        assertTrue(allocation.distribution.requiresOwnerApproval)
        assertFalse(allocation.distribution.bankDestinationAlias.any(Char::isDigit))
        assertEquals(TreasuryStatus.ALLOCATED, allocation.status)
    }

    @Test
    fun `test Treasury Isolation - Projected revenue ignored`() = runBlocking {
        val revenue = RevenueRecord(
            amount = 10000.0,
            status = RevenueStatus.PROJECTED,
            missionId = "m1",
            environment = Environment.REAL
        )
        
        val allocation = TreasuryLedger.performAllocation(listOf(revenue), emptyList(), Environment.REAL)
        assertNull("Projected revenue should not trigger allocation", allocation)
    }

    @Test
    fun `test Negative Profit - No allocation`() = runBlocking {
        val revenue = RevenueRecord(
            amount = 1000.0,
            status = RevenueStatus.RECEIVED,
            missionId = "m1",
            environment = Environment.REAL,
            verificationRef = "REAL-1"
        )
        val cost = CostRecord(
            amount = 2000.0,
            category = "Tools",
            missionId = "m1",
            environment = Environment.REAL,
            verified = true
        )
        
        val allocation = TreasuryLedger.performAllocation(listOf(revenue), listOf(cost), Environment.REAL)
        assertNull("Negative profit should result in zero allocation", allocation)
    }

    @Test
    fun `test Prevent Double Allocation`() = runBlocking {
        val revenue = RevenueRecord(
            id = "unique-rev-1",
            amount = 1000.0,
            status = RevenueStatus.RECEIVED,
            missionId = "m1",
            environment = Environment.REAL,
            verificationRef = "REF-1"
        )
        
        val firstAlloc = TreasuryLedger.performAllocation(listOf(revenue), emptyList(), Environment.REAL)
        assertNotNull(firstAlloc)
        
        val secondAlloc = TreasuryLedger.performAllocation(listOf(revenue), emptyList(), Environment.REAL)
        assertNull("Same revenue ID should not be allocated twice", secondAlloc)
    }

    @Test
    fun `test Spending Firewall - User Approval Required`() {
        val request = PermissionRequest(
            agentId = ExecutiveAgent.SHOSHI.id,
            capability = AgentCapability.TREASURY_SPEND,
            missionId = "m1"
        )
        val decision = PermissionKernel.authorize(request)
        assertFalse("Treasury spending must be blocked for autonomous agent requests", decision.allowed)
        assertEquals("Treasury Spending blocked: User Approval Required", decision.reason)
    }

    @Test
    fun `test Emergency Halt blocks Treasury Actions`() {
        EmpireMissionControl.emergencyHalt()
        val request = PermissionRequest(
            agentId = ExecutiveAgent.SHOSHI.id,
            capability = AgentCapability.MONITOR_TREASURY
        )
        val decision = PermissionKernel.authorize(request)
        assertFalse("Treasury monitoring should be blocked during Emergency Halt", decision.allowed)
    }

    @Test(expected = SecurityException::class)
    fun `test ALLOCATED cannot become FUNDED without verification`() = runBlocking {
        val revenue = RevenueRecord(
            id = "rev-1",
            amount = 1000.0,
            status = RevenueStatus.RECEIVED,
            missionId = "m1",
            environment = Environment.REAL,
            verificationRef = "REF-1"
        )
        val allocation = TreasuryLedger.performAllocation(listOf(revenue), emptyList(), Environment.REAL)
        assertNotNull(allocation)
        
        TreasuryLedger.updateAllocationStatus(allocation!!.id, TreasuryStatus.FUNDED, null)
    }

    @Test
    fun `test Reversal Handling - Accounting consistency`() = runBlocking {
        val revenue = RevenueRecord(
            id = "rev-reversal",
            amount = 1000.0,
            status = RevenueStatus.RECEIVED,
            missionId = "m1",
            environment = Environment.REAL,
            verificationRef = "REF-1"
        )
        TreasuryLedger.performAllocation(listOf(revenue), emptyList(), Environment.REAL)
        
        val reversalCost = CostRecord(
            amount = 1000.0,
            category = "REVERSAL",
            missionId = "m1",
            environment = Environment.REAL,
            verified = true
        )
        
        TreasuryLedger.performAllocation(emptyList(), listOf(reversalCost), Environment.REAL)
        
        val totalAllocated = TreasuryLedger.totalAllocatedBalance.first()
        assertTrue(totalAllocated >= 0.0)
    }
}
