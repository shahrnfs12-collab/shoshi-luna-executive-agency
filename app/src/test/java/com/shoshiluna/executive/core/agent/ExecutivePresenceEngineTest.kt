package com.shoshiluna.executive.core.agent

import org.junit.Assert.assertEquals
import org.junit.Test

class ExecutivePresenceEngineTest {
    @Test
    fun `presence tracks real executive state transitions`() {
        ExecutivePresenceEngine.idleBoth()
        ExecutivePresenceEngine.set(ExecutiveAgent.SHOSHI, ExecutivePresenceState.THINKING, "Analyzing")
        ExecutivePresenceEngine.set(ExecutiveAgent.LUNA, ExecutivePresenceState.LISTENING, "Reviewing")

        assertEquals(ExecutivePresenceState.THINKING, ExecutivePresenceEngine.snapshot(ExecutiveAgent.SHOSHI).state)
        assertEquals("Analyzing", ExecutivePresenceEngine.snapshot(ExecutiveAgent.SHOSHI).detail)
        assertEquals(ExecutivePresenceState.LISTENING, ExecutivePresenceEngine.snapshot(ExecutiveAgent.LUNA).state)

        ExecutivePresenceEngine.setBoth(ExecutivePresenceState.COLLABORATING, "Shared room")
        assertEquals(ExecutivePresenceState.COLLABORATING, ExecutivePresenceEngine.snapshot(ExecutiveAgent.SHOSHI).state)
        assertEquals(ExecutivePresenceState.COLLABORATING, ExecutivePresenceEngine.snapshot(ExecutiveAgent.LUNA).state)

        ExecutivePresenceEngine.idleBoth()
    }
}
