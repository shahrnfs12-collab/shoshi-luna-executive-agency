package com.shoshiluna.executive.core.agent

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

enum class CouncilOutcome {
    CONSENSUS,
    PARTIAL_CONSENSUS,
    DISAGREEMENT,
    ESCALATE_TO_USER
}

data class CouncilSession(
    val id: String = UUID.randomUUID().toString(),
    val missionId: String,
    val agenda: String,
    val participants: Set<String>,
    val votes: Map<String, String> = emptyMap(),
    val outcome: CouncilOutcome? = null,
    val isActive: Boolean = true
)

/**
 * ShoshiLuna Council Engine.
 * Manages formal multi-agent proposals and voting.
 */
object CouncilEngine {
    private val _sessions = MutableStateFlow<Map<String, CouncilSession>>(emptyMap())
    val sessions = _sessions.asStateFlow()

    fun startSession(missionId: String, agenda: String, participants: Set<String>): CouncilSession {
        check(!com.shoshiluna.executive.core.empire.EmpireMissionControl.state.value.emergencyHalt) { "Emergency halt is active" }
        require(missionId.isNotBlank()) { "missionId is required" }
        require(agenda.isNotBlank()) { "agenda is required" }
        require(participants.size >= 2) { "Council requires at least two participants" }
        require(com.shoshiluna.executive.core.empire.EmpireMissionControl.state.value.missions.any { it.id == missionId }) { "Unknown missionId" }
        val session = CouncilSession(missionId = missionId, agenda = agenda.trim(), participants = participants)
        _sessions.value = _sessions.value + (session.id to session)
        return session
    }

    fun castVote(sessionId: String, agentId: String, position: String): Boolean {
        val session = _sessions.value[sessionId] ?: return false
        if (!session.isActive || !session.participants.contains(agentId)) return false

        val updatedVotes = session.votes + (agentId to position)
        val updatedSession = session.copy(votes = updatedVotes)
        
        _sessions.value = _sessions.value + (sessionId to updatedSession)
        
        // Auto-resolve if all voted
        if (updatedVotes.size == session.participants.size) {
            resolveSession(sessionId)
        }
        
        return true
    }

    private fun resolveSession(sessionId: String) {
        val session = _sessions.value[sessionId] ?: return
        val normalized = session.votes.values.map { it.trim().uppercase() }
        val counts = normalized.groupingBy { it }.eachCount()
        val maxVotes = counts.values.maxOrNull() ?: 0
        val outcome = when {
            counts.size == 1 -> CouncilOutcome.CONSENSUS
            maxVotes > session.participants.size / 2 -> CouncilOutcome.PARTIAL_CONSENSUS
            else -> CouncilOutcome.DISAGREEMENT
        }
        
        _sessions.value = _sessions.value + (sessionId to session.copy(outcome = outcome, isActive = false))
    }
}
