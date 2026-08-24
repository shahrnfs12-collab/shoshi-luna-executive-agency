package com.shoshiluna.executive.core.network

data class AiProviderProtocol(
    val endpoint: String,
    val defaultModel: String,
    val authenticationHeader: String,
    val authenticationPrefix: String = ""
)

/** Audited provider endpoints and authentication contracts. */
object AiProviderProtocols {
    fun custom(endpoint: String, model: String, authenticationHeader: String, authenticationPrefix: String): AiProviderProtocol {
        val url = java.net.URI(endpoint.trim())
        require(url.scheme == "https" && !url.host.isNullOrBlank() && url.userInfo == null && url.fragment == null) { "Custom endpoint must be a clean HTTPS URL" }
        require(model.isNotBlank() && authenticationHeader.matches(Regex("[A-Za-z0-9-]+")))
        return AiProviderProtocol(url.toString(), model.trim(), authenticationHeader, authenticationPrefix)
    }
    fun local(endpoint: String, model: String): AiProviderProtocol {
        val url = java.net.URI(endpoint.trim())
        require(url.scheme == "https" && !url.host.isNullOrBlank() && url.userInfo == null && url.fragment == null) {
            "Local endpoint must be a clean HTTPS URL"
        }
        require(model.isNotBlank())
        return AiProviderProtocol(url.toString(), model.trim(), "Authorization", "Bearer ")
    }
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
            endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.7-flash:generateContent",
            defaultModel = "gemini-3.7-flash",
            authenticationHeader = "x-goog-api-key"
        )
        AiProviderId.OPENROUTER -> AiProviderProtocol(
            endpoint = "https://openrouter.ai/api/v1/chat/completions",
            defaultModel = "openrouter/auto",
            authenticationHeader = "Authorization",
            authenticationPrefix = "Bearer "
        )
        AiProviderId.LOCAL_OPENAI -> throw IllegalArgumentException("Local provider requires an explicit endpoint")
        AiProviderId.CUSTOM -> throw IllegalArgumentException("Custom provider requires an explicit endpoint")
    }
}
