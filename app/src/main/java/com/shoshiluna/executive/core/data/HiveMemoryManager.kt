package com.shoshiluna.executive.core.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson

/**
 * Encrypted local persistence for ShoshiLuna Executive Agency.
 */
object HiveMemoryManager {
    private const val PREFS_NAME = "shoshiluna_executive_secure"
    private val gson = Gson()

    @Volatile
    private var cachedPrefs: SharedPreferences? = null

    fun getPrefs(context: Context): SharedPreferences {
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

    fun saveString(context: Context, key: String, value: String) {
        getPrefs(context).edit().putString(key, value).apply()
    }

    fun getString(context: Context, key: String, default: String): String =
        runCatching { getPrefs(context).getString(key, default) ?: default }.getOrDefault(default)

    fun <T> saveObject(context: Context, key: String, obj: T) {
        saveString(context, key, gson.toJson(obj))
    }

    fun <T> getObject(context: Context, key: String, clazz: Class<T>): T? {
        val json = getString(context, key, "")
        if (json.isBlank()) return null
        return runCatching { gson.fromJson(json, clazz) }.getOrNull()
    }
}
