package com.shoshiluna.executive.core.presentation

enum class CommandCenterArea {
    EXECUTIVE_HALL,
    MISSION_ROOM,
    BUSINESS_ROOM,
    OPPORTUNITY_ROOM,
    COUNCIL_ROOM,
    DEBATE_ROOM,
    TREASURY_ROOM,
    AGENT_OPERATIONS_ROOM,
    PRESENTATION_STAGE,
    BUSINESS_WEATHER_AREA
}

data class SpatialContext(
    val currentArea: CommandCenterArea,
    val occupants: Set<String>,
    val activeDisplayTopic: String? = null
)

/**
 * Maps Mission Context to Spatial Area.
 */
object SpatialRouter {
    fun getTargetArea(topic: String): CommandCenterArea {
        val lower = topic.lowercase()
        return when {
            lower.contains("treasury") || lower.contains("spend") -> CommandCenterArea.TREASURY_ROOM
            lower.contains("council") || lower.contains("vote") -> CommandCenterArea.COUNCIL_ROOM
            lower.contains("debate") || lower.contains("challenge") -> CommandCenterArea.DEBATE_ROOM
            lower.contains("opportunity") || lower.contains("market") -> CommandCenterArea.OPPORTUNITY_ROOM
            lower.contains("business") || lower.contains("revenue") -> CommandCenterArea.BUSINESS_ROOM
            else -> CommandCenterArea.MISSION_ROOM
        }
    }
}
