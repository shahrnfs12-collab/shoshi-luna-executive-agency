package com.shoshiluna.executive.core.network

data class AiProviderProtocol(
    val endpoint: String,
    val defaultModel: String,
    val authenticationHeader: String,
    val authenticationPrefix: String = ""
)

/** Audited provider endpoints and authentication contracts. */
object AiProviderProtocols {
    fun forProvider(provider: AiProviderId): AiProviderProtocol = when (provider) {
        AiProviderId.OPENAI -> AiProviderProtocol(
            endpoint = "https://api.openai.com/v1/responses",
            defaultModel = "gpt-5.6-luna",
            authenticationHeader = "Authorization",
            authenticationPrefix = "Bearer "
        )
        AiProviderId.ANTHROPIC -> AiProviderProtocol(
            endpoint = "https://api.anthropic.com/v1/messages",
            defaultModel = "claude-sonnet-5",
            authenticationHeader = "x-api-key"
        )
        AiProviderId.GOOGLE_GEMINI -> AiProviderProtocol(
            endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent",
            defaultModel = "gemini-3.5-flash",
            authenticationHeader = "x-goog-api-key"
        )
        AiProviderId.OPENROUTER -> AiProviderProtocol(
            endpoint = "https://openrouter.ai/api/v1/chat/completions",
            defaultModel = "openai/gpt-5.6-luna",
            authenticationHeader = "Authorization",
            authenticationPrefix = "Bearer "
        )
        AiProviderId.CUSTOM -> throw IllegalArgumentException("Custom provider requires an explicit endpoint")
    }
}
