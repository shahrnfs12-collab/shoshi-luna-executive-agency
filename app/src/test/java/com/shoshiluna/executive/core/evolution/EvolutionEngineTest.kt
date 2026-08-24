package com.shoshiluna.executive.core.evolution

import com.shoshiluna.executive.core.business.EvidenceCategory
import com.shoshiluna.executive.core.business.EvidenceRegistry
import com.shoshiluna.executive.core.security.HumanApprovalGateway
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class EvolutionEngineTest {
    @Before fun setup() { EvidenceRegistry.resetForTest(); HumanApprovalGateway.resetForTest(); EvolutionEngine.resetForTest() }
    @After fun cleanup() { EvidenceRegistry.resetForTest(); HumanApprovalGateway.resetForTest(); EvolutionEngine.resetForTest() }

    @Test fun `unverified outcome cannot train agency`() {
        val evidence = EvidenceRegistry.record(EvidenceCategory.AI_INFERENCE, "maybe worked", "test", "m1")
        assertThrows(IllegalArgumentException::class.java) { EvolutionEngine.propose("m1", "repeat it", listOf(evidence)) }
    }

    @Test fun `verified learning requires owner approval before activation`() {
        val evidence = EvidenceRegistry.record(EvidenceCategory.VERIFIED_RESULT, "worked", "test", "m1")
        val learning = EvolutionEngine.propose("m1", "reuse verified process", listOf(evidence))
        val approval = EvolutionEngine.requestActivation(learning.id, "SHOSHI")
        assertThrows(IllegalStateException::class.java) { EvolutionEngine.finalizeActivation(learning.id) }
        HumanApprovalGateway.provideDecision(approval, true)
        assertEquals(LearningStatus.ACTIVE, EvolutionEngine.finalizeActivation(learning.id).status)
    }
}
