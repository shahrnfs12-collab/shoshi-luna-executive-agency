package com.shoshiluna.executive.core.network

/**
 * Prevents classifier/moderation labels from being accepted as executive conversation.
 * Transport success alone is not proof of a usable AI response.
 */
object AiResponseQualityGate {
    private val classifierOnly = listOf(
        Regex("""^user\s+safety\s*:\s*(safe|unsafe)\.?$""", RegexOption.IGNORE_CASE),
        Regex("""^safety\s*:\s*(safe|unsafe)\.?$""", RegexOption.IGNORE_CASE),
        Regex("""^(safe|unsafe)\.?$""", RegexOption.IGNORE_CASE),
        Regex("""^category\s*:\s*[a-z0-9_-]+\.?$""", RegexOption.IGNORE_CASE)
    )

    fun isUsable(text: String): Boolean {
        val clean = text.trim()
        if (clean.length < 2) return false
        return classifierOnly.none { it.matches(clean) }
    }
}
