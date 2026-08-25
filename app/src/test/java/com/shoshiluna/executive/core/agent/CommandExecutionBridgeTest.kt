package com.shoshiluna.executive.core.agent

import com.shoshiluna.executive.core.empire.EmpireMissionControl
import com.shoshiluna.executive.core.empire.MissionStatus
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CommandExecutionBridgeTest {
    private lateinit var bridge: CommandExecutionBridge

    @Before fun setup() {
        EmpireMissionControl.resetForTest()
        bridge = CommandExecutionBridge()
    }

    @After fun cleanup() { EmpireMissionControl.resetForTest() }

    @Test fun hebrewCreateMissionMutatesRealMissionState() {
        val result = bridge.tryExecute("צרי משימה בדיקת מערכת", ExecutiveAgent.SHOSHI)
        assertTrue(result is CommandBridgeResult.Executed)
        val mission = EmpireMissionControl.state.value.missions.single()
        assertEquals("בדיקת מערכת", mission.title)
        assertEquals(ExecutiveAgent.SHOSHI.id, mission.ownerId)
        assertEquals(MissionStatus.CREATED, mission.status)
    }

    @Test fun emergencyHaltMutatesMissionAndBlocksCreation() {
        bridge.tryExecute("צרי משימה לפני עצירה", ExecutiveAgent.SHOSHI)
        val halt = bridge.tryExecute("עצור הכל", ExecutiveAgent.SHOSHI)
        assertTrue(halt is CommandBridgeResult.Executed)
        assertTrue(EmpireMissionControl.state.value.emergencyHalt)
        assertEquals(MissionStatus.HALTED, EmpireMissionControl.state.value.missions.single().status)

        val blocked = bridge.tryExecute("צרי משימה שאסור ליצור", ExecutiveAgent.LUNA)
        assertTrue(blocked is CommandBridgeResult.Rejected)
        assertEquals(1, EmpireMissionControl.state.value.missions.size)
    }

    @Test fun releaseHaltCannotBeExecutedFromChat() {
        bridge.tryExecute("עצור הכל", ExecutiveAgent.SHOSHI)
        val result = bridge.tryExecute("שחרר", ExecutiveAgent.SHOSHI)
        assertTrue(result is CommandBridgeResult.Rejected)
        assertTrue(EmpireMissionControl.state.value.emergencyHalt)
    }

    @Test fun ordinaryConversationIsNotMisclassifiedAsCommand() {
        val result = bridge.tryExecute("תסבירי לי למה חברה יכולה לשרוף מזומן", ExecutiveAgent.SHOSHI)
        assertTrue(result is CommandBridgeResult.NotHandled)
    }

    @Test fun vagueWorkCommandRequestsMeasurableRevenueGoalWithoutCreatingMission() {
        val result = bridge.tryExecute("לכי לעבוד", ExecutiveAgent.SHOSHI)
        assertTrue(result is CommandBridgeResult.ClarificationRequired)
        result as CommandBridgeResult.ClarificationRequired
        assertEquals("CLARIFY_REVENUE_GOAL", result.command)
        assertTrue(result.message.contains("כמה כסף"))
        assertTrue(result.message.contains("באיזה טווח זמן"))
        assertTrue(EmpireMissionControl.state.value.missions.isEmpty())
    }
}
