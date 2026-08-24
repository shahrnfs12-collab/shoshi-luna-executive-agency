package com.shoshiluna.executive.core.network

import android.content.Context
import com.shoshiluna.executive.core.security.ApiKeyManager

/** Bounded failover across keys already configured by the owner. */
object ResilientAiClient {
    suspend fun getCompletionResult(
        context: Context,
        messages: List<ChatMessage>,
        model: String
    ): AiCallResult {
        val configured = ApiKeyManager.configuredProviders(context)
        if (configured.isEmpty()) return AiCallResult.Failure(AiConnectionState.API_KEY_MISSING, "No AI provider key is configured.")
        val active = ApiKeyManager.activeProvider(context)
        val ordered = buildList {
            if (active != null && active in configured) add(active)
            addAll(configured.filterNot { it == active })
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
                    if (!canFailOver(result)) return result
                }
            }
        }
        return last ?: AiCallResult.Failure(AiConnectionState.ERROR, "No configured AI provider completed the request.")
    }

    private fun canFailOver(failure: AiCallResult.Failure): Boolean =
        failure.state == AiConnectionState.OFFLINE ||
            failure.state == AiConnectionState.RATE_LIMITED ||
            (failure.httpCode?.let { it in 500..599 } == true)
}
