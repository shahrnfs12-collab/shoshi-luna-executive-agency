package com.shoshiluna.executive.core.fabric

import java.util.UUID

enum class RegistryStatus { DISCOVERED, DOCUMENTED, CONFIGURED, CONNECTED, TESTED, VERIFIED, FAILED }
enum class TrustLevel { T0_UNTRUSTED, T1_PUBLIC_OPEN, T2_VERIFIED_PROVIDER, T3_SENSITIVE, T4_PRIVILEGED, T5_CRITICAL }
enum class DataSensitivity { PUBLIC, INTERNAL, CONFIDENTIAL, RESTRICTED, CRITICAL }
enum class ModelOwnership { OPEN_SOURCE, OPEN_WEIGHT, PROPRIETARY, UNKNOWN }
enum class ContentPolicyProfile { STANDARD, RESTRICTED, OPEN_POLICY, UNKNOWN }
enum class Modality { TEXT, IMAGE, AUDIO, VIDEO, FILE, EMBEDDING }
enum class FabricCapability {
    GENERAL_LLM, REASONING, CODING, VISION, IMAGE_GENERATION, VIDEO_GENERATION,
    SPEECH_TO_TEXT, TEXT_TO_SPEECH, TRANSLATION, EMBEDDINGS, SEARCH, AGENTS,
    RESEARCH, MATHEMATICS, CYBER_DEFENSE, DATA_ANALYSIS, FINANCE, DOCUMENT_INTELLIGENCE,
    COMPUTER_USE, ROBOTICS, TOOL_CALLING, STRUCTURED_OUTPUT, LONG_CONTEXT, LOCAL_INFERENCE
}

data class CostCard(val inputUsdPerMillion: Double?, val outputUsdPerMillion: Double?, val currency: String = "USD")
data class RuntimeMetrics(
    val latencyP50Ms: Long? = null,
    val throughputTokensPerSecond: Double? = null,
    val reliability: Double? = null,
    val lastSuccessAt: Long? = null,
    val consecutiveFailures: Int = 0
)

data class EvidenceReference(
    val sourceUrl: String,
    val sourceType: String,
    val verifiedAt: Long,
    val claim: String,
    val evidenceHash: String? = null
)

data class ModelDescriptor(
    val id: String,
    val providerId: String,
    val displayName: String,
    val version: String? = null,
    val capabilities: Set<FabricCapability>,
    val contextWindow: Int? = null,
    val inputModalities: Set<Modality> = setOf(Modality.TEXT),
    val outputModalities: Set<Modality> = setOf(Modality.TEXT),
    val toolCalling: Boolean? = null,
    val structuredOutput: Boolean? = null,
    val cost: CostCard = CostCard(null, null),
    val rateLimitDescription: String? = null,
    val privacyPolicyUrl: String? = null,
    val dataRetention: String? = null,
    val hostingRegions: Set<String> = emptySet(),
    val apiCompatibility: String,
    val ownership: ModelOwnership,
    val license: String? = null,
    val contentPolicy: ContentPolicyProfile = ContentPolicyProfile.UNKNOWN,
    val status: RegistryStatus,
    val trustLevel: TrustLevel,
    val evidence: List<EvidenceReference>,
    val metrics: RuntimeMetrics = RuntimeMetrics(),
    val lastVerifiedAt: Long? = null
) {
    init {
        require(id.isNotBlank() && providerId.isNotBlank() && evidence.isNotEmpty())
        require(metrics.reliability == null || metrics.reliability in 0.0..1.0)
        if (status == RegistryStatus.VERIFIED) require(lastVerifiedAt != null && metrics.lastSuccessAt != null)
    }
}

data class TaskRequirements(
    val id: String = UUID.randomUUID().toString(),
    val capabilities: Set<FabricCapability>,
    val inputModalities: Set<Modality> = setOf(Modality.TEXT),
    val sensitivity: DataSensitivity = DataSensitivity.INTERNAL,
    val minimumContext: Int = 0,
    val maximumCostUsd: Double? = null,
    val requiresTools: Boolean = false,
    val requiresStructuredOutput: Boolean = false,
    val allowOpenPolicy: Boolean = false,
    val requireLocal: Boolean = false,
    val consensusCount: Int = 1
)

data class RoutingWeights(
    val quality: Double = 0.24,
    val specialization: Double = 0.18,
    val reasoning: Double = 0.12,
    val latency: Double = 0.10,
    val reliability: Double = 0.12,
    val privacy: Double = 0.10,
    val cost: Double = 0.08,
    val context: Double = 0.03,
    val tools: Double = 0.03
)

data class RouteCandidate(val model: ModelDescriptor, val score: Double, val reasons: List<String>)
data class RoutingDecision(
    val taskId: String,
    val primary: RouteCandidate,
    val fallbacks: List<RouteCandidate>,
    val consensus: List<RouteCandidate>,
    val blockedModels: Map<String, String>,
    val policyDecisionId: String,
    val createdAt: Long = System.currentTimeMillis()
)
