package com.shoshiluna.executive.core.agent

import java.util.concurrent.ConcurrentHashMap

enum class DebateStatus {
    ACTIVE,
    CONSENSUS,
    DISAGREEMENT,
    MAX_ROUNDS_REACHED,
    TERMINATED
}

/**
 * ShoshiLuna Debate Manager.
 * Handles bounded round-limited arguments between agents.
 */
object DebateManager {
    
    private const val DEFAULT_MAX_ROUNDS = 3
    private val activeDebates = ConcurrentHashMap<String, Int>() // conversationId to currentRound

    fun initiateDebate(conversationId: String): Boolean {
        if (activeDebates.containsKey(conversationId)) return false
        activeDebates[conversationId] = 1
        return true
    }

    fun submitArgument(message: InternalMessage): DebateStatus {
        val currentRound = activeDebates[message.conversationId] ?: return DebateStatus.TERMINATED
        
        if (currentRound >= DEFAULT_MAX_ROUNDS) {
            activeDebates.remove(message.conversationId)
            return DebateStatus.MAX_ROUNDS_REACHED
        }

        // Logic for evaluating argument and incrementing round
        activeDebates[message.conversationId] = currentRound + 1
        
        return DebateStatus.ACTIVE
    }

    fun resolve(conversationId: String, status: DebateStatus) {
        activeDebates.remove(conversationId)
        // Record final position in Shared Memory
        val resultSummary = when (status) {
            DebateStatus.CONSENSUS -> "Debate Resolved: Consensus achieved."
            DebateStatus.DISAGREEMENT -> "Debate Resolved: Irreconcilable differences."
            DebateStatus.MAX_ROUNDS_REACHED -> "Debate Terminated: Round limit reached."
            else -> "Debate Terminated: External intervention."
        }
        
        SovereignMemoryVault.record(
            SovereignMemory(
                partition = MemoryPartition.EXECUTIVE_SHARED,
                agentId = "SYSTEM",
                content = resultSummary
            )
        )
    }
}
