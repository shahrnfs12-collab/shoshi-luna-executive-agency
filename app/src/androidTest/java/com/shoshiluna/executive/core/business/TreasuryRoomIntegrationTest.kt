package com.shoshiluna.executive.core.business

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shoshiluna.executive.core.business.db.TreasuryDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TreasuryRoomIntegrationTest {
    private lateinit var db: TreasuryDatabase

    private val verifier = object : ApprovalProofVerifier {
        override suspend fun verify(proposal: FinancialProposal, approval: AuthenticatedApproval) = ApprovalVerificationResult.Verified
    }

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), TreasuryDatabase::class.java)
            .allowMainThreadQueries().build()
    }
    @After fun tearDown() = db.close()

    @Test fun simulationDoubleEntryIsAtomicAndIdempotent() = runBlocking {
        val engine = TreasuryEngine(db.treasuryDao(), verifier, isEmergencyHaltActive = { false })
        val proposal = FinancialProposal("P1", "M1", "C1", 1025L, "USD", "A", "B", ExecutionMode.SIMULATION)
        val first = engine.executeDurableTransfer("R1", proposal)
        assertTrue(first.isSuccess)
        assertEquals(2, first.getOrThrow().entries.size)
        val second = engine.executeDurableTransfer("R1", proposal)
        assertTrue(second.isSuccess)
        assertEquals(first.getOrThrow().transactionId, second.getOrThrow().transactionId)
    }

    @Test fun requestIdCannotBeReboundToDifferentProposal() = runBlocking {
        val engine = TreasuryEngine(db.treasuryDao(), verifier, isEmergencyHaltActive = { false })
        val p1 = FinancialProposal("P1", "M1", "C1", 100L, "USD", "A", "B", ExecutionMode.SIMULATION)
        val p2 = FinancialProposal("P2", "M1", "C2", 100L, "USD", "A", "B", ExecutionMode.SIMULATION)
        assertTrue(engine.executeDurableTransfer("R1", p1).isSuccess)
        assertTrue(engine.executeDurableTransfer("R1", p2).isFailure)
    }
}
