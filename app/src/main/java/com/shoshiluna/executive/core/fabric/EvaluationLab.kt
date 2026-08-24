package com.shoshiluna.executive.core.fabric

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

enum class BenchmarkDimension { REASONING, CODING, HALLUCINATION, INSTRUCTION_FOLLOWING, HEBREW, ENGLISH, VISION, LONG_CONTEXT, TOOL_USE, LATENCY, UPTIME, COST_EFFICIENCY, SECURITY }
data class BenchmarkEvidence(val id: String = UUID.randomUUID().toString(), val modelId: String, val dimension: BenchmarkDimension, val score: Double, val testCaseId: String, val rawEvidenceHash: String, val timestamp: Long = System.currentTimeMillis()) {
    init { require(score in 0.0..1.0 && rawEvidenceHash.isNotBlank()) }
}

object EvaluationLaboratory {
    private val _evidence = MutableStateFlow<List<BenchmarkEvidence>>(emptyList())
    val evidence = _evidence.asStateFlow()
    @Synchronized fun record(result: BenchmarkEvidence) { _evidence.value = _evidence.value + result }
    fun matrix(modelId: String) = _evidence.value.filter { it.modelId == modelId }.groupBy { it.dimension }.mapValues { (_, rows) -> rows.map { it.score }.average() }
    fun verifiedDimensions(modelId: String) = matrix(modelId).keys
}

data class ProviderHealth(val providerId: String, val status: RegistryStatus, val latencyP50Ms: Long?, val reliability: Double?, val consecutiveFailures: Int, val lastCheckedAt: Long)

object ProviderHealthMonitor {
    private val _health = MutableStateFlow<Map<String, ProviderHealth>>(emptyMap())
    val health = _health.asStateFlow()
    @Synchronized fun record(providerId: String, success: Boolean, latencyMs: Long) {
        val previous = _health.value[providerId]
        val failures = if (success) 0 else (previous?.consecutiveFailures ?: 0) + 1
        val oldReliability = previous?.reliability ?: .5
        val reliability = oldReliability * .8 + (if (success) 1.0 else 0.0) * .2
        val status = if (success) RegistryStatus.CONNECTED else if (failures >= 3) RegistryStatus.FAILED else RegistryStatus.CONFIGURED
        _health.value = _health.value + (providerId to ProviderHealth(providerId, status, latencyMs, reliability, failures, System.currentTimeMillis()))
    }
}

object CostController {
    fun estimateUsd(model: ModelDescriptor, inputTokens: Int, outputTokens: Int): Double? {
        val input = model.cost.inputUsdPerMillion ?: return null; val output = model.cost.outputUsdPerMillion ?: return null
        return inputTokens / 1_000_000.0 * input + outputTokens / 1_000_000.0 * output
    }
    fun withinBudget(model: ModelDescriptor, inputTokens: Int, outputTokens: Int, budget: Double?) = budget == null || estimateUsd(model, inputTokens, outputTokens)?.let { it <= budget } == true
}

object ConsensusEngine {
    data class ConsensusInput(val modelId: String, val answer: String, val verifierScore: Double)
    fun select(inputs: List<ConsensusInput>): ConsensusInput = inputs.maxWithOrNull(compareBy<ConsensusInput> { it.verifierScore }.thenBy { it.answer.length }) ?: error("No consensus inputs")
}
