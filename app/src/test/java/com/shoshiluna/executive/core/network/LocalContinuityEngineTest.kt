package com.shoshiluna.executive.core.network

import org.junit.Assert.assertTrue
import org.junit.Test

class LocalContinuityEngineTest {
    @Test
    fun `direct chat remains available without cloud ai`() {
        val result = LocalContinuityEngine.tryHandle(
            listOf(
                ChatMessage("system", "You are SHOSHI."),
                ChatMessage("user", "תכיני תוכנית עבודה")
            )
        )
        assertTrue(result is AiCallResult.Success)
        val text = (result as AiCallResult.Success).text
        assertTrue(text.startsWith("[LOCAL CONTINUITY MODE]"))
        assertTrue(text.contains("ללא API"))
    }

    @Test
    fun `mission fallback stays fail closed without cloud ai`() {
        val result = LocalContinuityEngine.tryHandle(
            listOf(
                ChatMessage("system", "You are SHOSHI.\nMission ID: mission-123."),
                ChatMessage("user", "Complete the mission")
            )
        )
        assertTrue(result is AiCallResult.Failure)
        val failure = result as AiCallResult.Failure
        assertTrue(failure.state == AiConnectionState.API_KEY_MISSING)
    }

    @Test
    fun `local response never claims external completion`() {
        val result = LocalContinuityEngine.tryHandle(
            listOf(ChatMessage("user", "תעשי את המשימה ותכניסי כסף"))
        )
        assertTrue(result is AiCallResult.Success)
        val text = (result as AiCallResult.Success).text
        assertTrue(text.contains("לא יסומנו כהושלמו") || text.contains("לא אמציא"))
    }
}
