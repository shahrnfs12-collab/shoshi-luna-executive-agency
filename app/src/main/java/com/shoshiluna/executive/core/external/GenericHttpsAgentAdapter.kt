package com.shoshiluna.executive.core.external

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GenericHttpsAgentAdapter(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build(),
    private val bearerTokenProvider: suspend (ExternalAgentDescriptor) -> String? = { null }
) : ExternalAgentAdapter {
    override val protocol: ExternalProtocol = ExternalProtocol.GENERIC_HTTPS_JSON
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    override suspend fun handshake(agent: ExternalAgentDescriptor): Result<ExternalHandshakeResult> = runCatching {
        EndpointPolicy.validate(agent.endpoint).getOrThrow()
        val payload = JSONObject()
            .put("type", "handshake")
            .put("sender", "SHOSHI_LUNA_EXECUTIVE_AGENCY")
            .put("requestedProtocol", "1.0")
        val responseText = execute(agent, payload)
        val json = JSONObject(responseText)
        ExternalHandshakeResult(
            accepted = json.optBoolean("accepted", false),
            remoteIdentity = json.optString("identity", agent.displayName),
            protocolVersion = json.optString("protocolVersion", null),
            capabilities = json.optJSONArray("capabilities")?.let { arr -> (0 until arr.length()).map { arr.getString(it) }.toSet() } ?: emptySet(),
            proof = json.optString("proof", null)
        )
    }

    override suspend fun send(agent: ExternalAgentDescriptor, message: ExternalAgentMessage): Result<String> = runCatching {
        EndpointPolicy.validate(agent.endpoint).getOrThrow()
        val payload = JSONObject()
            .put("type", "message")
            .put("messageId", message.id)
            .put("missionId", message.missionId)
            .put("correlationId", message.correlationId)
            .put("senderAgentId", message.senderAgentId)
            .put("purpose", message.purpose)
            .put("body", message.body)
            .put("idempotencyKey", message.idempotencyKey)
        execute(agent, payload)
    }

    private suspend fun execute(agent: ExternalAgentDescriptor, payload: JSONObject): String = withContext(Dispatchers.IO) {
        val builder = Request.Builder()
            .url(agent.endpoint)
            .post(payload.toString().toRequestBody(jsonMediaType))
            .header("Accept", "application/json")
            .header("X-ShoshiLuna-Agent", "executive-agency")
        bearerTokenProvider(agent)?.takeIf { it.isNotBlank() }?.let { builder.header("Authorization", "Bearer $it") }
        client.newCall(builder.build()).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("HTTP ${response.code}: ${body.take(300)}")
            require(body.isNotBlank()) { "Empty external-agent response" }
            body
        }
    }
}
