package com.shoshiluna.executive.core.external

import com.shoshiluna.executive.core.engine.TitanEngine
import com.shoshiluna.executive.core.network.AiCallResult
import com.shoshiluna.executive.core.network.AiProviderId
import com.shoshiluna.executive.core.network.ChatMessage
import com.shoshiluna.executive.core.network.OpenAiClient
import com.shoshiluna.executive.core.security.ApiKeyManager

/** Real OpenAI Responses API boundary for the owner's CODEX-1 external worker. */
class OpenAiResponsesAgentAdapter : ExternalAgentAdapter {
    override val protocol = ExternalProtocol.OPENAI_RESPONSES

    override suspend fun handshake(agent: ExternalAgentDescriptor): Result<ExternalHandshakeResult> = runCatching {
        require(agent.id == TrustedExternalAgentRegistry.CODEX_ONE_ID) { "Unknown OpenAI agent identity" }
        val reply = call(
            system = "You are CODEX-1, the first recruited external worker of SHOSHI/LUNA Executive Agency. Reply only: CODEX-1 READY",
            input = "Perform identity handshake."
        )
        require(reply.contains("CODEX-1", ignoreCase = true)) { "OpenAI identity handshake was not confirmed" }
        ExternalHandshakeResult(
            accepted = true,
            remoteIdentity = "CODEX-1",
            protocolVersion = "openai-responses-v1",
            capabilities = agent.capabilities,
            proof = "openai-runtime-verified"
        )
    }

    override suspend fun send(agent: ExternalAgentDescriptor, message: ExternalAgentMessage): Result<String> = runCatching {
        require(agent.id == TrustedExternalAgentRegistry.CODEX_ONE_ID) { "Unknown OpenAI agent identity" }
        call(
            system = "You are CODEX-1, an external executive worker serving SHOSHI/LUNA. Follow the mission purpose, return concise evidence-aware work, never claim unperformed actions, and never bypass approval, security, or financial gates.",
            input = "Mission: ${message.missionId}\nPurpose: ${message.purpose}\nTask: ${message.body}"
        )
    }

    private suspend fun call(system: String, input: String): String {
        val context = requireNotNull(TitanEngine.applicationContext()) { "AI runtime is not initialized" }
        val key = requireNotNull(ApiKeyManager.getProviderKey(context, AiProviderId.OPENAI)) {
            "OpenAI API key is not configured"
        }
        return when (val result = OpenAiClient.getCompletionResult(
            messages = listOf(ChatMessage("system", system), ChatMessage("user", input)),
            model = "gpt-5.6-luna",
            providerOverride = AiProviderId.OPENAI,
            apiKeyOverride = key
        )) {
            is AiCallResult.Success -> result.text
            is AiCallResult.Failure -> error(result.safeMessage)
        }
    }
}
