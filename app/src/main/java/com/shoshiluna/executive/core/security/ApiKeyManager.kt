package com.shoshiluna.executive.core.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.shoshiluna.executive.core.network.AiProviderId
import com.shoshiluna.executive.core.network.AiProviderRegistry
import java.security.MessageDigest

/** ShoshiLuna Secret Store. Encrypted at rest. No financial secrets allowed. */
object ApiKeyManager {
    private const val PREFS_NAME = "shoshiluna_secure_keys"
    private const val KEY_OPENAI_API = "OPENAI_API_KEY"
    private const val KEY_ACTIVE_PROVIDER = "ACTIVE_AI_PROVIDER"
    private const val KEY_PROVIDER_PREFIX = "AI_PROVIDER_KEY_"
    private const val KEY_PROVIDER_POOL_PREFIX = "AI_PROVIDER_KEY_POOL_"
    private const val KEY_PROVIDER_CURSOR_PREFIX = "AI_PROVIDER_KEY_CURSOR_"
    private const val KEY_PROVIDER_DISABLED_PREFIX = "AI_PROVIDER_KEY_DISABLED_"
    private const val KEY_CUSTOM_ENDPOINT = "CUSTOM_ENDPOINT"
    private const val KEY_CUSTOM_MODEL = "CUSTOM_MODEL"
    private const val KEY_CUSTOM_AUTH_HEADER = "CUSTOM_AUTH_HEADER"
    private const val KEY_CUSTOM_AUTH_PREFIX = "CUSTOM_AUTH_PREFIX"

    @Volatile private var cachedPrefs: SharedPreferences? = null

    fun saveOpenAiKey(context: Context, apiKey: String) = saveProviderKey(context, AiProviderId.OPENAI, apiKey)
    fun getOpenAiKey(context: Context): String? = getProviderKey(context, AiProviderId.OPENAI)
    fun hasOpenAiKey(context: Context): Boolean = getOpenAiKey(context) != null
    fun clearOpenAiKey(context: Context) = clearProviderKey(context, AiProviderId.OPENAI)

    fun saveProviderKey(context: Context, provider: AiProviderId, apiKey: String) {
        require(provider != AiProviderId.CUSTOM) { "Use saveCustomProvider for a custom provider" }
        val normalized = AiProviderRegistry.normalizeSecret(apiKey)
        require(normalized.isNotBlank()) { "API key cannot be blank" }
        val credentialProvider = credentialProvider(provider)
        val pool = getProviderKeys(context, credentialProvider).toMutableList()
        if (normalized !in pool) pool.add(normalized)
        prefs(context).edit()
            .putString(providerKey(credentialProvider), normalized)
            .putString(providerPoolKey(credentialProvider), encodePool(pool))
            .putString(KEY_ACTIVE_PROVIDER, provider.name)
            .also { editor -> if (credentialProvider == AiProviderId.OPENAI) editor.putString(KEY_OPENAI_API, normalized) }
            .apply()
        setKeyEnabled(context, credentialProvider, normalized, true)
    }

    fun addProviderKey(context: Context, provider: AiProviderId, apiKey: String) {
        require(provider != AiProviderId.CUSTOM) { "Use saveCustomProvider for a custom provider" }
        val normalized = AiProviderRegistry.normalizeSecret(apiKey)
        require(normalized.isNotBlank()) { "API key cannot be blank" }
        val credentialProvider = credentialProvider(provider)
        val pool = getProviderKeys(context, credentialProvider).toMutableList()
        if (normalized !in pool) pool.add(normalized)
        prefs(context).edit().putString(providerPoolKey(credentialProvider), encodePool(pool)).apply()
        setKeyEnabled(context, credentialProvider, normalized, true)
    }

    fun getProviderKeys(context: Context, provider: AiProviderId): List<String> {
        val credentialProvider = credentialProvider(provider)
        val encoded = prefs(context).getString(providerPoolKey(credentialProvider), null)
        val pooled = decodePool(encoded)
        if (pooled.isNotEmpty()) return pooled
        val legacy = prefs(context).getString(providerKey(credentialProvider), null)
            ?: if (credentialProvider == AiProviderId.OPENAI) prefs(context).getString(KEY_OPENAI_API, null) else null
        return listOfNotNull(legacy?.takeIf { it.isNotBlank() })
    }

    fun getEnabledProviderKeys(context: Context, provider: AiProviderId): List<String> =
        getProviderKeys(context, provider).filter { isKeyEnabled(context, provider, it) }

    fun getProviderKey(context: Context, provider: AiProviderId): String? {
        val keys = getEnabledProviderKeys(context, provider)
        if (keys.isEmpty()) return null
        val credentialProvider = credentialProvider(provider)
        val cursor = prefs(context).getInt(providerCursorKey(credentialProvider), 0)
        return keys[cursor.mod(keys.size)]
    }

    fun advanceProviderKey(context: Context, provider: AiProviderId) {
        val credentialProvider = credentialProvider(provider)
        val keys = getEnabledProviderKeys(context, credentialProvider)
        if (keys.size <= 1) return
        val p = prefs(context)
        val next = (p.getInt(providerCursorKey(credentialProvider), 0) + 1).mod(keys.size)
        p.edit().putInt(providerCursorKey(credentialProvider), next).apply()
    }

    fun setKeyEnabled(context: Context, provider: AiProviderId, apiKey: String, enabled: Boolean) {
        val credentialProvider = credentialProvider(provider)
        val fingerprint = fingerprint(AiProviderRegistry.normalizeSecret(apiKey))
        val disabled = disabledFingerprints(context, credentialProvider).toMutableSet()
        if (enabled) disabled.remove(fingerprint) else disabled.add(fingerprint)
        prefs(context).edit().putString(disabledKey(credentialProvider), disabled.joinToString("\n")).apply()
    }

    fun isKeyEnabled(context: Context, provider: AiProviderId, apiKey: String): Boolean {
        val credentialProvider = credentialProvider(provider)
        return fingerprint(AiProviderRegistry.normalizeSecret(apiKey)) !in disabledFingerprints(context, credentialProvider)
    }

    fun removeProviderKey(context: Context, provider: AiProviderId, apiKey: String) {
        val credentialProvider = credentialProvider(provider)
        val normalized = AiProviderRegistry.normalizeSecret(apiKey)
        val remaining = getProviderKeys(context, credentialProvider).filterNot { it == normalized }
        val editor = prefs(context).edit()
            .putString(providerPoolKey(credentialProvider), encodePool(remaining))
            .putInt(providerCursorKey(credentialProvider), 0)
        if (remaining.isEmpty()) {
            editor.remove(providerKey(credentialProvider)).remove(providerPoolKey(credentialProvider))
            if (credentialProvider == AiProviderId.OPENAI) editor.remove(KEY_OPENAI_API)
        } else {
            editor.putString(providerKey(credentialProvider), remaining.first())
            if (credentialProvider == AiProviderId.OPENAI) editor.putString(KEY_OPENAI_API, remaining.first())
        }
        editor.apply()
        setKeyEnabled(context, credentialProvider, normalized, true)
    }

    fun maskedKey(apiKey: String): String {
        val normalized = AiProviderRegistry.normalizeSecret(apiKey)
        if (normalized.length <= 10) return "••••${normalized.takeLast(4)}"
        return "${normalized.take(6)}••••${normalized.takeLast(4)}"
    }

    fun hasProviderKey(context: Context, provider: AiProviderId): Boolean = getEnabledProviderKeys(context, provider).isNotEmpty()

    fun clearProviderKey(context: Context, provider: AiProviderId) {
        val credentialProvider = credentialProvider(provider)
        val editor = prefs(context).edit()
            .remove(providerKey(credentialProvider))
            .remove(providerPoolKey(credentialProvider))
            .remove(providerCursorKey(credentialProvider))
            .remove(disabledKey(credentialProvider))
        if (credentialProvider == AiProviderId.OPENAI) editor.remove(KEY_OPENAI_API)
        if (provider == AiProviderId.CUSTOM) editor.remove(KEY_CUSTOM_ENDPOINT).remove(KEY_CUSTOM_MODEL).remove(KEY_CUSTOM_AUTH_HEADER).remove(KEY_CUSTOM_AUTH_PREFIX)
        val active = activeProvider(context)
        if (active == provider || (credentialProvider == AiProviderId.OPENROUTER && active == AiProviderId.OPENROUTER_UNCENSORED)) editor.remove(KEY_ACTIVE_PROVIDER)
        editor.apply()
    }

    fun configuredProviders(context: Context): Set<AiProviderId> = AiProviderId.entries.filter { hasProviderKey(context, it) }.toSet()

    fun saveCustomProvider(context: Context, apiKey: String, endpoint: String, model: String, authHeader: String = "Authorization", authPrefix: String = "Bearer ") {
        com.shoshiluna.executive.core.network.AiProviderProtocols.custom(endpoint, model, authHeader, authPrefix)
        val normalized = AiProviderRegistry.normalizeSecret(apiKey)
        require(normalized.isNotBlank())
        prefs(context).edit().putString(providerKey(AiProviderId.CUSTOM), normalized)
            .putString(providerPoolKey(AiProviderId.CUSTOM), encodePool(listOf(normalized)))
            .putString(KEY_CUSTOM_ENDPOINT, endpoint.trim()).putString(KEY_CUSTOM_MODEL, model.trim())
            .putString(KEY_CUSTOM_AUTH_HEADER, authHeader).putString(KEY_CUSTOM_AUTH_PREFIX, authPrefix)
            .putString(KEY_ACTIVE_PROVIDER, AiProviderId.CUSTOM.name).apply()
    }

    fun customProtocol(context: Context) = com.shoshiluna.executive.core.network.AiProviderProtocols.custom(
        requireNotNull(prefs(context).getString(KEY_CUSTOM_ENDPOINT, null)),
        requireNotNull(prefs(context).getString(KEY_CUSTOM_MODEL, null)),
        prefs(context).getString(KEY_CUSTOM_AUTH_HEADER, "Authorization") ?: "Authorization",
        prefs(context).getString(KEY_CUSTOM_AUTH_PREFIX, "Bearer ") ?: "Bearer "
    )

    fun activeProvider(context: Context): AiProviderId? = prefs(context).getString(KEY_ACTIVE_PROVIDER, null)?.let { runCatching { AiProviderId.valueOf(it) }.getOrNull() }

    fun setActiveProvider(context: Context, provider: AiProviderId) {
        require(hasProviderKey(context, provider)) { "Provider has no enabled key" }
        prefs(context).edit().putString(KEY_ACTIVE_PROVIDER, provider.name).apply()
    }

    private fun credentialProvider(provider: AiProviderId): AiProviderId = when (provider) {
        AiProviderId.OPENROUTER_UNCENSORED -> AiProviderId.OPENROUTER
        else -> provider
    }

    private fun providerKey(provider: AiProviderId): String = "$KEY_PROVIDER_PREFIX${provider.name}"
    private fun providerPoolKey(provider: AiProviderId): String = "$KEY_PROVIDER_POOL_PREFIX${provider.name}"
    private fun providerCursorKey(provider: AiProviderId): String = "$KEY_PROVIDER_CURSOR_PREFIX${provider.name}"
    private fun disabledKey(provider: AiProviderId): String = "$KEY_PROVIDER_DISABLED_PREFIX${provider.name}"
    private fun encodePool(keys: List<String>): String = keys.distinct().joinToString("\n")
    private fun decodePool(value: String?): List<String> = value.orEmpty().lineSequence().map { it.trim() }.filter { it.isNotBlank() }.distinct().toList()
    private fun disabledFingerprints(context: Context, provider: AiProviderId): Set<String> =
        prefs(context).getString(disabledKey(provider), null).orEmpty().lineSequence().map { it.trim() }.filter { it.isNotBlank() }.toSet()
    private fun fingerprint(secret: String): String = MessageDigest.getInstance("SHA-256").digest(secret.toByteArray())
        .take(8).joinToString("") { "%02x".format(it) }

    private fun prefs(context: Context): SharedPreferences {
        cachedPrefs?.let { return it }
        return synchronized(this) {
            cachedPrefs ?: EncryptedSharedPreferences.create(
                context.applicationContext,
                PREFS_NAME,
                MasterKey.Builder(context.applicationContext).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            ).also { cachedPrefs = it }
        }
    }
}
