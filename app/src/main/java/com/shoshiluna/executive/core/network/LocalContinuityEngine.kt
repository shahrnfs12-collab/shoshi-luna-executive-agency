package com.shoshiluna.executive.core.network

/**
 * Deterministic continuity layer used when no cloud AI route is available.
 *
 * This is intentionally NOT a fake LLM. It keeps direct chat usable for safe local planning,
 * status explanation and next-action generation while clearly disclosing degraded mode.
 * Mission-scoped autonomous reasoning remains fail-closed so a local template can never be
 * misrepresented as verified AI-backed mission completion.
 */
object LocalContinuityEngine {
    private const val LOCAL_PREFIX = "[LOCAL CONTINUITY MODE]"

    fun tryHandle(messages: List<ChatMessage>): AiCallResult {
        val system = messages.filter { it.role == "system" }.joinToString("\n") { it.content }
        val prompt = messages.lastOrNull { it.role == "user" }?.content?.trim().orEmpty()

        if (prompt.isBlank()) {
            return AiCallResult.Failure(
                AiConnectionState.ERROR,
                "Local continuity could not run because the request was empty."
            )
        }

        // AutonomousMissionEngine currently treats AiCallResult.Success as provider-backed
        // evidence. Do not allow deterministic fallback to cross that truth boundary.
        if (system.contains("Mission ID:", ignoreCase = true)) {
            return AiCallResult.Failure(
                AiConnectionState.API_KEY_MISSING,
                "Cloud AI is unavailable for this mission. Deterministic local services remain available, but AI-backed mission reasoning is paused rather than fabricated."
            )
        }

        return AiCallResult.Success(buildResponse(prompt))
    }

    private fun buildResponse(prompt: String): String {
        val normalized = prompt.lowercase()
        val body = when {
            normalized.contains("סטטוס") || normalized.contains("status") || normalized.contains("מצב") -> statusResponse()
            normalized.contains("תוכנית") || normalized.contains("תכנית") || normalized.contains("plan") -> planResponse(prompt)
            normalized.contains("משימה") || normalized.contains("task") || normalized.contains("לעשות") -> taskResponse(prompt)
            else -> genericResponse(prompt)
        }
        return "$LOCAL_PREFIX\n$body"
    }

    private fun statusResponse(): String = """
        הענן אינו זמין כרגע, אבל האפליקציה לא מושבתת. שכבת העבודה המקומית יכולה להמשיך לנהל state, זיכרון, משימות דטרמיניסטיות, בדיקות, תורים, Evidence וכלים מקומיים שמותרים במדיניות. פעולות שדורשות reasoning של מודל ענן נשארות מושהות ולא יסומנו כהושלמו ללא ראיה.
    """.trimIndent()

    private fun planResponse(prompt: String): String = """
        מצב ללא API פעיל. אני יכול להכין מסלול עבודה מקומי ובטוח עבור הבקשה:
        1. מטרה: ${prompt.take(300)}
        2. לבצע קודם את כל השלבים הדטרמיניסטיים והפנימיים שאינם דורשים מודל ענן.
        3. להשתמש בנתונים, זיכרון, כלים מקומיים ו-Evidence שכבר זמינים במערכת.
        4. לעצור רק את תתי-השלבים שדורשים reasoning חיצוני, credential או פעולה מאושרת.
        5. לא לסמן פעולה חיצונית, הכנסה, מסירה או אימות כהושלמו ללא ראיה אמיתית.
    """.trimIndent()

    private fun taskResponse(prompt: String): String = """
        הבקשה נשמרת כעבודה מקומית אפשרית: ${prompt.take(300)}
        ללא API ניתן להמשיך בכל שלב דטרמיניסטי, persistence, orchestration, validation, Evidence וכלים מקומיים מותרים. אם שלב מסוים דורש מודל ענן, רק אותו שלב יושהה; שאר המערכת ממשיכה לעבוד.
    """.trimIndent()

    private fun genericResponse(prompt: String): String = """
        אין כרגע מסלול AI ענני מאומת. במקום להחזיר כשל ריק, המערכת נשארת במצב עבודה מקומי עבור: ${prompt.take(300)}
        אפשר להמשיך בפעולות מקומיות ודטרמיניסטיות ובכלים שכבר מורשים. לא אמציא reasoning של מודל, ביצוע חיצוני או הכנסה שלא התרחשו בפועל.
    """.trimIndent()
}
