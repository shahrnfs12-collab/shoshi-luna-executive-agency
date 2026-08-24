package com.shoshiluna.executive.core.fabric

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiFabricTest {
    @Test fun registryUsesOnlyTruthfulStatusesAndNeverStartsVerified() {
        assertTrue(ModelRegistry.all().size <= ModelRegistry.MAX_MODELS)
        assertTrue(ModelRegistry.verified().isEmpty())
        assertTrue(ModelRegistry.all().all { it.evidence.isNotEmpty() })
    }

    @Test fun internalDataCannotRouteToOpenPolicyPool() {
        val local = requireNotNull(ModelRegistry.get("local/custom-open-weight"))
        val decision = FabricPolicyEngine.authorize(local, TaskRequirements(setOf(FabricCapability.GENERAL_LLM)))
        assertFalse(decision.allowed)
    }

    @Test fun configuredProviderGetsPrimaryAndFallbackDecision() {
        val result = MetaRouter.route(
            TaskRequirements(setOf(FabricCapability.GENERAL_LLM), sensitivity = DataSensitivity.INTERNAL),
            setOf("openai")
        )
        assertTrue(result.isSuccess)
        assertEquals("openai", result.getOrThrow().primary.model.providerId)
        assertNotNull(result.getOrThrow().policyDecisionId)
        assertTrue(FabricAuditLedger.verifyChain())
    }

    @Test fun costControllerUsesDocumentedTokenPricing() {
        val model = requireNotNull(ModelRegistry.get("openai/gpt-5.6-luna"))
        assertEquals(0.0014, CostController.estimateUsd(model, 1000, 1000)!!, 0.000001)
    }

    @Test fun verifiedTransitionRequiresRealSuccessEvidence() {
        val model = requireNotNull(ModelRegistry.get("openai/gpt-5.6-sol"))
        val invalid = runCatching { model.copy(status = RegistryStatus.VERIFIED, lastVerifiedAt = System.currentTimeMillis()) }
        assertTrue(invalid.isFailure)
    }
}
