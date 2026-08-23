package com.shoshiluna.executive.core.security

import android.content.Context
import com.shoshiluna.executive.core.data.HiveMemoryManager

/**
 * ShoshiLuna Secret Manager.
 * Secure storage and indirect reference for sensitive credentials.
 */
object SecretManager {
    
    private val secretReferenceMap = mutableMapOf<String, String>() // refId to rawKey

    fun storeSecret(context: Context, keyName: String, value: String): String {
        // Enforce redaction of the value before logging or memory storage
        LogRedactor.registerSecretPattern(Regex(Regex.escape(value)))
        
        // Physically store encrypted
        HiveMemoryManager.saveString(context, "SECRET_$keyName", value)
        
        val refId = "REF-${keyName.uppercase()}"
        secretReferenceMap[refId] = keyName
        return refId
    }

    fun getSecretRaw(context: Context, refId: String): String? {
        val keyName = secretReferenceMap[refId] ?: return null
        return HiveMemoryManager.getString(context, "SECRET_$keyName", "")
    }

    /**
     * Agent-safe interface: only returns metadata, never the raw value.
     */
    fun getSecretMetadata(refId: String): Map<String, String>? {
        val keyName = secretReferenceMap[refId] ?: return null
        return mapOf("id" to refId, "name" to keyName, "status" to "PROTECTED")
    }
}
