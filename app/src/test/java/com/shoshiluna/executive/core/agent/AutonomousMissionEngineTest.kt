package com.shoshiluna.executive.core.agent

import com.shoshiluna.executive.core.empire.EmpireMissionControl
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AutonomousMissionEngineTest {
    @Before fun setup() = EmpireMissionControl.resetForTest()
    @After fun cleanup() = EmpireMissionControl.resetForTest()

    @Test fun `explicit build request creates assigned persisted plan`() {
        val result = CommandExecutionBridge().tryExecute("תבנו לי תוכנית שיווק", ExecutiveAgent.SHOSHI)
        assertTrue(result is CommandBridgeResult.Executed)
        val mission = EmpireMissionControl.state.value.missions.single()
        assertEquals(4, mission.workPlan.size)
        assertTrue(mission.workPlan.any { it.contains(ExecutiveAgent.SHOSHI.id) })
        assertTrue(mission.workPlan.any { it.contains(ExecutiveAgent.LUNA.id) })
        assertEquals("COMPLETED", mission.workPlanProgress["1"])
    }
}
