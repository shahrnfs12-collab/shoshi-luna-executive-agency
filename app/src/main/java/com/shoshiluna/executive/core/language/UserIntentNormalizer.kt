package com.shoshiluna.executive.core.language

/**
 * Conservative preprocessing for chat intent detection.
 * The original user text is always preserved; normalization is only used to improve routing
 * and to give the model typo-aware context without silently changing ambiguous meaning.
 */
object UserIntentNormalizer {
    data class Result(
        val original: String,
        val normalizedForRouting: String,
        val modelPrompt: String
    )

    private val conservativeReplacements = linkedMapOf(
        "גיטהב" to "גיטהאב",
        "גיטאב" to "גיטהאב",
        "גיט האב" to "גיטהאב",
        "ריפו" to "repo",
        "רפו" to "repo",
        "ברנץ" to "branch",
        "קומיט" to "commit",
        "וורקפלואו" to "workflow",
        "סירטון" to "סרטון",
        "סירטונים" to "סרטונים",
        "וידאוים" to "סרטונים",
        "ליבנות" to "לבנות",
        "תיבני" to "תבני",
        "תיבנה" to "תבנה",
        "תיצרי" to "תייצרי",
        "תיצור" to "תייצר",
        "git hub" to "github",
        "gitub" to "github",
        "githb" to "github",
        "repsoitory" to "repository",
        "repositry" to "repository",
        "workfow" to "workflow",
        "comit" to "commit",
        "brach" to "branch"
    )

    /** Only routing keywords are fuzzy-corrected. Arbitrary prose is never fuzzy-rewritten. */
    private val fuzzyRoutingKeywords = setOf(
        "github", "repository", "repo", "workflow", "commit", "branch",
        "סרטון", "סרטונים", "וידאו", "לבנות", "תבני", "ליצור", "תייצרי", "תייצר"
    )

    fun process(text: String): Result {
        val original = text.trim()
        var normalized = original
            .lowercase()
            .replace(Regex("[\\u200E\\u200F\\u202A-\\u202E]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()

        conservativeReplacements.forEach { (from, to) ->
            normalized = normalized.replace(from, to, ignoreCase = true)
        }
        normalized = fuzzyNormalizeRoutingTokens(normalized)

        val modelPrompt = """
            LANGUAGE INTERPRETATION RULE
            The user's original message may contain spelling mistakes, missing letters, joined words, keyboard slips, phonetic Hebrew, or mixed Hebrew/English.
            Infer the intended meaning from context and continue without calling out minor spelling mistakes.
            Never silently change a meaning when two materially different interpretations are plausible; ask one concise clarification only in that case.
            Preserve names, numbers, URLs, credentials, file paths and code literals exactly as written unless the user explicitly asks to correct them.

            ORIGINAL USER MESSAGE:
            $original
        """.trimIndent()

        return Result(original, normalized, modelPrompt)
    }

    private fun fuzzyNormalizeRoutingTokens(text: String): String {
        return text.split(' ').joinToString(" ") { token ->
            if (!isSafeFuzzyCandidate(token)) return@joinToString token
            val candidate = fuzzyRoutingKeywords
                .map { keyword -> keyword to levenshtein(token, keyword) }
                .filter { (keyword, distance) -> distance <= allowedDistance(token, keyword) }
                .minByOrNull { it.second }
            candidate?.first ?: token
        }
    }

    private fun isSafeFuzzyCandidate(token: String): Boolean {
        if (token.length < 4) return false
        if (token.any { it.isDigit() }) return false
        if (token.contains('/') || token.contains('.') || token.contains('@') || token.contains(':')) return false
        if (!token.all { it.isLetter() || it == '-' || it == '_' }) return false
        return true
    }

    private fun allowedDistance(token: String, keyword: String): Int {
        val maxLen = maxOf(token.length, keyword.length)
        return when {
            maxLen <= 5 -> 1
            maxLen <= 9 -> 2
            else -> 3
        }
    }

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in a.indices) {
            current[0] = i + 1
            for (j in b.indices) {
                val insert = current[j] + 1
                val delete = previous[j + 1] + 1
                val replace = previous[j] + if (a[i] == b[j]) 0 else 1
                current[j + 1] = minOf(insert, delete, replace)
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[b.length]
    }
}
