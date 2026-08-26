package com.shoshiluna.executive.core.network

import android.content.Context
import com.shoshiluna.executive.core.security.ApiKeyManager

/**
 * Bounded failover across owner-configured provider/key pools.
 * Key rotation is used only for key-specific auth/billing/permission failures.
 * Rate-limit, timeout and provider 5xx failures fail over to another provider instead of
 * cycling keys to bypass a service quota or rate-limit policy.
 */
object ResilientAiClient {
    private val freeFirstPriority = listOf(
        AiProviderId.OPENROUTER,
        AiProviderId.OPENROUTER_UNCENSORED,
        AiProviderId.GROQ,
        AiProviderId.GOOGLE_GEMINI,
        AiProviderId.ANTHROPIC,
        AiProviderId.OPENAI,
        AiProviderId.CUSTOM
    )
    private const val MAX_PROVIDER_ATTEMPTS = 4

    suspend fun getCompletionResult(
        context: Context,
        messages: List<ChatMessage>,
        model: String
    ): AiCallResult {
        val configured = ApiKeyManager.configuredProviders(context)
        if (configured.isEmpty()) return AiCallResult.Failure(AiConnectionState.API_KEY_MISSING, "No AI provider key is configured.")

        val active = ApiKeyManager.activeProvider(context)
        val ordered = buildList {
            if (active != null && active in setOf(AiProviderId.CUSTOM, AiProviderId.OPENROUTER_UNCENSORED) && active in configured) add(active)
            addAll(freeFirstPriority.filter { it in configured && it !in this })
        }

        var last: AiCallResult.Failure? = null
        val failures = mutableListOf<String>()
        var attemptedProviders = 0
        for (provider in ordered) {
            if (attemptedProviders >= MAX_PROVIDER_ATTEMPTS) break
            val keys = ApiKeyManager.getProviderKeys(context, provider)
            if (keys.isEmpty()) continue
            attemptedProviders += 1

            var providerFailure: AiCallResult.Failure? = null
            for ((index, key) in keys.withIndex()) {
                when (val result = OpenAiClient.getCompletionResult(messages, model, provider, key)) {
                    is AiCallResult.Success -> {
                        ApiKeyManager.setActiveProvider(context, provider)
                        repeat(index) { ApiKeyManager.advanceProviderKey(context, provider) }
                        return result
                    }
                    is AiCallResult.Failure -> {
                        last = result
                        providerFailure = result
                        if (shouldTryNextKey(result) && index < keys.lastIndex) {
                            ApiKeyManager.advanceProviderKey(context, provider)
                            continue
                        }
                        break
                    }
                }
            }

            val failure = providerFailure ?: continue
            failures += "${provider.name}: ${failure.safeMessage}"
            if (!shouldFailOverProvider(failure)) return failure
        }

        if (failures.isNotEmpty()) {
            val remaining = ordered.count { ApiKeyManager.getProviderKeys(context, it).isNotEmpty() } - attemptedProviders
            val suffix = if (remaining > 0) " ${remaining} additional configured route(s) will be reconsidered on the next request." else ""
            val state = last?.state ?: AiConnectionState.ERROR
            return AiCallResult.Failure(
                state,
                "AI provider route failed. ${failures.joinToString(" | ")}$suffix",
                last?.httpCode
            )
        }
        return last ?: AiCallResult.Failure(AiConnectionState.ERROR, "No configured AI provider completed the request.")
    }

    internal fun shouldTryNextKey(failure: AiCallResult.Failure): Boolean =
        failure.httpCode in setOf(401, 402, 403)

    internal fun shouldFailOverProvider(failure: AiCallResult.Failure): Boolean =
        failure.state == AiConnectionState.OFFLINE ||
            failure.state == AiConnectionState.RATE_LIMITED ||
            failure.httpCode in setOf(401, 402, 403) ||
            (failure.httpCode?.let { it in 500..599 } == true)

    // Backward-compatible name used by existing tests.
    internal fun shouldFailOver(failure: AiCallResult.Failure): Boolean = shouldFailOverProvider(failure)
}
