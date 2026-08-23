package com.shoshiluna.executive.core.security

/**
 * ShoshiLuna Log Redactor.
 * Prevents secrets from appearing in logs and system memory.
 */
object LogRedactor {
    
    private val sensitivePatterns = mutableListOf<Regex>()

    fun registerSecretPattern(regex: Regex) {
        sensitivePatterns.add(regex)
    }

    fun redact(input: String?): String {
        if (input == null) return ""
        var current = input
        sensitivePatterns.forEach { regex ->
            current = current?.replace(regex, "[REDACTED]")
        }
        return current ?: ""
    }

    /**
     * Auto-register common secret patterns.
     */
    init {
        // Example: common API key patterns (alphanumeric, 32-40 chars)
        registerSecretPattern(Regex("[a-zA-Z0-9]{32,40}"))
        // Example: bearer tokens
        registerSecretPattern(Regex("Bearer\\s+[a-zA-Z0-9\\-\\._~\\+/]+=*"))
    }
}
