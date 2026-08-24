package com.shoshiluna.executive.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiProviderProtocolTest {
    @Test fun `each built in provider has https endpoint and auth contract`() {
        AiProviderId.entries.filter { it != AiProviderId.CUSTOM }.forEach { provider ->
            val protocol = AiProviderProtocols.forProvider(provider)
            assertTrue(protocol.endpoint.startsWith("https://"))
            assertTrue(protocol.authenticationHeader.isNotBlank())
            assertTrue(protocol.defaultModel.isNotBlank())
        }
    }

    @Test fun `gemini uses dedicated api key header`() {
        assertEquals("x-goog-api-key", AiProviderProtocols.forProvider(AiProviderId.GOOGLE_GEMINI).authenticationHeader)
    }

    @Test fun `custom provider rejects insecure endpoint`() {
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            AiProviderProtocols.custom("http://example.com/v1/chat/completions", "model", "Authorization", "Bearer ")
        }
    }
}
