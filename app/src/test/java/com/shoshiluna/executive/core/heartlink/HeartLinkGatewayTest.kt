package com.shoshiluna.executive.core.heartlink

import com.shoshiluna.executive.core.business.EvidenceRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class HeartLinkGatewayTest {
    @Before fun setup() { HeartLinkGateway.resetForTest(); EvidenceRegistry.resetForTest() }
    @After fun cleanup() { HeartLinkGateway.resetForTest(); EvidenceRegistry.resetForTest() }

    @Test fun writeCannotStartWithoutExplicitApproval() {
        val p = HeartLinkProposal(
            objective = "Fix bug",
            snapshotId = "snapshot-1",
            operations = listOf(HeartLinkOperation(HeartLinkTool.APPLY_APPROVED_PATCH, "app/src/main/Test.kt", "bounded fix"))
        )
        HeartLinkGateway.submit(p).getOrThrow()
        assertThrows(IllegalArgumentException::class.java) { HeartLinkGateway.start(p.id) }
    }

    @Test fun secretsAndSigningPathsAreOutsideAgentBoundary() {
        val p = HeartLinkProposal(
            objective = "Read key",
            snapshotId = "snapshot-1",
            operations = listOf(HeartLinkOperation(HeartLinkTool.READ_FILE, "release/signing.keystore", "inspect"))
        )
        assertTrue(HeartLinkGateway.submit(p).isFailure)
    }

    @Test fun approvedVerifiedWorkCreatesEvidence() {
        val p = HeartLinkProposal(
            objective = "Build verified APK",
            snapshotId = "snapshot-1",
            operations = listOf(HeartLinkOperation(HeartLinkTool.BUILD_APK, "app", "assemble debug"))
        )
        HeartLinkGateway.submit(p).getOrThrow()
        HeartLinkGateway.approve(p.id, "owner-approval")
        HeartLinkGateway.start(p.id)
        val done = HeartLinkGateway.finish(p.id, true, "111 tests and APK signature verified")
        assertEquals(HeartLinkStatus.VERIFIED, done.status)
        assertEquals(1, done.evidenceIds.size)
        assertTrue(EvidenceRegistry.isVerified(done.evidenceIds.single()))
    }
}
