package com.shoshiluna.executive.core.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * ShoshiLuna Secret Store.
 * Encrypted at rest. No financial secrets allowed.
 */
object ApiKeyManager {
    private const val PREFS_NAME = "shoshiluna_secure_keys"
    private const val KEY_OPENAI_API = "OPENAI_API_KEY"

    @Volatile
    private var cachedPrefs: SharedPreferences? = null

    fun saveOpenAiKey(context: Context, apiKey: String) {
        require(apiKey.isNotBlank()) { "OpenAI API key cannot be blank" }
        prefs(context).edit().putString(KEY_OPENAI_API, apiKey.trim()).apply()
    }

    fun getOpenAiKey(context: Context): String? =
        prefs(context).getString(KEY_OPENAI_API, null)?.takeIf { it.isNotBlank() }

    fun hasOpenAiKey(context: Context): Boolean = getOpenAiKey(context) != null

    fun clearOpenAiKey(context: Context) {
        prefs(context).edit().remove(KEY_OPENAI_API).apply()
    }

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
