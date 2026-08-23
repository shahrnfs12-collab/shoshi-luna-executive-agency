package com.shoshiluna.executive.core.presentation

import java.util.UUID

enum class PresenceState {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING,
    WALKING,
    TURNING,
    PRESENTING,
    COUNCIL,
    DEBATE,
    WAITING,
    ALERT,
    PAUSED,
    SITTING,
    STANDING_UP,
    SITTING_DOWN
}

enum class HairStyle {
    DOWN_LOOSE,
    SUBTLE_WAVES,
    PONYTAIL_FORBIDDEN // Rule: Shoshi hair MUST be DOWN/LOOSE
}

enum class HairColor {
    DARK_BROWN,
    BLACK,
    OTHER_FORBIDDEN
}

data class IdentityConfig(
    val agentId: String,
    val visualAge: Int,
    val hairColor: HairColor,
    val hairStyle: HairStyle,
    val isLongHair: Boolean = true,
    val photorealisticTarget: String = "ULTRA_HIGH",
    val voiceId: String,
    val personalityProfile: String
)

data class PresenceUpdate(
    val agentId: String,
    val newState: PresenceState,
    val targetArea: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val auditId: String = UUID.randomUUID().toString()
)
