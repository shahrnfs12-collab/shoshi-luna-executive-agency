package com.shoshiluna.executive.core.business

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine

data class AgentRoi(
    val agentId: String,
    val revenueGenerated: Double,
    val timeSpentHours: Double,
    val roi: Double
)

/**
 * ShoshiLuna ROI Engine.
 * Tracks financial performance per agent and strategy.
 */
object RoiEngine {
    
    private val _agentMetrics = MutableStateFlow<Map<String, AgentRoi>>(emptyMap())
    val agentMetrics = _agentMetrics.asStateFlow()

    fun recordActivity(agentId: String, revenue: Double, hours: Double) {
        val current = _agentMetrics.value[agentId] ?: AgentRoi(agentId, 0.0, 0.0, 0.0)
        val nextRevenue = current.revenueGenerated + revenue
        val nextHours = current.timeSpentHours + hours
        val nextRoi = if (nextHours > 0) nextRevenue / nextHours else 0.0
        
        _agentMetrics.value = _agentMetrics.value + (agentId to current.copy(
            revenueGenerated = nextRevenue,
            timeSpentHours = nextHours,
            roi = nextRoi
        ))
    }
}
