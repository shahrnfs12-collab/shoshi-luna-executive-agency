package com.shoshiluna.executive.core.hardening

import com.shoshiluna.executive.core.agent.ExecutiveAgent
import com.shoshiluna.executive.core.empire.EmpireMissionControl
import com.shoshiluna.executive.core.empire.MissionStatus
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class ExistingErrorRegressionTest {
    @Before fun setUp() = EmpireMissionControl.resetForTest()
    @After fun tearDown() = EmpireMissionControl.resetForTest()

    @Test
    fun waitingTransitionCannotBypassMissionTransitionPolicy() {
        val mission = EmpireMissionControl.createMission("regression", ExecutiveAgent.SHOSHI.id)
        assertThrows(IllegalArgumentException::class.java) {
            EmpireMissionControl.enterWaitingState(
                mission.id,
                MissionStatus.WAITING_FOR_EXTERNAL_RESPONSE,
                "external response"
            )
        }
        assertEquals(MissionStatus.CREATED, EmpireMissionControl.state.value.missions.first().status)
    }
}
