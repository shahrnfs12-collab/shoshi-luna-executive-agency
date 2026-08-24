package com.shoshiluna.executive.core.network

import com.google.gson.Gson
import com.google.gson.JsonObject

object AiProviderPayloadCodec {
    private val gson = Gson()

    fun encode(provider: AiProviderId, model: String, instructions: String?, input: String): String {
        require(model.isNotBlank() && input.isNotBlank())
        val payload: Map<String, Any> = when (provider) {
            AiProviderId.OPENAI -> mapOf("model" to model, "instructions" to (instructions ?: ""), "input" to input)
            AiProviderId.ANTHROPIC -> buildMap {
                put("model", model); put("max_tokens", 4096)
                if (!instructions.isNullOrBlank()) put("system", instructions)
                put("messages", listOf(mapOf("role" to "user", "content" to input)))
            }
            AiProviderId.GOOGLE_GEMINI -> buildMap {
                if (!instructions.isNullOrBlank()) put("system_instruction", mapOf("parts" to listOf(mapOf("text" to instructions))))
                put("contents", listOf(mapOf("role" to "user", "parts" to listOf(mapOf("text" to input)))))
            }
            AiProviderId.OPENROUTER, AiProviderId.GROQ, AiProviderId.CUSTOM -> mapOf(
                "model" to model,
                "messages" to buildList {
                    if (!instructions.isNullOrBlank()) add(mapOf("role" to "system", "content" to instructions))
                    add(mapOf("role" to "user", "content" to input))
                }
            )
        }
        return gson.toJson(payload)
    }

    fun decodeText(provider: AiProviderId, body: String): String {
        val root = runCatching { gson.fromJson(body, JsonObject::class.java) }.getOrNull() ?: return ""
        return when (provider) {
            AiProviderId.OPENAI -> decodeOpenAi(root)
            AiProviderId.ANTHROPIC -> root.getAsJsonArray("content")?.mapNotNull { item ->
                item.asJsonObject.takeIf { it.get("type")?.asString == "text" }?.get("text")?.asString
            }?.joinToString("\n").orEmpty()
            AiProviderId.GOOGLE_GEMINI -> root.getAsJsonArray("candidates")?.firstOrNull()?.asJsonObject
                ?.getAsJsonObject("content")?.getAsJsonArray("parts")?.mapNotNull { it.asJsonObject.get("text")?.asString }
                ?.joinToString("\n").orEmpty()
            AiProviderId.OPENROUTER, AiProviderId.GROQ, AiProviderId.CUSTOM -> root.getAsJsonArray("choices")?.firstOrNull()?.asJsonObject
                ?.getAsJsonObject("message")?.get("content")?.asString.orEmpty()
        }.trim()
    }

    private fun decodeOpenAi(root: JsonObject): String {
        root.get("output_text")?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() }?.let { return it }
        return root.getAsJsonArray("output")?.flatMap { item ->
            item.asJsonObject.getAsJsonArray("content")?.mapNotNull { part ->
                part.asJsonObject.takeIf { it.get("type")?.asString in setOf("output_text", "text") }?.get("text")?.asString
            }.orEmpty()
        }?.joinToString("\n").orEmpty()
    }
}
