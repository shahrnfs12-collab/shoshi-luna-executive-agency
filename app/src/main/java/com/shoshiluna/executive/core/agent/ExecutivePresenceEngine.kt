package com.shoshiluna.executive.core.agent

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

enum class ExecutivePresenceState {
    IDLE,
    LISTENING,
    THINKING,
    WORKING,
    SPEAKING,
    COLLABORATING,
    WAITING,
    ERROR
}

data class ExecutivePresence(
    val agent: ExecutiveAgent,
    val state: ExecutivePresenceState = ExecutivePresenceState.IDLE,
    val detail: String = "Ready",
    val missionId: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Runtime truth for SHOSHI/LUNA presence. UI animation/status must be driven from
 * these states rather than from decorative timers so "alive" never means fake activity.
 */
object ExecutivePresenceEngine {
    private val current = ConcurrentHashMap<ExecutiveAgent, ExecutivePresence>().apply {
        ExecutiveAgent.entries.forEach { agent -> put(agent, ExecutivePresence(agent)) }
    }
    private val _presence = MutableStateFlow(current.toMap())
    val presence = _presence.asStateFlow()

    fun set(
        agent: ExecutiveAgent,
        state: ExecutivePresenceState,
        detail: String,
        missionId: String? = null
    ) {
        current[agent] = ExecutivePresence(agent, state, detail, missionId)
        _presence.value = current.toMap()
    }

    fun setBoth(state: ExecutivePresenceState, detail: String, missionId: String? = null) {
        ExecutiveAgent.entries.forEach { set(it, state, detail, missionId) }
    }

    fun idle(agent: ExecutiveAgent, detail: String = "Ready") =
        set(agent, ExecutivePresenceState.IDLE, detail)

    fun idleBoth(detail: String = "Ready") =
        setBoth(ExecutivePresenceState.IDLE, detail)

    fun snapshot(agent: ExecutiveAgent): ExecutivePresence =
        current[agent] ?: ExecutivePresence(agent)
}
