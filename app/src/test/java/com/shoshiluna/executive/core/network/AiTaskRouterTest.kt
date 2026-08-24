package com.shoshiluna.executive.core.network

import org.junit.Assert.assertEquals
import org.junit.Test

class AiTaskRouterTest {
    @Test fun `classifies common Hebrew executive tasks`() {
        assertEquals(AiTaskProfile.RESEARCH, AiTaskRouter.classify("בדוק מקורות ומחקר שוק"))
        assertEquals(AiTaskProfile.WRITING, AiTaskRouter.classify("כתוב לי מייל לשותף"))
        assertEquals(AiTaskProfile.STRATEGY, AiTaskRouter.classify("בני תוכנית צמיחה אסטרטגית"))
        assertEquals(AiTaskProfile.OPERATIONS, AiTaskRouter.classify("בצע את המשימה בתהליך מסודר"))
    }

    @Test fun `uses only configured brains and prefers profile specialist`() {
        val configured = setOf(AiProviderId.OPENAI, AiProviderId.GOOGLE_GEMINI)
        assertEquals(
            listOf(AiProviderId.GOOGLE_GEMINI, AiProviderId.OPENAI),
            AiTaskRouter.orderedProviders(configured, AiProviderId.OPENAI, AiTaskProfile.RESEARCH)
        )
    }
}
