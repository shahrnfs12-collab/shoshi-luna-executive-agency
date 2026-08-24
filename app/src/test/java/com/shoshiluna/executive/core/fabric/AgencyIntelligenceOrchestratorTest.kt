package com.shoshiluna.executive.core.fabric

import com.shoshiluna.executive.core.business.TreasuryLedger
import com.shoshiluna.executive.core.business.Environment
import com.shoshiluna.executive.core.business.RevenueRecord
import com.shoshiluna.executive.core.business.RevenueStatus
import com.shoshiluna.executive.core.business.TreasuryStatus
import com.shoshiluna.executive.core.business.SpendStatus
import com.shoshiluna.executive.core.security.HumanApprovalGateway
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AgencyIntelligenceOrchestratorTest {
    @Before fun setup() { TreasuryLedger.resetForTest(); HumanApprovalGateway.resetForTest() }
    @After fun cleanup() { TreasuryLedger.resetForTest(); HumanApprovalGateway.resetForTest() }

    @Test fun procurementFailsClosedWhenTreasuryIsNotFunded() {
        val result = AiProcurementPlanner.propose("mission", "openai/gpt-5.6-luna", 1.0, "Hebrew research")
        assertTrue(result.isFailure)
        assertTrue(TreasuryLedger.spendProposals.value.isEmpty())
        assertEquals(0.0, TreasuryLedger.availableFundedBalance(), 0.0)
    }

    @Test fun brainAssignmentsAreExplicitAndModelSpecific() {
        val assignment = BrainAssignment("RED_TEAM", "model/a", "provider", "Find risks")
        assertEquals("model/a", assignment.modelId)
        assertEquals("RED_TEAM", assignment.role)
    }

    @Test fun fundedTreasuryCreatesProposalButNeverExecutesPurchase() {
        val revenue = RevenueRecord(amount = 1_000.0, status = RevenueStatus.RECEIVED, missionId = "m", environment = Environment.REAL, verificationRef = "verified")
        val allocation = requireNotNull(TreasuryLedger.performAllocation(listOf(revenue), emptyList(), Environment.REAL))
        TreasuryLedger.updateAllocationStatus(allocation.id, TreasuryStatus.FUNDED, "bank-evidence")
        val result = AiProcurementPlanner.propose("m", "openai/gpt-5.6-luna", 10.0, "Faster multilingual work")
        assertTrue(result.isSuccess)
        assertEquals(SpendStatus.PROPOSED, TreasuryLedger.spendProposals.value.single().status)
        assertTrue(!HumanApprovalGateway.isApproved(result.getOrThrow().approvalId))
    }
}
