package com.shoshiluna.executive.core.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.shoshiluna.executive.core.network.AiProviderId
import com.shoshiluna.executive.core.network.AiProviderRegistry

/**
 * ShoshiLuna Secret Store.
 * Encrypted at rest. No financial secrets allowed.
 */
object ApiKeyManager {
    private const val PREFS_NAME = "shoshiluna_secure_keys"
    private const val KEY_OPENAI_API = "OPENAI_API_KEY"
    private const val KEY_ACTIVE_PROVIDER = "ACTIVE_AI_PROVIDER"
    private const val KEY_PROVIDER_PREFIX = "AI_PROVIDER_KEY_"
    private const val KEY_CUSTOM_ENDPOINT = "CUSTOM_ENDPOINT"
    private const val KEY_CUSTOM_MODEL = "CUSTOM_MODEL"
    private const val KEY_CUSTOM_AUTH_HEADER = "CUSTOM_AUTH_HEADER"
    private const val KEY_CUSTOM_AUTH_PREFIX = "CUSTOM_AUTH_PREFIX"
    private const val KEY_LOCAL_ENDPOINT = "LOCAL_OPENAI_ENDPOINT"
    private const val KEY_LOCAL_MODEL = "LOCAL_OPENAI_MODEL"

    @Volatile
    private var cachedPrefs: SharedPreferences? = null

    fun saveOpenAiKey(context: Context, apiKey: String) {
        saveProviderKey(context, AiProviderId.OPENAI, apiKey)
    }

    fun getOpenAiKey(context: Context): String? = getProviderKey(context, AiProviderId.OPENAI)

    fun hasOpenAiKey(context: Context): Boolean = getOpenAiKey(context) != null

    fun clearOpenAiKey(context: Context) {
        clearProviderKey(context, AiProviderId.OPENAI)
    }

    fun saveProviderKey(context: Context, provider: AiProviderId, apiKey: String) {
        require(provider != AiProviderId.CUSTOM && provider != AiProviderId.LOCAL_OPENAI) { "Use the dedicated custom/local provider method" }
        val normalized = AiProviderRegistry.normalizeSecret(apiKey)
        require(normalized.isNotBlank()) { "API key cannot be blank" }
        prefs(context).edit()
            .putString(providerKey(provider), normalized)
            .putString(KEY_ACTIVE_PROVIDER, provider.name)
            .also { editor -> if (provider == AiProviderId.OPENAI) editor.putString(KEY_OPENAI_API, normalized) }
            .apply()
    }

    fun getProviderKey(context: Context, provider: AiProviderId): String? {
        val stored = prefs(context).getString(providerKey(provider), null)
        if (!stored.isNullOrBlank()) return stored
        return if (provider == AiProviderId.OPENAI) prefs(context).getString(KEY_OPENAI_API, null)?.takeIf { it.isNotBlank() } else null
    }

    fun hasProviderKey(context: Context, provider: AiProviderId): Boolean = getProviderKey(context, provider) != null

    fun hasProviderAccess(context: Context, provider: AiProviderId): Boolean =
        if (provider == AiProviderId.LOCAL_OPENAI) {
            prefs(context).getString(KEY_LOCAL_ENDPOINT, null)?.isNotBlank() == true &&
                prefs(context).getString(KEY_LOCAL_MODEL, null)?.isNotBlank() == true
        } else hasProviderKey(context, provider)

    fun clearProviderKey(context: Context, provider: AiProviderId) {
        val editor = prefs(context).edit().remove(providerKey(provider))
        if (provider == AiProviderId.OPENAI) editor.remove(KEY_OPENAI_API)
        if (provider == AiProviderId.CUSTOM) editor.remove(KEY_CUSTOM_ENDPOINT).remove(KEY_CUSTOM_MODEL).remove(KEY_CUSTOM_AUTH_HEADER).remove(KEY_CUSTOM_AUTH_PREFIX)
        if (provider == AiProviderId.LOCAL_OPENAI) editor.remove(KEY_LOCAL_ENDPOINT).remove(KEY_LOCAL_MODEL)
        if (activeProvider(context) == provider) editor.remove(KEY_ACTIVE_PROVIDER)
        editor.apply()
    }

    fun configuredProviders(context: Context): Set<AiProviderId> = AiProviderId.entries
        .filter { hasProviderAccess(context, it) }
        .toSet()

    fun saveLocalProvider(context: Context, endpoint: String, model: String) {
        com.shoshiluna.executive.core.network.AiProviderProtocols.local(endpoint, model)
        prefs(context).edit()
            .putString(KEY_LOCAL_ENDPOINT, endpoint.trim())
            .putString(KEY_LOCAL_MODEL, model.trim())
            .putString(KEY_ACTIVE_PROVIDER, AiProviderId.LOCAL_OPENAI.name)
            .apply()
    }

    fun localProtocol(context: Context) = com.shoshiluna.executive.core.network.AiProviderProtocols.local(
        requireNotNull(prefs(context).getString(KEY_LOCAL_ENDPOINT, null)),
        requireNotNull(prefs(context).getString(KEY_LOCAL_MODEL, null))
    )

    fun saveCustomProvider(context: Context, apiKey: String, endpoint: String, model: String, authHeader: String = "Authorization", authPrefix: String = "Bearer ") {
        com.shoshiluna.executive.core.network.AiProviderProtocols.custom(endpoint, model, authHeader, authPrefix)
        val normalized = AiProviderRegistry.normalizeSecret(apiKey)
        require(normalized.isNotBlank())
        prefs(context).edit().putString(providerKey(AiProviderId.CUSTOM), normalized)
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

    fun activeProvider(context: Context): AiProviderId? = prefs(context)
        .getString(KEY_ACTIVE_PROVIDER, null)
        ?.let { runCatching { AiProviderId.valueOf(it) }.getOrNull() }

    fun setActiveProvider(context: Context, provider: AiProviderId) {
        require(hasProviderAccess(context, provider)) { "Provider is not configured" }
        prefs(context).edit().putString(KEY_ACTIVE_PROVIDER, provider.name).apply()
    }

    private fun providerKey(provider: AiProviderId): String = "$KEY_PROVIDER_PREFIX${provider.name}"

    private fun prefs(context: Context): SharedPreferences {
        cachedPrefs?.let { return it }
        return synchronized(this) {
            cachedPrefs ?: EncryptedSharedPreferences.create(
                context.applicationContext,
                PREFS_NAME,
                MasterKey.Builder(context.applicationContext)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build(),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            ).also { cachedPrefs = it }
        }
    }
}
