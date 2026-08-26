package com.shoshiluna.executive.core.business

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RevenueAgentNetworkTest {
    @Before fun reset() = RevenueAgentNetwork.resetForTest()

    @Test fun `network has exactly four bounded advisory roles`() {
        val agents = RevenueAgentNetwork.agents.value.values
        assertEquals(4, agents.size)
        assertEquals(setOf(RevenueAgentRole.SCOUT, RevenueAgentRole.VALIDATOR, RevenueAgentRole.BUILDER, RevenueAgentRole.GROWTH), agents.map { it.role }.toSet())
        assertTrue(agents.all { it.status == RevenueAgentRunStatus.READY })
    }

    @Test fun `daily target is a target and external action remains approval gated`() {
        assertEquals(1000.0, RevenueAgentNetwork.DAILY_REVENUE_TARGET_USD, 0.0)
        assertTrue(RevenueAgentReport(cycleId = "c", agentId = "a", role = RevenueAgentRole.SCOUT, content = "x", evidenceId = "e").requiresOwnerApprovalForExternalAction)
    }
}
