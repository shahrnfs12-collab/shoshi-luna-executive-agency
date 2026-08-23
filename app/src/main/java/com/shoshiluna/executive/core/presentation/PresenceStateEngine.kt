package com.shoshiluna.executive.core.presentation

import com.shoshiluna.executive.core.empire.EmpireMissionControl
import com.shoshiluna.executive.core.empire.MissionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine

/**
 * ShoshiLuna Presence State Engine.
 * Maps REAL system state to visual behavior.
 */
object PresenceStateEngine {
    
    private val _shoshiPresence = MutableStateFlow(PresenceState.IDLE)
    val shoshiPresence = _shoshiPresence.asStateFlow()

    private val _lunaPresence = MutableStateFlow(PresenceState.IDLE)
    val lunaPresence = _lunaPresence.asStateFlow()

    /**
     * Reactive mapping of system truth to visuals.
     */
    val agencyAtmosphere = combine(
        EmpireMissionControl.state,
        VoiceEngine.voiceState
    ) { empireState, voice ->
        val activeMissions = empireState.missions.filter { it.status == MissionStatus.RUNNING || it.status == MissionStatus.EXECUTING }
        
        if (empireState.emergencyHalt) {
            _shoshiPresence.value = PresenceState.ALERT
            _lunaPresence.value = PresenceState.ALERT
            return@combine "HALTED"
        }

        if (voice == VoiceState.SPEAKING) {
            _shoshiPresence.value = PresenceState.SPEAKING
            DigitalHumanController.triggerExpression("SHOSHI", MicroExpression("TALKING", 1.0f, 1000L))
        } else if (activeMissions.isNotEmpty()) {
            val mission = activeMissions.first()
            val targetArea = SpatialRouter.getTargetArea(mission.title)
            
            _shoshiPresence.value = PresenceState.PRESENTING
            _lunaPresence.value = PresenceState.THINKING
            
            DigitalHumanController.walkTo("SHOSHI", targetArea)
            DigitalHumanController.updateGaze("LUNA", targetArea)
        } else {
            _shoshiPresence.value = PresenceState.IDLE
            _lunaPresence.value = PresenceState.IDLE
            DigitalHumanController.stopAll("SHOSHI")
            DigitalHumanController.stopAll("LUNA")
        }

        "ACTIVE"
    }

    fun resetForTest() {
        _shoshiPresence.value = PresenceState.IDLE
        _lunaPresence.value = PresenceState.IDLE
    }
}
