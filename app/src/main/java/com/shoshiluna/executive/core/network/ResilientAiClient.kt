package com.shoshiluna.executive.core.network

import android.content.Context
import com.shoshiluna.executive.core.fabric.FabricTaskClassifier
import com.shoshiluna.executive.core.fabric.MetaRouter
import com.shoshiluna.executive.core.fabric.ModelRegistry
import com.shoshiluna.executive.core.fabric.RegistryStatus
import com.shoshiluna.executive.core.security.ApiKeyManager

/** Bounded failover across keys already configured by the owner. */
object ResilientAiClient {
    suspend fun getCompletionResult(
        context: Context,
        messages: List<ChatMessage>,
        model: String,
        images: List<AiImageInput> = emptyList(),
        taskProfile: AiTaskProfile = AiTaskRouter.classify(messages.lastOrNull { it.role == "user" }?.content.orEmpty())
    ): AiCallResult {
        val configured = ApiKeyManager.configuredProviders(context)
        if (configured.isEmpty()) return AiCallResult.Failure(AiConnectionState.API_KEY_MISSING, "No AI provider key is configured.")
        val providerIds = configured.map { it.fabricId }.toSet()
        ModelRegistry.all().filter { it.providerId in providerIds && it.status in setOf(RegistryStatus.DISCOVERED, RegistryStatus.DOCUMENTED) }
            .forEach { ModelRegistry.transition(it.id, RegistryStatus.CONFIGURED) }
        val prompt = messages.lastOrNull { it.role == "user" }?.content.orEmpty()
        val fabricTask = FabricTaskClassifier.classify(prompt, hasImage = images.isNotEmpty(), hasFile = prompt.contains("[Attachment:"))
        val routed = MetaRouter.route(fabricTask, providerIds).getOrNull()
        val modelChoices = routed?.let { listOf(it.primary) + it.fallbacks }
            ?.mapNotNull { candidate ->
                val provider = configured.firstOrNull { it.fabricId == candidate.model.providerId } ?: return@mapNotNull null
                provider to candidate.model.id.substringAfter('/')
            }.orEmpty()
        val active = ApiKeyManager.activeProvider(context)
        val legacyChoices = AiTaskRouter.orderedProviders(configured, active, taskProfile).map { it to null }
        val ordered = (modelChoices + legacyChoices).distinctBy { it.first }
        var last: AiCallResult.Failure? = null
        for ((provider, resolvedModel) in ordered) {
            when (val result = OpenAiClient.getCompletionResult(messages, model, provider, images, resolvedModel)) {
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

    private val AiProviderId.fabricId: String get() = when (this) {
        AiProviderId.OPENAI -> "openai"
        AiProviderId.ANTHROPIC -> "anthropic"
        AiProviderId.GOOGLE_GEMINI -> "google_gemini"
        AiProviderId.OPENROUTER -> "openrouter"
        AiProviderId.CUSTOM -> "custom"
    }
}
