package com.shoshiluna.desktop

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

enum class DesktopProvider(val endpoint: String) {
    OPENAI("https://api.openai.com/v1/chat/completions"),
    OPENROUTER("https://openrouter.ai/api/v1/chat/completions")
}

data class DesktopAiConfig(
    val provider: DesktopProvider,
    val apiKey: String,
    val model: String
)

object AiGateway {
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build()

    fun complete(config: DesktopAiConfig, systemPrompt: String, userText: String): String {
        require(config.apiKey.isNotBlank()) { "API key is required" }
        require(config.model.isNotBlank()) { "Model is required" }

        val messages = JsonArray().apply {
            add(JsonObject().apply { addProperty("role", "system"); addProperty("content", systemPrompt) })
            add(JsonObject().apply { addProperty("role", "user"); addProperty("content", userText) })
        }
        val body = JsonObject().apply {
            addProperty("model", config.model)
            add("messages", messages)
        }.toString()

        val builder = HttpRequest.newBuilder(URI.create(config.provider.endpoint))
            .timeout(Duration.ofSeconds(90))
            .header("Authorization", "Bearer ${config.apiKey}")
            .header("Content-Type", "application/json")
        if (config.provider == DesktopProvider.OPENROUTER) {
            builder.header("HTTP-Referer", "https://github.com/shahrnfs12-collab/shoshi-luna-executive-agency")
            builder.header("X-Title", "SHOSHI LUNA DESKTOP")
        }
        val response = client.send(builder.POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            throw IllegalStateException("Provider error ${response.statusCode()}: ${response.body().take(600)}")
        }
        val root = JsonParser.parseString(response.body()).asJsonObject
        return root.getAsJsonArray("choices")
            ?.firstOrNull()?.asJsonObject
            ?.getAsJsonObject("message")
            ?.get("content")?.asString
            ?: throw IllegalStateException("Provider returned no message content")
    }
}
