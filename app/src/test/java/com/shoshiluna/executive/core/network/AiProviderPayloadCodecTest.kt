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
        assertEquals("c", AiProviderPayloadCodec.decodeText(AiProviderId.CUSTOM, "{\"choices\":[{\"message\":{\"content\":\"c\"}}]}"))
    }

    @Test fun `encodes real image input for every provider`() {
        val image = AiImageInput("image/jpeg", "AQID", "photo.jpg")
        AiProviderId.entries.forEach { provider ->
            val model = if (provider == AiProviderId.CUSTOM) "custom" else AiProviderProtocols.forProvider(provider).defaultModel
            val json = AiProviderPayloadCodec.encode(provider, model, null, "see", listOf(image))
            assertTrue(json.contains("AQID"))
            assertTrue(json.contains("image"))
        }
    }
}
