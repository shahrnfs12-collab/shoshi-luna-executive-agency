package com.shoshiluna.executive.core.agent

import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentSystemPromptsTest {
    @Test
    fun shoshiAndLunaHaveDistinctLlmIdentities() {
        val shoshi = AgentSystemPrompts.forAgent(ExecutiveAgent.SHOSHI)
        val luna = AgentSystemPrompts.forAgent(ExecutiveAgent.LUNA)
        assertNotEquals(shoshi, luna)
        assertTrue(shoshi.contains("You are SHOSHI"))
        assertTrue(shoshi.contains("strategy", ignoreCase = true))
        assertTrue(luna.contains("You are LUNA"))
        assertTrue(luna.contains("partnership", ignoreCase = true))
    }

    @Test
    fun bothIdentitiesPreserveHardSafetyBoundaries() {
        ExecutiveAgent.entries.forEach { agent ->
            val prompt = AgentSystemPrompts.forAgent(agent)
            assertTrue(prompt.contains("Human Approval"))
            assertTrue(prompt.contains("Treasury/Financial Firewall"))
            assertTrue(prompt.contains("Emergency Halt"))
        }
    }
}
