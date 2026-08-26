package com.shoshiluna.executive.core.external

import com.shoshiluna.executive.core.engine.TitanEngine
import com.shoshiluna.executive.core.network.AiProviderId
import com.shoshiluna.executive.core.security.ApiKeyManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * CODEX-1 remote runtime hosted by SHOSHI/LUNA. The server performs an independent
 * identity handshake and proxies the owner's configured OpenRouter route over HTTPS.
 * It is not represented as the current ChatGPT conversation or an OpenAI account session.
 */
class OpenAiResponsesAgentAdapter : ExternalAgentAdapter {
    override val protocol = ExternalProtocol.OPENROUTER_CHAT

    override suspend fun handshake(agent: ExternalAgentDescriptor): Result<ExternalHandshakeResult> = runCatching {
        require(agent.id == TrustedExternalAgentRegistry.CODEX_ONE_ID) { "Unknown CODEX-1 agent identity" }
        val payload = post(agent.endpoint, JSONObject().put("action", "handshake"), null)
        require(payload.optBoolean("accepted") && payload.optString("agent_id") == TrustedExternalAgentRegistry.CODEX_ONE_ID) {
            "CODEX-1 server identity handshake was not confirmed"
        }
        ExternalHandshakeResult(
            accepted = true,
            remoteIdentity = payload.optString("display_name", "CODEX-1 Cloud Agent"),
            protocolVersion = payload.optString("protocol", "shoshi-agent-v1"),
            capabilities = agent.capabilities,
            proof = payload.optString("evidence_id")
        )
    }

    override suspend fun send(agent: ExternalAgentDescriptor, message: ExternalAgentMessage): Result<String> = runCatching {
        require(agent.id == TrustedExternalAgentRegistry.CODEX_ONE_ID) { "Unknown CODEX-1 agent identity" }
        val context = requireNotNull(TitanEngine.applicationContext()) { "AI runtime is not initialized" }
        val key = requireNotNull(ApiKeyManager.getProviderKey(context, AiProviderId.OPENROUTER)) {
            "OpenRouter API key is not configured"
        }
        val system = """You are CODEX-1 Cloud Agent, Chief Technology & Execution Agent of SHOSHI/LUNA Executive Agency.
                |Own research, coding plans, debugging, build/test guidance, security review, evidence verification,
                |tool design, cost-benefit analysis, opportunity discovery, technical writing and agent orchestration.
                |Work naturally in Hebrew or English. Coordinate with SHOSHI on strategy and LUNA on operations.
                |Prefer free or low-cost routes when quality is sufficient, but never trade away truth or security.
                |Return concise evidence-aware work, never claim unperformed actions, and never bypass human approval,
                |security, external-communication, credential or financial gates.
                |You are a remote service using OpenRouter, not the current ChatGPT conversation.
            """.trimMargin()
        val payload = JSONObject()
            .put("action", "task")
            .put("mission_id", message.missionId)
            .put("purpose", message.purpose)
            .put("system", system)
            .put("task", message.body)
        post(agent.endpoint, payload, key).getString("text")
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(100, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(110, TimeUnit.SECONDS)
        .build()

    private suspend fun post(endpoint: String, payload: JSONObject, apiKey: String?): JSONObject = withContext(Dispatchers.IO) {
        val builder = Request.Builder()
            .url(endpoint)
            .header("Accept", "application/json")
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
        if (!apiKey.isNullOrBlank()) builder.header("Authorization", "Bearer $apiKey")
        client.newCall(builder.build()).execute().use { response ->
            val body = response.body?.string().orEmpty()
            require(response.isSuccessful) { "CODEX-1 cloud request failed (HTTP ${response.code})" }
            JSONObject(body)
        }
    }
}
