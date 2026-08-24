package com.shoshiluna.executive.core.fabric

import kotlin.math.ln

object FabricTaskClassifier {
    fun classify(text: String, hasImage: Boolean = false, hasFile: Boolean = false): TaskRequirements {
        val value = text.lowercase()
        val caps = buildSet {
            add(FabricCapability.GENERAL_LLM)
            if (listOf("code", "קוד", "android", "bug", "apk").any(value::contains)) add(FabricCapability.CODING)
            if (listOf("research", "מחקר", "מקורות", "בדוק").any(value::contains)) add(FabricCapability.RESEARCH)
            if (listOf("reason", "היגיון", "אסטרטג", "math", "מתמט").any(value::contains)) add(FabricCapability.REASONING)
            if (hasImage) add(FabricCapability.VISION)
            if (hasFile) add(FabricCapability.DOCUMENT_INTELLIGENCE)
        }
        val sensitivity = when {
            listOf("סיסמה", "secret", "critical", "זהות", "תשלום").any(value::contains) -> DataSensitivity.CRITICAL
            listOf("פרטי", "confidential", "רפואי", "פיננס").any(value::contains) -> DataSensitivity.CONFIDENTIAL
            else -> DataSensitivity.INTERNAL
        }
        return TaskRequirements(capabilities = caps, inputModalities = buildSet { add(Modality.TEXT); if (hasImage) add(Modality.IMAGE); if (hasFile) add(Modality.FILE) }, sensitivity = sensitivity)
    }
}

object MetaRouter {
    fun route(task: TaskRequirements, configuredProviderIds: Set<String>, weights: RoutingWeights = RoutingWeights()): Result<RoutingDecision> {
        val blocked = linkedMapOf<String, String>()
        val candidates = ModelRegistry.all().mapNotNull { model ->
            val policy = FabricPolicyEngine.authorize(model, task)
            val blockReason = when {
                !policy.allowed -> policy.reason
                model.providerId !in configuredProviderIds && model.providerId != "local" -> "Provider not configured"
                !model.capabilities.containsAll(task.capabilities) -> "Missing capabilities"
                !model.inputModalities.containsAll(task.inputModalities.filter { it != Modality.FILE }.toSet()) -> "Unsupported modality"
                (model.contextWindow ?: 0) < task.minimumContext -> "Context too small"
                task.requiresTools && model.toolCalling != true -> "Tool calling required"
                task.requiresStructuredOutput && model.structuredOutput != true -> "Structured output required"
                model.status == RegistryStatus.FAILED -> "Provider health failed"
                else -> null
            }
            if (blockReason != null) { blocked[model.id] = blockReason; null } else score(model, task, weights)
        }.sortedByDescending { it.score }
        if (candidates.isEmpty()) return Result.failure(IllegalStateException("No policy-compliant configured model"))
        val primary = candidates.first(); val fallback = candidates.drop(1).take(3)
        val consensus = candidates.take(task.consensusCount.coerceIn(1, 5))
        val policyId = FabricPolicyEngine.authorize(primary.model, task).id
        val decision = RoutingDecision(task.id, primary, fallback, consensus, blocked, policyId)
        FabricAuditLedger.append("ROUTE_DECISION", task.id, primary.model.id, policyId, "PRIMARY=${primary.model.id};FALLBACKS=${fallback.joinToString { it.model.id }}")
        return Result.success(decision)
    }

    private fun score(model: ModelDescriptor, task: TaskRequirements, w: RoutingWeights): RouteCandidate {
        val verified = when (model.status) { RegistryStatus.VERIFIED -> 1.0; RegistryStatus.TESTED -> .9; RegistryStatus.CONNECTED -> .8; RegistryStatus.CONFIGURED -> .65; RegistryStatus.DOCUMENTED -> .45; else -> .25 }
        val specialization = task.capabilities.count { it in model.capabilities }.toDouble() / task.capabilities.size.coerceAtLeast(1)
        val reasoning = if (FabricCapability.REASONING in model.capabilities) 1.0 else .45
        val latency = model.metrics.latencyP50Ms?.let { 1.0 / (1.0 + it / 1500.0) } ?: .5
        val reliability = model.metrics.reliability ?: .5
        val privacy = when (model.trustLevel) { TrustLevel.T5_CRITICAL -> 1.0; TrustLevel.T4_PRIVILEGED -> .95; TrustLevel.T3_SENSITIVE -> .9; TrustLevel.T2_VERIFIED_PROVIDER -> .8; TrustLevel.T1_PUBLIC_OPEN -> .55; TrustLevel.T0_UNTRUSTED -> 0.0 }
        val avgCost = listOfNotNull(model.cost.inputUsdPerMillion, model.cost.outputUsdPerMillion).average().takeUnless(Double::isNaN)
        val cost = avgCost?.let { 1.0 / (1.0 + ln(1.0 + it)) } ?: .4
        val context = model.contextWindow?.let { minOf(1.0, it.toDouble() / maxOf(1, task.minimumContext).coerceAtLeast(128_000)) } ?: .2
        val tools = if (!task.requiresTools || model.toolCalling == true) 1.0 else 0.0
        val total = w.quality * verified + w.specialization * specialization + w.reasoning * reasoning + w.latency * latency + w.reliability * reliability + w.privacy * privacy + w.cost * cost + w.context * context + w.tools * tools
        return RouteCandidate(model, total, listOf("status=$verified", "specialization=$specialization", "reliability=$reliability", "privacy=$privacy", "cost=$cost"))
    }
}
