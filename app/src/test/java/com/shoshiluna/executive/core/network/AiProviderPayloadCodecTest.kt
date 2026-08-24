package com.shoshiluna.executive.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiProviderPayloadCodecTest {
    @Test fun `encodes every built in provider`() {
        AiProviderId.entries.filter { it != AiProviderId.CUSTOM }.forEach {
            val json = AiProviderPayloadCodec.encode(it, AiProviderProtocols.forProvider(it).defaultModel, "system", "hello")
            assertTrue(json.contains("hello"))
        }
    }

    @Test fun `decodes provider response shapes`() {
        assertEquals("o", AiProviderPayloadCodec.decodeText(AiProviderId.OPENAI, "{\"output_text\":\"o\"}"))
        assertEquals("a", AiProviderPayloadCodec.decodeText(AiProviderId.ANTHROPIC, "{\"content\":[{\"type\":\"text\",\"text\":\"a\"}]}"))
        assertEquals("g", AiProviderPayloadCodec.decodeText(AiProviderId.GOOGLE_GEMINI, "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"g\"}]}}]}"))
        assertEquals("r", AiProviderPayloadCodec.decodeText(AiProviderId.OPENROUTER, "{\"choices\":[{\"message\":{\"content\":\"r\"}}]}"))
    }
}
