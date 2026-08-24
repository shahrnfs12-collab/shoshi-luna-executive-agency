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

    @Volatile
    private var cachedPrefs: SharedPreferences? = null

    fun saveOpenAiKey(context: Context, apiKey: String) {
        val normalized = AiProviderRegistry.normalizeSecret(apiKey)
        require(normalized.isNotBlank()) { "OpenAI API key cannot be blank" }
        prefs(context).edit()
            .putString(KEY_OPENAI_API, normalized)
            .putString(KEY_ACTIVE_PROVIDER, AiProviderId.OPENAI.name)
            .apply()
    }

    fun getOpenAiKey(context: Context): String? =
        prefs(context).getString(KEY_OPENAI_API, null)?.takeIf { it.isNotBlank() }

    fun hasOpenAiKey(context: Context): Boolean = getOpenAiKey(context) != null

    fun clearOpenAiKey(context: Context) {
        prefs(context).edit().remove(KEY_OPENAI_API).remove(KEY_ACTIVE_PROVIDER).apply()
    }

    fun activeProvider(context: Context): AiProviderId? = prefs(context)
        .getString(KEY_ACTIVE_PROVIDER, null)
        ?.let { runCatching { AiProviderId.valueOf(it) }.getOrNull() }

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
