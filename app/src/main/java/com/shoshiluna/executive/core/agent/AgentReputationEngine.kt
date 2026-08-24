package com.shoshiluna.executive.core.agent

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AgentReputation(
    val agentId: String,
    val successRate: Float = 1.0f,
    val evidenceQuality: Float = 1.0f,
    val reliabilityTrend: List<Float> = emptyList(),
    val totalTasks: Int = 0,
    val failedTasks: Int = 0
)

/**
 * ShoshiLuna Agent Reputation Engine.
 * Tracks performance and reliability metrics for SHOSHI and LUNA.
 */
object AgentReputationEngine {
    
    private val _reputations = MutableStateFlow<Map<String, AgentReputation>>(
        mapOf(
            ExecutiveAgent.SHOSHI.id to AgentReputation(ExecutiveAgent.SHOSHI.id),
            ExecutiveAgent.LUNA.id to AgentReputation(ExecutiveAgent.LUNA.id)
        )
    )
    val reputations = _reputations.asStateFlow()

    fun recordTaskOutcome(agentId: String, success: Boolean, quality: Float) {
        val current = _reputations.value[agentId] ?: return
        val updated = current.copy(
            totalTasks = current.totalTasks + 1,
            failedTasks = if (success) current.failedTasks else current.failedTasks + 1,
            evidenceQuality = (current.evidenceQuality + quality) / 2,
            reliabilityTrend = current.reliabilityTrend + (if (success) 1.0f else 0.0f)
        )
        _reputations.value = _reputations.value + (agentId to updated)
    }

    fun getScore(agentId: String): Float {
        val rep = _reputations.value[agentId] ?: return 0.0f
        if (rep.totalTasks == 0) return 0.0f
        return (rep.totalTasks - rep.failedTasks).toFloat() / rep.totalTasks * rep.evidenceQuality
    }
}
