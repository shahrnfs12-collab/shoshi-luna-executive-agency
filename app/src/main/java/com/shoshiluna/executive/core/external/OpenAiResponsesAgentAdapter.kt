package com.shoshiluna.executive.core.external

import com.shoshiluna.executive.core.engine.TitanEngine
import com.shoshiluna.executive.core.network.AiCallResult
import com.shoshiluna.executive.core.network.AiProviderId
import com.shoshiluna.executive.core.network.ChatMessage
import com.shoshiluna.executive.core.network.OpenAiClient
import com.shoshiluna.executive.core.security.ApiKeyManager

/**
 * CODEX-1 community runtime over the owner's configured OpenRouter key.
 * This is deliberately not represented as an authenticated OpenAI/Codex cloud session.
 */
class OpenAiResponsesAgentAdapter : ExternalAgentAdapter {
    override val protocol = ExternalProtocol.OPENROUTER_CHAT

    override suspend fun handshake(agent: ExternalAgentDescriptor): Result<ExternalHandshakeResult> = runCatching {
        require(agent.id == TrustedExternalAgentRegistry.CODEX_ONE_ID) { "Unknown CODEX-1 agent identity" }
        val reply = call(
            system = "You are CODEX-1 Community Agent, the first recruited external worker of SHOSHI/LUNA Executive Agency. Reply only: CODEX-1 READY",
            input = "Perform identity handshake."
        )
        require(reply.trim().contains("CODEX-1 READY", ignoreCase = true)) {
            "CODEX-1 conversational handshake was not confirmed"
        }
        ExternalHandshakeResult(
            accepted = true,
            remoteIdentity = "CODEX-1 Community Agent",
            protocolVersion = "openrouter-chat-v1",
            capabilities = agent.capabilities,
            proof = "openrouter-live-conversation-verified"
        )
    }

    override suspend fun send(agent: ExternalAgentDescriptor, message: ExternalAgentMessage): Result<String> = runCatching {
        require(agent.id == TrustedExternalAgentRegistry.CODEX_ONE_ID) { "Unknown CODEX-1 agent identity" }
        call(
            system = """You are CODEX-1 Community Agent, Chief Technology & Execution Agent of SHOSHI/LUNA Executive Agency.
                |Own research, coding plans, debugging, build/test guidance, security review, evidence verification,
                |tool design, cost-benefit analysis, opportunity discovery, technical writing and agent orchestration.
                |Work naturally in Hebrew or English. Coordinate with SHOSHI on strategy and LUNA on operations.
                |Prefer free or low-cost routes when quality is sufficient, but never trade away truth or security.
                |Return concise evidence-aware work, never claim unperformed actions, and never bypass human approval,
                |security, external-communication, credential or financial gates.
                |You are running through OpenRouter, not an authenticated OpenAI/Codex cloud session.
            """.trimMargin(),
            input = "Mission: ${message.missionId}\nPurpose: ${message.purpose}\nTask: ${message.body}"
        )
    }

    private suspend fun call(system: String, input: String): String {
        val context = requireNotNull(TitanEngine.applicationContext()) { "AI runtime is not initialized" }
        val key = requireNotNull(ApiKeyManager.getProviderKey(context, AiProviderId.OPENROUTER)) {
            "OpenRouter API key is not configured"
        }
        return when (val result = OpenAiClient.getCompletionResult(
            messages = listOf(ChatMessage("system", system), ChatMessage("user", input)),
            providerOverride = AiProviderId.OPENROUTER,
            apiKeyOverride = key
        )) {
            is AiCallResult.Success -> result.text
            is AiCallResult.Failure -> error(result.safeMessage)
        }
    }
}
