package com.shoshiluna.executive.core.network

import android.content.Context
import com.shoshiluna.executive.core.security.ApiKeyManager

/** Bounded failover across keys already configured by the owner. */
object ResilientAiClient {
    private val freeFirstPriority = listOf(
        AiProviderId.OPENROUTER,
        AiProviderId.GROQ,
        AiProviderId.GOOGLE_GEMINI,
        AiProviderId.ANTHROPIC,
        AiProviderId.OPENAI,
        AiProviderId.CUSTOM
    )

    suspend fun getCompletionResult(
        context: Context,
        messages: List<ChatMessage>,
        model: String
    ): AiCallResult {
        val configured = ApiKeyManager.configuredProviders(context)
        if (configured.isEmpty()) return AiCallResult.Failure(AiConnectionState.API_KEY_MISSING, "No AI provider key is configured.")

        val active = ApiKeyManager.activeProvider(context)
        val ordered = buildList {
            // Respect an explicitly selected custom provider first; otherwise prefer legal free-tier routes.
            if (active == AiProviderId.CUSTOM && active in configured) add(active)
            addAll(freeFirstPriority.filter { it in configured && it !in this })
        }

        var last: AiCallResult.Failure? = null
        for (provider in ordered) {
            when (val result = OpenAiClient.getCompletionResult(messages, model, provider)) {
                is AiCallResult.Success -> {
                    ApiKeyManager.setActiveProvider(context, provider)
                    return result
                }
                is AiCallResult.Failure -> {
                    last = result
                    if (!shouldFailOver(result)) return result
                }
            }
        }
        return last ?: AiCallResult.Failure(AiConnectionState.ERROR, "No configured AI provider completed the request.")
    }

    internal fun shouldFailOver(failure: AiCallResult.Failure): Boolean =
        failure.state == AiConnectionState.OFFLINE ||
            failure.state == AiConnectionState.RATE_LIMITED ||
            (failure.httpCode?.let { it in 500..599 } == true)
}
