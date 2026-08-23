package com.shoshiluna.executive.core.audit

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

data class DecisionExplanation(
    val id: String = UUID.randomUUID().toString(),
    val agentId: String,
    val objective: String,
    val inputs: List<String>,
    val evidenceIds: List<String>,
    val selectedAction: String,
    val reason: String,
    val risk: String,
    val confidence: Float,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * ShoshiLuna Explainability Engine.
 * Answers: "Why did the agent do this?"
 */
object ExplainabilityEngine {
    
    private val _decisions = MutableStateFlow<Map<String, DecisionExplanation>>(emptyMap())
    val decisions = _decisions.asStateFlow()

    fun recordDecision(explanation: DecisionExplanation) {
        _decisions.value = _decisions.value + (explanation.id to explanation)
    }

    fun getExplanation(decisionId: String): DecisionExplanation? {
        return _decisions.value[decisionId]
    }
}
