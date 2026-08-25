package com.shoshiluna.executive.core.github

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Device-local GitHub credential store for the owner's personal installation.
 * Credentials never belong in source control and are encrypted at rest.
 */
object GitHubCredentialStore {
    private const val PREFS_NAME = "shoshiluna_github_credentials"
    private const val KEY_TOKEN = "github_token"
    private const val KEY_REPOSITORY = "github_repository"

    @Volatile private var context: Context? = null
    @Volatile private var cachedPrefs: SharedPreferences? = null

    fun initialize(context: Context) {
        this.context = context.applicationContext
    }

    fun save(token: String, repositoryFullName: String) {
        val normalizedToken = token.trim().removePrefix("Bearer ").trim()
        require(normalizedToken.isNotBlank()) { "GitHub credential cannot be blank" }
        val repo = normalizeRepository(repositoryFullName)
        prefs().edit()
            .putString(KEY_TOKEN, normalizedToken)
            .putString(KEY_REPOSITORY, repo)
            .apply()
    }

    fun token(): String? = prefs().getString(KEY_TOKEN, null)?.takeIf { it.isNotBlank() }

    fun repository(): String? = prefs().getString(KEY_REPOSITORY, null)?.takeIf { it.isNotBlank() }

    fun isConfigured(): Boolean = token() != null && repository() != null

    fun clear() {
        prefs().edit().clear().apply()
    }

    fun normalizeRepository(value: String): String {
        val repo = value.trim()
            .removePrefix("https://github.com/")
            .removeSuffix(".git")
            .trim('/')
        require(repo.matches(Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+"))) {
            "Repository must be in owner/name format"
        }
        return repo
    }

    private fun prefs(): SharedPreferences {
        cachedPrefs?.let { return it }
        val appContext = requireNotNull(context) { "GitHubCredentialStore is not initialized" }
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
