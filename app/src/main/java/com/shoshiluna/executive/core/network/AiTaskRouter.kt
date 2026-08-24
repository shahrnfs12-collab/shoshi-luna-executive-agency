package com.shoshiluna.executive.core.network

/** Routes work by task shape; it never claims that an unavailable provider was used. */
enum class AiTaskProfile { STRATEGY, RESEARCH, WRITING, OPERATIONS, GENERAL }

object AiTaskRouter {
    fun classify(text: String): AiTaskProfile {
        val value = text.lowercase()
        return when {
            containsAny(value, "מחקר", "בדוק", "חפש", "מקורות", "research", "verify", "sources") -> AiTaskProfile.RESEARCH
            containsAny(value, "כתוב", "ניסוח", "מייל", "תוכן", "פוסט", "write", "draft", "content") -> AiTaskProfile.WRITING
            containsAny(value, "אסטרטג", "תוכנית", "צמיחה", "החלט", "strategy", "plan", "growth", "decide") -> AiTaskProfile.STRATEGY
            containsAny(value, "בצע", "תפעול", "משימה", "תהליך", "execute", "operate", "workflow", "task") -> AiTaskProfile.OPERATIONS
            else -> AiTaskProfile.GENERAL
        }
    }

    fun orderedProviders(
        configured: Set<AiProviderId>,
        active: AiProviderId?,
        profile: AiTaskProfile
    ): List<AiProviderId> {
        val preferred = when (profile) {
            AiTaskProfile.STRATEGY -> listOf(AiProviderId.LOCAL_OPENAI, AiProviderId.OPENAI, AiProviderId.ANTHROPIC, AiProviderId.GOOGLE_GEMINI, AiProviderId.OPENROUTER, AiProviderId.CUSTOM)
            AiTaskProfile.RESEARCH -> listOf(AiProviderId.LOCAL_OPENAI, AiProviderId.GOOGLE_GEMINI, AiProviderId.OPENAI, AiProviderId.ANTHROPIC, AiProviderId.OPENROUTER, AiProviderId.CUSTOM)
            AiTaskProfile.WRITING -> listOf(AiProviderId.LOCAL_OPENAI, AiProviderId.ANTHROPIC, AiProviderId.OPENAI, AiProviderId.GOOGLE_GEMINI, AiProviderId.OPENROUTER, AiProviderId.CUSTOM)
            AiTaskProfile.OPERATIONS -> listOf(AiProviderId.LOCAL_OPENAI, AiProviderId.OPENAI, AiProviderId.GOOGLE_GEMINI, AiProviderId.ANTHROPIC, AiProviderId.OPENROUTER, AiProviderId.CUSTOM)
            AiTaskProfile.GENERAL -> listOfNotNull(active) + AiProviderId.entries
        }
        return (preferred + listOfNotNull(active) + configured).distinct().filter { it in configured }
    }

    private fun containsAny(value: String, vararg terms: String) = terms.any(value::contains)
}
