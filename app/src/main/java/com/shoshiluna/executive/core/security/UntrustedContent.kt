package com.shoshiluna.executive.core.security

import java.util.UUID

enum class TrustLevel {
    TRUSTED_SYSTEM,
    USER_PROVIDED,
    EXTERNAL_UNTRUSTED,
    MALICIOUS_CANDIDATE
}

/**
 * Wrapper for all data retrieved from external sources.
 * Prevents instruction override and exfiltration.
 */
data class UntrustedContent(
    val id: String = UUID.randomUUID().toString(),
    val rawData: String,
    val source: String,
    val trustLevel: TrustLevel = TrustLevel.EXTERNAL_UNTRUSTED,
    val timestamp: Long = System.currentTimeMillis()
) {
    /**
     * Enforce strict boundary by wrapping content in delimiters.
     */
    fun getSafeRepresentation(): String {
        return """
            --- START UNTRUSTED DATA [$source] ---
            $rawData
            --- END UNTRUSTED DATA ---
        """.trimIndent()
    }
}
