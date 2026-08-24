package com.shoshiluna.executive.core.external

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

object TrustedExternalAgentRegistry {
    private val _agents = MutableStateFlow<Map<String, ExternalAgentDescriptor>>(emptyMap())
    val agents = _agents.asStateFlow()
    @Volatile private var context: Context? = null
    @Volatile private var testMode = true

    @Synchronized
    fun initialize(context: Context) {
        this.context = context.applicationContext
        testMode = false
        _agents.value = decode(prefs().getString(KEY_AGENTS, null))
    }

    @Synchronized
    fun registerCandidate(descriptor: ExternalAgentDescriptor): Result<ExternalAgentDescriptor> {
        require(descriptor.displayName.isNotBlank())
        EndpointPolicy.validate(descriptor.endpoint).getOrThrow()
        require(_agents.value.values.none { it.endpoint == descriptor.endpoint && it.id != descriptor.id }) { "Endpoint already registered" }
        val pending = descriptor.copy(trustStatus = ExternalTrustStatus.PENDING)
        _agents.value = _agents.value + (pending.id to pending)
        persist()
        return Result.success(pending)
    }

    @Synchronized
    fun markTrusted(agentId: String, fingerprint: String?): ExternalAgentDescriptor {
        val current = _agents.value[agentId] ?: error("Unknown external agent")
        require(current.trustStatus != ExternalTrustStatus.REVOKED) { "Revoked agent cannot be trusted" }
        val verifiedFingerprint = fingerprint?.trim().takeUnless { it.isNullOrBlank() }
            ?: error("Verified handshake proof is required before trust")
        val updated = current.copy(
            trustStatus = ExternalTrustStatus.TRUSTED,
            publicKeyFingerprint = verifiedFingerprint
        )
        _agents.value = _agents.value + (agentId to updated)
        persist()
        return updated
    }

    @Synchronized fun suspend(agentId: String) = updateStatus(agentId, ExternalTrustStatus.SUSPENDED)
    @Synchronized fun revoke(agentId: String) = updateStatus(agentId, ExternalTrustStatus.REVOKED)
    fun get(agentId: String): ExternalAgentDescriptor? = _agents.value[agentId]
    fun trusted(): List<ExternalAgentDescriptor> = _agents.value.values.filter { it.trustStatus == ExternalTrustStatus.TRUSTED }

    private fun updateStatus(agentId: String, status: ExternalTrustStatus) {
        val current = _agents.value[agentId] ?: error("Unknown external agent")
        _agents.value = _agents.value + (agentId to current.copy(trustStatus = status))
        persist()
    }

    @Synchronized fun resetForTest() { _agents.value = emptyMap(); testMode = true }

    private fun persist() {
        if (testMode) return
        val array = JSONArray()
        _agents.value.values.forEach { agent ->
            array.put(JSONObject()
                .put("id", agent.id)
                .put("displayName", agent.displayName)
                .put("endpoint", agent.endpoint)
                .put("protocol", agent.protocol.name)
                .put("trustStatus", agent.trustStatus.name)
                .put("publicKeyFingerprint", agent.publicKeyFingerprint.orEmpty())
                .put("capabilities", JSONArray(agent.capabilities.toList()))
                .put("createdAt", agent.createdAt))
        }
        prefs().edit().putString(KEY_AGENTS, array.toString()).apply()
    }

    private fun decode(raw: String?): Map<String, ExternalAgentDescriptor> = runCatching {
        val array = JSONArray(raw ?: "[]")
        (0 until array.length()).map { array.getJSONObject(it) }.associate { json ->
            val capabilities = json.optJSONArray("capabilities")?.let { values ->
                (0 until values.length()).map { values.getString(it) }.toSet()
            } ?: emptySet()
            val agent = ExternalAgentDescriptor(
                id = json.getString("id"),
                displayName = json.getString("displayName"),
                endpoint = json.getString("endpoint"),
                protocol = ExternalProtocol.valueOf(json.getString("protocol")),
                trustStatus = ExternalTrustStatus.valueOf(json.getString("trustStatus")),
                publicKeyFingerprint = json.optString("publicKeyFingerprint").takeIf { it.isNotBlank() },
                capabilities = capabilities,
                createdAt = json.optLong("createdAt", System.currentTimeMillis())
            )
            agent.id to agent
        }
    }.getOrDefault(emptyMap())

    private fun prefs() = EncryptedSharedPreferences.create(
        requireNotNull(context) { "TrustedExternalAgentRegistry not initialized" },
        "trusted_external_agent_registry",
        MasterKey.Builder(requireNotNull(context)).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private const val KEY_AGENTS = "agents"
}
