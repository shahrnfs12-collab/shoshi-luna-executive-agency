package com.shoshiluna.executive.core.network

import android.content.Context
import com.shoshiluna.executive.core.security.ApiKeyManager

/** Bounded failover across keys already configured by the owner. */
object ResilientAiClient {
    suspend fun getCompletionResult(
        context: Context,
        messages: List<ChatMessage>,
        model: String,
        taskProfile: AiTaskProfile = AiTaskRouter.classify(messages.lastOrNull { it.role == "user" }?.content.orEmpty())
    ): AiCallResult {
        val configured = ApiKeyManager.configuredProviders(context)
        if (configured.isEmpty()) return AiCallResult.Failure(AiConnectionState.API_KEY_MISSING, "No AI provider key is configured.")
        val active = ApiKeyManager.activeProvider(context)
        val ordered = AiTaskRouter.orderedProviders(configured, active, taskProfile)
        var last: AiCallResult.Failure? = null
        for (provider in ordered) {
            when (val result = OpenAiClient.getCompletionResult(messages, model, provider)) {
                is AiCallResult.Success -> {
                    ApiKeyManager.setActiveProvider(context, provider)
                    return result.copy(provider = provider, taskProfile = taskProfile)
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
