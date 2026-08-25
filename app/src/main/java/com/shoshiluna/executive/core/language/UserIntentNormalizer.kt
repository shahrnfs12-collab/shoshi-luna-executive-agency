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
        "ליצור" to "ליצור",
        "צור לי" to "צור לי",
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
}
