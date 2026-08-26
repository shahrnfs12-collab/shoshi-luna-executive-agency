package com.shoshiluna.executive.core.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiResponseQualityGateTest {
    @Test fun classifierLabelsAreNotConversation() {
        assertFalse(AiResponseQualityGate.isUsable("User Safety: safe"))
        assertFalse(AiResponseQualityGate.isUsable("Safety: unsafe"))
        assertFalse(AiResponseQualityGate.isUsable("safe"))
    }

    @Test fun realHebrewAnswerIsAccepted() {
        assertTrue(AiResponseQualityGate.isUsable("הנה תוכנית פעולה מעשית בשלושה שלבים."))
    }

    @Test fun openRouterUsesPinnedFreeConversationModel() {
        assertTrue(AiProviderProtocols.forProvider(AiProviderId.OPENROUTER).defaultModel.contains(":free"))
        assertTrue(AiProviderProtocols.forProvider(AiProviderId.OPENROUTER).defaultModel.contains("nemotron"))
    }
}
