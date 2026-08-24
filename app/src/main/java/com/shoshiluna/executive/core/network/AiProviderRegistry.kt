package com.shoshiluna.executive.core.network

enum class AiProviderId {
    OPENAI,
    ANTHROPIC,
    GOOGLE_GEMINI,
    OPENROUTER,
    OPENROUTER_UNCENSORED,
    GROQ,
    CUSTOM
}

data class DetectedAiProvider(
    val provider: AiProviderId?,
    val confidence: Confidence,
    val requiresUserSelection: Boolean,
    val reason: String
) {
    enum class Confidence { HIGH, MEDIUM, UNKNOWN }
}

/**
 * Local-only credential classification. A secret is never sent to several vendors
 * in an attempt to discover where it belongs.
 */
object AiProviderRegistry {
    fun detect(rawSecret: String): DetectedAiProvider {
        val secret = normalizeSecret(rawSecret)
        require(secret.isNotEmpty()) { "API credential cannot be blank" }

        return when {
            secret.startsWith("sk-ant-") -> detected(AiProviderId.ANTHROPIC, "Anthropic key prefix")
            secret.startsWith("AIza") -> detected(AiProviderId.GOOGLE_GEMINI, "Google API key prefix")
            secret.startsWith("sk-or-") -> detected(AiProviderId.OPENROUTER, "OpenRouter key prefix; usable by standard or uncensored OpenRouter profiles")
            secret.startsWith("gsk_") -> detected(AiProviderId.GROQ, "Groq key prefix")
            secret.startsWith("sk-proj-") -> detected(AiProviderId.OPENAI, "OpenAI project key prefix")
            secret.startsWith("sk-") -> DetectedAiProvider(
                provider = null,
                confidence = DetectedAiProvider.Confidence.UNKNOWN,
                requiresUserSelection = true,
                reason = "The sk- prefix is shared by multiple providers"
            )
            else -> DetectedAiProvider(
                provider = null,
                confidence = DetectedAiProvider.Confidence.UNKNOWN,
                requiresUserSelection = true,
                reason = "Unknown credential format; provider and endpoint are required"
            )
        }
    }

    fun normalizeSecret(rawSecret: String): String = rawSecret
        .trim()
        .removePrefix("Bearer ")
        .trim()
        .replace("\r", "")
        .replace("\n", "")

    private fun detected(provider: AiProviderId, reason: String) = DetectedAiProvider(
        provider = provider,
        confidence = DetectedAiProvider.Confidence.HIGH,
        requiresUserSelection = false,
        reason = reason
    )
}
