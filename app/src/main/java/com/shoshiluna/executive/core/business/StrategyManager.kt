package com.shoshiluna.executive.core.business

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

enum class StrategyStatus {
    ACTIVE,
    EXPERIMENTAL,
    PAUSED,
    RETIRED,
    ROLLED_BACK
}

data class BusinessStrategy(
    val id: String = UUID.randomUUID().toString(),
    val version: Int = 1,
    val title: String,
    val hypothesis: String,
    val creatorAgentId: String,
    val performanceScore: Float = 1.0f,
    val status: StrategyStatus = StrategyStatus.EXPERIMENTAL,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * ShoshiLuna Strategy Manager.
 * Handles versioning and rollback for business strategies.
 */
object StrategyManager {
    
    private val _strategies = MutableStateFlow<Map<String, BusinessStrategy>>(emptyMap())
    val strategies = _strategies.asStateFlow()

    fun createStrategy(title: String, hypothesis: String, agentId: String): BusinessStrategy {
        val strategy = BusinessStrategy(title = title, hypothesis = hypothesis, creatorAgentId = agentId)
        _strategies.value = _strategies.value + (strategy.id to strategy)
        return strategy
    }

    fun updateVersion(strategyId: String, nextHypothesis: String): BusinessStrategy? {
        val current = _strategies.value[strategyId] ?: return null
        val next = current.copy(
            version = current.version + 1,
            hypothesis = nextHypothesis,
            createdAt = System.currentTimeMillis()
        )
        _strategies.value = _strategies.value + (strategyId to next)
        return next
    }

    fun rollback(strategyId: String) {
        val current = _strategies.value[strategyId] ?: return
        if (current.version > 1) {
            _strategies.value = _strategies.value + (strategyId to current.copy(
                version = current.version - 1,
                status = StrategyStatus.ROLLED_BACK
            ))
        }
    }
}
