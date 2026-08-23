package com.shoshiluna.executive.core.presentation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class MicroExpression(
    val type: String,
    val intensity: Float,
    val durationMs: Long
)

data class SpatialPosition(
    val x: Float,
    val y: Float,
    val z: Float
)

enum class AnimationState {
    IDLE,
    WALK,
    TURN_LEFT,
    TURN_RIGHT,
    SPEAK,
    LISTEN,
    GESTURE_EXECUTIVE
}

/**
 * ShoshiLuna Digital Human Controller.
 * Manages physical properties like translation and skeletal state.
 */
object DigitalHumanController {
    
    private val _shoshiPosition = MutableStateFlow(SpatialPosition(0f, 0f, 0f))
    val shoshiPosition = _shoshiPosition.asStateFlow()

    private val _lunaPosition = MutableStateFlow(SpatialPosition(2f, 0f, 0f))
    val lunaPosition = _lunaPosition.asStateFlow()

    private val _shoshiAnim = MutableStateFlow(AnimationState.IDLE)
    val shoshiAnim = _shoshiAnim.asStateFlow()

    private val _lunaAnim = MutableStateFlow(AnimationState.IDLE)
    val lunaAnim = _lunaAnim.asStateFlow()

    private val _shoshiGaze = MutableStateFlow(SpatialContext(CommandCenterArea.MISSION_ROOM, setOf("SHOSHI")))
    val shoshiGaze = _shoshiGaze.asStateFlow()

    private val _lunaGaze = MutableStateFlow(SpatialContext(CommandCenterArea.MISSION_ROOM, setOf("LUNA")))
    val lunaGaze = _lunaGaze.asStateFlow()

    fun triggerExpression(agentId: String, expression: MicroExpression) {
        // Expression transport is intentionally disabled until the GLB exposes compatible morph targets.
    }

    fun updateGaze(agentId: String, target: CommandCenterArea) {
        if (agentId == "SHOSHI") {
            _shoshiGaze.value = _shoshiGaze.value.copy(currentArea = target)
        } else {
            _lunaGaze.value = _lunaGaze.value.copy(currentArea = target)
        }
    }

    /**
     * Physical movement logic (Spatial Translation + skeletal state).
     */
    fun walkTo(agentId: String, area: CommandCenterArea) {
        // Map Area to Coordinates
        val targetPos = when(area) {
            CommandCenterArea.TREASURY_ROOM -> SpatialPosition(-5f, 0f, 10f)
            CommandCenterArea.COUNCIL_ROOM -> SpatialPosition(5f, 0f, 5f)
            CommandCenterArea.BUSINESS_ROOM -> SpatialPosition(0f, 0f, 8f)
            CommandCenterArea.OPPORTUNITY_ROOM -> SpatialPosition(-3f, 0f, 3f)
            CommandCenterArea.PRESENTATION_STAGE -> SpatialPosition(0f, 0f, 0f)
            else -> SpatialPosition(0f, 0f, 0f)
        }
        
        if (agentId == "SHOSHI") {
            _shoshiAnim.value = AnimationState.WALK
            _shoshiPosition.value = targetPos
        } else {
            _lunaAnim.value = AnimationState.WALK
            _lunaPosition.value = targetPos
        }
        
        updateGaze(agentId, area)
    }

    fun stopAll(agentId: String) {
        if (agentId == "SHOSHI") {
            _shoshiAnim.value = AnimationState.IDLE
        } else {
            _lunaAnim.value = AnimationState.IDLE
        }
    }

    fun triggerBlink(agentId: String) {
        // Blink requires a verified eyelid morph target in the final rigged GLB; no fake animation is emitted.
    }
}
