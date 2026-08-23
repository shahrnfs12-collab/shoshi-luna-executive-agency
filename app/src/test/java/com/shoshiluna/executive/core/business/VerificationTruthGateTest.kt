package com.shoshiluna.executive.core.business

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class VerificationTruthGateTest {
    @Before fun resetEvidence() { EvidenceRegistry.resetForTest() }

    @Test fun arbitraryStringCannotVerifyRealRevenue() {
        val record = RevenueRecord(
            amount = 1000.0,
            status = RevenueStatus.INVOICED,
            missionId = "M-TRUTH-${System.nanoTime()}",
            environment = Environment.REAL
        )
        RevenueLedger.addRecord(record)
        assertFalse(RevenueVerificationService.verifyAndPost(record.id, "arbitrary-string", "TEST"))
        assertEquals(RevenueStatus.INVOICED, RevenueLedger.records.value.first { it.id == record.id }.status)
    }

    @Test fun onlyVerifiedRegistryEvidenceCanPostRevenue() {
        val record = RevenueRecord(
            amount = 2500.0,
            status = RevenueStatus.INVOICED,
            missionId = "M-EVIDENCE-${System.nanoTime()}",
            environment = Environment.REAL
        )
        RevenueLedger.addRecord(record)
        val evidenceId = EvidenceRegistry.record(
            EvidenceCategory.EXTERNAL_EVIDENCE,
            "Gateway receipt",
            "TEST_GATEWAY",
            record.missionId
        )
        assertFalse(RevenueVerificationService.verifyAndPost(record.id, evidenceId, "TEST"))
        EvidenceRegistry.verify(evidenceId, "verified-test-reference")
        assertTrue(RevenueVerificationService.verifyAndPost(record.id, evidenceId, "TEST"))
        assertEquals(RevenueStatus.RECEIVED, RevenueLedger.records.value.first { it.id == record.id }.status)
    }
}
