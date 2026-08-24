package com.shoshiluna.executive.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiProviderProtocolTest {
    @Test fun `each built in provider has https endpoint and auth contract`() {
        AiProviderId.entries.filter { it !in setOf(AiProviderId.CUSTOM, AiProviderId.LOCAL_OPENAI) }.forEach { provider ->
            val protocol = AiProviderProtocols.forProvider(provider)
            assertTrue(protocol.endpoint.startsWith("https://"))
            assertTrue(protocol.authenticationHeader.isNotBlank())
            assertTrue(protocol.defaultModel.isNotBlank())
        }
    }

    @Test fun `local provider permits only loopback cleartext or clean https`() {
        assertTrue(AiProviderProtocols.local("https://local-ai.example/v1/chat/completions", "llama3.2").endpoint.startsWith("https://"))
        assertTrue(AiProviderProtocols.local("https://local-ai.example/v1/chat/completions", "model").endpoint.startsWith("https://"))
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            AiProviderProtocols.local("http://192.168.1.20:11434/v1/chat/completions", "model")
        }
    }

    @Test fun `gemini uses dedicated api key header`() {
        val protocol = AiProviderProtocols.forProvider(AiProviderId.GOOGLE_GEMINI)
        assertEquals("x-goog-api-key", protocol.authenticationHeader)
        assertEquals("gemini-3.7-flash", protocol.defaultModel)
    }

    @Test fun `openrouter delegates model choice to its task aware auto router`() {
        assertEquals("openrouter/auto", AiProviderProtocols.forProvider(AiProviderId.OPENROUTER).defaultModel)
    }

    @Test fun `custom provider rejects insecure endpoint`() {
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            AiProviderProtocols.custom("http://example.com/v1/chat/completions", "model", "Authorization", "Bearer ")
        }
    }
}
