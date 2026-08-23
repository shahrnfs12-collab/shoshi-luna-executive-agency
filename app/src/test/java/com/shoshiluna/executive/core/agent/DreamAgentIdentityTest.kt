package com.shoshiluna.executive.core.agent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DreamAgentIdentityTest {
    @Test
    fun shoshiAndLunaHaveDistinctExecutiveIdentity() {
        val shoshi = AgentSystemPrompts.forAgent(ExecutiveAgent.SHOSHI)
        val luna = AgentSystemPrompts.forAgent(ExecutiveAgent.LUNA)
        assertFalse(shoshi == luna)
        assertTrue(shoshi.contains("Chief Growth & Strategy Executive"))
        assertTrue(luna.contains("Chief Partnerships & Operations Executive"))
        assertTrue(shoshi.contains("User Success Obsession"))
        assertTrue(luna.contains("User Success Obsession"))
        assertTrue(shoshi.contains("Never bypass Human Approval"))
        assertTrue(luna.contains("Never bypass Human Approval"))
    }

    @Test
    fun dreamModelPolicyUsesCurrentFrontierModel() {
        assertTrue(AgentModelPolicy.modelFor(ExecutiveAgent.SHOSHI) == "gpt-5.6-sol")
        assertTrue(AgentModelPolicy.modelFor(ExecutiveAgent.LUNA) == "gpt-5.6-sol")
    }
}
