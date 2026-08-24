package com.shoshiluna.executive.core.integration

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shoshiluna.executive.core.business.ArtifactFactory
import com.shoshiluna.executive.core.business.ArtifactType
import com.shoshiluna.executive.core.business.EvidenceCategory
import com.shoshiluna.executive.core.business.EvidenceRegistry
import com.shoshiluna.executive.core.evolution.EvolutionEngine
import com.shoshiluna.executive.core.security.ApprovalRiskLevel
import com.shoshiluna.executive.core.security.HumanApprovalGateway
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AgencyPersistenceRuntimeTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test fun approvalsArtifactsAndLearningAreWrittenToDurableStorage() {
        HumanApprovalGateway.initialize(context)
        ArtifactFactory.initialize(context)
        EvolutionEngine.initialize(context)

        val approvalId = HumanApprovalGateway.requestApproval("test action", "SHOSHI", ApprovalRiskLevel.MEDIUM, "runtime persistence test")
        val artifact = ArtifactFactory.createArtifact(ArtifactType.REPORT, "SHOSHI", "runtime-task", "runtime artifact")
        val evidenceId = EvidenceRegistry.record(EvidenceCategory.VERIFIED_RESULT, "verified runtime outcome", "androidTest", "runtime-mission")
        val learning = EvolutionEngine.propose("runtime-mission", "retain verified runtime process", listOf(evidenceId))

        val approvalsJson = context.getSharedPreferences("agency_approvals_v1", 0).getString("requests", "").orEmpty()
        val artifactsJson = context.getSharedPreferences("agency_artifacts_v1", 0).getString("artifacts", "").orEmpty()
        val evolutionJson = context.getSharedPreferences("agency_evolution_v1", 0).getString("learnings", "").orEmpty()
        assertTrue(approvalsJson.contains(approvalId))
        assertTrue(artifactsJson.contains(artifact.id))
        assertTrue(evolutionJson.contains(learning.id))
    }
}
