package com.shoshiluna.executive.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiProviderRegistryTest {
    @Test fun `detects known provider prefixes without a network call`() {
        assertEquals(AiProviderId.OPENAI, AiProviderRegistry.detect("sk-proj-example").provider)
        assertEquals(AiProviderId.ANTHROPIC, AiProviderRegistry.detect("sk-ant-example").provider)
        assertEquals(AiProviderId.GOOGLE_GEMINI, AiProviderRegistry.detect("AIza-example").provider)
        assertEquals(AiProviderId.OPENROUTER, AiProviderRegistry.detect("sk-or-example").provider)
    }

    @Test fun `ambiguous key is never sprayed across providers`() {
        val result = AiProviderRegistry.detect("sk-ambiguous")
        assertTrue(result.requiresUserSelection)
        assertEquals(null, result.provider)
    }

    @Test fun `normalizes paste artifacts`() {
        assertEquals("sk-proj-example", AiProviderRegistry.normalizeSecret("  Bearer sk-proj-example\r\n"))
        assertFalse(AiProviderRegistry.normalizeSecret(" key ").contains(" "))
    }
}
