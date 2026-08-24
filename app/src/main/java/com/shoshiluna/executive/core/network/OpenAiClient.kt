package com.shoshiluna.executive.core.network

import android.content.Context
import com.google.gson.Gson
import com.shoshiluna.executive.core.health.EngineStatus
import com.shoshiluna.executive.core.health.SystemHealthManager
import com.shoshiluna.executive.core.hardening.CapabilityRegistry
import com.shoshiluna.executive.core.hardening.CapabilityStatus
import com.shoshiluna.executive.core.security.ApiKeyManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

data class ChatMessage(val role: String, val content: String)
data class ResponsesRequest(val model: String, val instructions: String?, val input: String)

enum class AiConnectionState {
    IDLE,
    CONNECTING,
    CONNECTED,
    API_KEY_MISSING,
    OFFLINE,
    RATE_LIMITED,
    ERROR
}

sealed interface AiCallResult {
    data class Success(
        val text: String,
        val provider: AiProviderId? = null,
        val taskProfile: AiTaskProfile? = null,
        val model: String? = null
    ) : AiCallResult
    data class Failure(val state: AiConnectionState, val safeMessage: String, val httpCode: Int? = null) : AiCallResult
}

/**
 * Network-only OpenAI client. It never fabricates a model response.
 * Failure paths are represented explicitly so the UI cannot confuse an error with AI output.
 */
object OpenAiClient {
    private const val DEFAULT_MODEL = "gpt-5.6-luna"
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()
    private val mediaType = "application/json; charset=utf-8".toMediaType()

    @Volatile private var appContext: Context? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    suspend fun getCompletionResult(
        messages: List<ChatMessage>,
        model: String = DEFAULT_MODEL,
        providerOverride: AiProviderId? = null
    ): AiCallResult = withContext(Dispatchers.IO) {
        val context = appContext ?: return@withContext fail(
            AiConnectionState.OFFLINE,
            "AI runtime is not initialized."
        )

        val provider = providerOverride ?: ApiKeyManager.activeProvider(context) ?: AiProviderId.OPENAI
        val apiKey = ApiKeyManager.getProviderKey(context, provider)
        if (apiKey.isNullOrBlank()) {
            return@withContext fail(AiConnectionState.API_KEY_MISSING, "${provider.name} API key is not configured.")
        }

        SystemHealthManager.updateHealth("AI_PROVIDER", EngineStatus.INITIALIZING, "Connecting")
        val instructions = messages.filter { it.role == "system" }.joinToString("\n\n") { it.content }.ifBlank { null }
        val input = messages.filter { it.role != "system" }.joinToString("\n\n") { message ->
            if (message.role == "user") message.content else "${message.role}: ${message.content}"
        }
        val protocol = if (provider == AiProviderId.CUSTOM) ApiKeyManager.customProtocol(context) else AiProviderProtocols.forProvider(provider)
        val effectiveModel = if (provider == AiProviderId.OPENAI) model else protocol.defaultModel
        val requestBody = AiProviderPayloadCodec.encode(provider, effectiveModel, instructions, input).toRequestBody(mediaType)
        val requestBuilder = Request.Builder()
            .url(protocol.endpoint)
            .header(protocol.authenticationHeader, protocol.authenticationPrefix + apiKey)
            .header("Content-Type", "application/json")
        if (provider == AiProviderId.ANTHROPIC) requestBuilder.header("anthropic-version", "2023-06-01")
        val request = requestBuilder.post(requestBody).build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val state = when (response.code) {
                        429 -> AiConnectionState.RATE_LIMITED
                        401, 403 -> AiConnectionState.ERROR
                        in 500..599 -> AiConnectionState.ERROR
                        else -> AiConnectionState.ERROR
                    }
                    return@withContext fail(state, safeHttpMessage(provider, response.code), response.code)
                }

                val body = response.body?.string().orEmpty()
                if (body.isBlank()) {
                    return@withContext fail(AiConnectionState.ERROR, "AI provider returned an empty HTTP body.")
                }

                val text = AiProviderPayloadCodec.decodeText(provider, body)
                if (text.isBlank()) {
                    return@withContext fail(AiConnectionState.ERROR, "AI provider returned no usable message content.")
                }

                SystemHealthManager.updateHealth("AI_PROVIDER", EngineStatus.RUNNING, "Validated provider response")
                CapabilityRegistry.update("ai", CapabilityStatus.READY, "Real provider response validated at runtime")
                val root = runCatching { gson.fromJson(body, com.google.gson.JsonObject::class.java) }.getOrNull()
                val reportedModel = listOf("model", "modelVersion").firstNotNullOfOrNull { field ->
                    root?.get(field)?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() }
                } ?: effectiveModel
                AiCallResult.Success(text, provider = provider, model = reportedModel)
            }
        } catch (_: SocketTimeoutException) {
            fail(AiConnectionState.OFFLINE, "AI request timed out.")
        } catch (_: IOException) {
            fail(AiConnectionState.OFFLINE, "AI network connection failed.")
        } catch (_: Exception) {
            fail(AiConnectionState.ERROR, "AI request failed.")
        }
    }

    private fun extractResponseText(body: String): String {
        val root = runCatching { gson.fromJson(body, com.google.gson.JsonObject::class.java) }.getOrNull() ?: return ""
        root.get("output_text")?.takeIf { it.isJsonPrimitive }?.asString?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
        val output = root.getAsJsonArray("output") ?: return ""
        val pieces = mutableListOf<String>()
        output.forEach { itemEl ->
            val item = itemEl.takeIf { it.isJsonObject }?.asJsonObject ?: return@forEach
            val content = item.getAsJsonArray("content") ?: return@forEach
            content.forEach { contentEl ->
                val contentObj = contentEl.takeIf { it.isJsonObject }?.asJsonObject ?: return@forEach
                val type = contentObj.get("type")?.asString
                if (type == "output_text" || type == "text") {
                    contentObj.get("text")?.asString?.trim()?.takeIf { it.isNotBlank() }?.let(pieces::add)
                }
            }
        }
        return pieces.joinToString("\n").trim()
    }

    private fun fail(state: AiConnectionState, message: String, code: Int? = null): AiCallResult.Failure {
        val health = when (state) {
            AiConnectionState.API_KEY_MISSING, AiConnectionState.OFFLINE -> EngineStatus.OFFLINE
            AiConnectionState.CONNECTING -> EngineStatus.INITIALIZING
            AiConnectionState.CONNECTED, AiConnectionState.IDLE -> EngineStatus.RUNNING
            AiConnectionState.RATE_LIMITED, AiConnectionState.ERROR -> EngineStatus.ERROR
        }
        SystemHealthManager.updateHealth("AI_PROVIDER", health, message)
        val capability = when (state) {
            AiConnectionState.API_KEY_MISSING -> CapabilityStatus.LOCKED
            AiConnectionState.OFFLINE, AiConnectionState.RATE_LIMITED, AiConnectionState.ERROR -> CapabilityStatus.DEGRADED
            else -> CapabilityStatus.LOCKED
        }
        CapabilityRegistry.update("ai", capability, message)
        return AiCallResult.Failure(state, message, code)
    }

    private fun safeHttpMessage(provider: AiProviderId, code: Int): String = when (code) {
        401 -> "${provider.name} rejected the configured API key (HTTP 401)."
        402 -> "${provider.name} requires active billing or credits (HTTP 402)."
        403 -> "${provider.name} denied this request (HTTP 403)."
        429 -> "${provider.name} rate limit or quota reached (HTTP 429)."
        in 500..599 -> "${provider.name} service error (HTTP $code)."
        else -> "${provider.name} request failed (HTTP $code)."
    }
}
