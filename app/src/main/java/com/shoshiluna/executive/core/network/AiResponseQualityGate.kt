package com.shoshiluna.executive.core.network

import java.text.Normalizer

/**
 * Rejects non-conversational output and removes provider-side repetition before it reaches chat.
 */
object AiResponseQualityGate {
    private val classifierOnly = listOf(
        Regex("""^user\s+safety\s*:\s*(safe|unsafe)\.?$""", RegexOption.IGNORE_CASE),
        Regex("""^safety\s*:\s*(safe|unsafe)\.?$""", RegexOption.IGNORE_CASE),
        Regex("""^(safe|unsafe)\.?$""", RegexOption.IGNORE_CASE),
        Regex("""^category\s*:\s*[a-z0-9_-]+\.?$""", RegexOption.IGNORE_CASE)
    )

    fun isUsable(text: String): Boolean {
        val clean = sanitize(text)
        if (clean.length < 2) return false
        return classifierOnly.none { it.matches(clean) }
    }

    /**
     * Keeps the first occurrence of a repeated paragraph/list item while preserving order.
     * Providers sometimes echo the same block with different spacing or punctuation.
     */
    fun sanitize(text: String): String {
        val normalized = Normalizer.normalize(text, Normalizer.Form.NFC)
            .replace("\r\n", "\n")
            .replace(Regex("""[ \t]+"""), " ")
            .replace(Regex(""" *\n *"""), "\n")
            .replace(Regex("""\n{3,}"""), "\n\n")
            .replace(Regex("""([!?.,])\1{2,}"""), "$1$1")
            .trim()

        if (normalized.isBlank()) return ""

        val seen = LinkedHashSet<String>()
        return normalized
            .split(Regex("""\n\s*\n"""))
            .map { it.trim() }
            .filter { block ->
                val key = block
                    .lowercase()
                    .replace(Regex("""[\p{P}\p{S}\s]+"""), "")
                key.isBlank() || seen.add(key)
            }
            .joinToString("\n\n")
            .trim()
    }
}
