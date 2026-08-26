package com.shoshiluna.executive.core.video

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.net.URI

/**
 * Encrypted configuration for a user-owned video-generation endpoint.
 * The app never hard-codes provider credentials in source control.
 */
object VideoGenerationCredentialStore {
    private const val PREFS_NAME = "shoshiluna_video_generation_credentials"
    private const val KEY_ENDPOINT = "endpoint"
    private const val KEY_TOKEN = "token"

    @Volatile private var context: Context? = null
    @Volatile private var cachedPrefs: SharedPreferences? = null

    fun initialize(context: Context) {
        this.context = context.applicationContext
    }

    fun save(endpoint: String, token: String) {
        val normalizedEndpoint = normalizeEndpoint(endpoint)
        val normalizedToken = token.trim().removePrefix("Bearer ").trim()
        require(normalizedToken.isNotBlank()) { "Video-generation credential cannot be blank" }
        prefs().edit()
            .putString(KEY_ENDPOINT, normalizedEndpoint)
            .putString(KEY_TOKEN, normalizedToken)
            .apply()
    }

    fun endpoint(): String? = prefs().getString(KEY_ENDPOINT, null)?.takeIf { it.isNotBlank() }
    fun token(): String? = prefs().getString(KEY_TOKEN, null)?.takeIf { it.isNotBlank() }
    fun isConfigured(): Boolean = endpoint() != null && token() != null
    fun clear() = prefs().edit().clear().apply()

    private fun normalizeEndpoint(value: String): String {
        val raw = value.trim().trimEnd('/')
        val uri = runCatching { URI(raw) }.getOrElse { throw IllegalArgumentException("Invalid video-generation endpoint") }
        require(uri.scheme.equals("https", ignoreCase = true)) { "Video-generation endpoint must use HTTPS" }
        require(!uri.host.isNullOrBlank()) { "Video-generation endpoint must include a host" }
        return raw
    }

    private fun prefs(): SharedPreferences {
        cachedPrefs?.let { return it }
        val appContext = requireNotNull(context) { "VideoGenerationCredentialStore is not initialized" }
        return synchronized(this) {
            cachedPrefs ?: EncryptedSharedPreferences.create(
                appContext,
                PREFS_NAME,
                MasterKey.Builder(appContext)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build(),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            ).also { cachedPrefs = it }
        }
    }
}
