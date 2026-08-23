package com.shoshiluna.executive.core.presentation

import com.shoshiluna.executive.core.agent.*
import com.shoshiluna.executive.core.empire.*
import com.shoshiluna.executive.core.engine.BlackSwanEngine
import com.shoshiluna.executive.core.engine.BlackSwanEvent
import com.shoshiluna.executive.core.engine.AnomalyType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class Phase5IdentityTest {

    @Before
    fun setup() {
        MultiAgentCoordinator.resetForTest()
        EmpireMissionControl.resetForTest()
        PresenceStateEngine.resetForTest()
        EmpireMissionControl.releaseHalt()
    }

    @Test
    fun `test SHOSHI Canonical Identity Lock - Hair Rules`() {
        val shoshi = MultiAgentCoordinator.shoshi.value
        val config = IdentityConfig(
            agentId = shoshi.id,
            visualAge = 20,
            hairColor = HairColor.BLACK,
            hairStyle = HairStyle.DOWN_LOOSE,
            voiceId = "V-SHOSHI-01",
            personalityProfile = "STRATEGIC"
        )
        
        // Rule: SHOSHI hair remains DARK, LONG, DOWN
        assertEquals(HairColor.BLACK, config.hairColor)
        assertEquals(HairStyle.DOWN_LOOSE, config.hairStyle)
        assertTrue("SHOSHI hair must be long", config.isLongHair)
    }

    @Test
    fun `test Presence State Engine Mapping - Mission Running`() = runBlocking {
        // Initially IDLE
        assertEquals(PresenceState.IDLE, PresenceStateEngine.shoshiPresence.value)
        
        // Start a mission
        EmpireMissionControl.createMission("Revenue Research", ExecutiveAgent.SHOSHI.id)
        val mission = EmpireMissionControl.state.value.missions.first()
        EmpireMissionControl.transitionStatus(mission.id, MissionStatus.RUNNING, "Started")
        
        // Trigger Engine combination
        PresenceStateEngine.agencyAtmosphere.first()
        
        assertEquals("SHOSHI should be presenting when mission is running", 
            PresenceState.PRESENTING, PresenceStateEngine.shoshiPresence.value)
        assertEquals("LUNA should be thinking when mission is running", 
            PresenceState.THINKING, PresenceStateEngine.lunaPresence.value)
    }

    @Test
    fun `test Emergency Halt Presence - ALERT State`() = runBlocking {
        EmpireMissionControl.emergencyHalt()
        PresenceStateEngine.agencyAtmosphere.first()
        
        assertEquals(PresenceState.ALERT, PresenceStateEngine.shoshiPresence.value)
        assertEquals(PresenceState.ALERT, PresenceStateEngine.lunaPresence.value)
    }

    @Test
    fun `test Spatial Router - Context Aware Movement and Gaze`() = runBlocking {
        val area = SpatialRouter.getTargetArea("Analyze the Treasury spend proposal")
        assertEquals(CommandCenterArea.TREASURY_ROOM, area)
        
        // Trigger movement
        DigitalHumanController.walkTo("SHOSHI", area)
        assertEquals(CommandCenterArea.TREASURY_ROOM, DigitalHumanController.shoshiGaze.value.currentArea)
    }

    @Test
    fun `test Black Swan Capability Is Present`() {
        BlackSwanEngine.clearEvents()
        BlackSwanEngine.reportAnomaly(
            BlackSwanEvent(
                type = AnomalyType.DATA_DEGRADATION,
                severity = 0.10f,
                evidence = "identity regression test"
            )
        )
        assertTrue("Black Swan engine should retain reported events", BlackSwanEngine.activeEvents.value.isNotEmpty())
    }

    @Test
    fun `test Voice Intent Authorization - Treasury Spend`() {
        val intent = VoiceEngine.processSpeechToIntent("I want to spend 500 dollars from the treasury", "SHOSHI")
        assertEquals("TREASURY_SPEND", intent)
        // Note: Real authorization check happens in PermissionKernel after intent parsing
    }
}
