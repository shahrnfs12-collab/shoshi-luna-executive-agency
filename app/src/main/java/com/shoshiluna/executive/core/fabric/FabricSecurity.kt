package com.shoshiluna.executive.core.fabric

import android.content.Context
import com.shoshiluna.executive.core.network.AiProviderId
import com.shoshiluna.executive.core.security.ApiKeyManager
import java.security.MessageDigest
import java.util.UUID

data class PolicyDecision(val id: String = UUID.randomUUID().toString(), val allowed: Boolean, val reason: String, val minimumTrust: TrustLevel)

object FabricPolicyEngine {
    fun authorize(model: ModelDescriptor, task: TaskRequirements): PolicyDecision {
        val minimum = when (task.sensitivity) {
            DataSensitivity.PUBLIC -> TrustLevel.T1_PUBLIC_OPEN
            DataSensitivity.INTERNAL -> TrustLevel.T2_VERIFIED_PROVIDER
            DataSensitivity.CONFIDENTIAL -> TrustLevel.T2_VERIFIED_PROVIDER
            DataSensitivity.RESTRICTED -> TrustLevel.T3_SENSITIVE
            DataSensitivity.CRITICAL -> TrustLevel.T4_PRIVILEGED
        }
        if (model.trustLevel.ordinal < minimum.ordinal) return PolicyDecision(allowed = false, reason = "Trust below $minimum", minimumTrust = minimum)
        if (model.contentPolicy == ContentPolicyProfile.OPEN_POLICY && !task.allowOpenPolicy) return PolicyDecision(allowed = false, reason = "Open-policy pool not authorized for task", minimumTrust = minimum)
        if (task.requireLocal && FabricCapability.LOCAL_INFERENCE !in model.capabilities) return PolicyDecision(allowed = false, reason = "Local-only task", minimumTrust = minimum)
        if (task.sensitivity >= DataSensitivity.RESTRICTED && model.dataRetention != "ZERO") return PolicyDecision(allowed = false, reason = "Restricted data requires verified zero retention", minimumTrust = minimum)
        return PolicyDecision(allowed = true, reason = "Policy constraints satisfied", minimumTrust = minimum)
    }

    fun trustForAction(changesMoney: Boolean, changesIdentity: Boolean, productionWrite: Boolean, sensitiveFiles: Boolean): TrustLevel = when {
        changesMoney || changesIdentity -> TrustLevel.T5_CRITICAL
        productionWrite -> TrustLevel.T4_PRIVILEGED
        sensitiveFiles -> TrustLevel.T3_SENSITIVE
        else -> TrustLevel.T2_VERIFIED_PROVIDER
    }
}

@JvmInline value class CredentialReference(val providerId: String)

/** The model/router receives only CredentialReference. Secret material exists only inside this callback. */
object SecureCredentialBroker {
    fun reference(provider: AiProviderId) = CredentialReference(provider.name)

    inline fun <T> withCredential(context: Context, reference: CredentialReference, block: (String) -> T): T {
        val provider = AiProviderId.valueOf(reference.providerId)
        val secret = ApiKeyManager.getProviderKey(context, provider) ?: error("Credential is not configured")
        return try { block(secret) } finally { /* Secret is never returned, logged, cached or inserted into prompts. */ }
    }
}

data class FabricAuditRecord(
    val id: String,
    val timestamp: Long,
    val action: String,
    val taskId: String?,
    val modelId: String?,
    val policyDecisionId: String?,
    val outcome: String,
    val previousHash: String,
    val hash: String
)

/** Hash-chained, append-only audit ledger. Tampering or reordering is detectable by verifyChain(). */
object FabricAuditLedger {
    private val records = mutableListOf<FabricAuditRecord>()
    @Synchronized fun append(action: String, taskId: String? = null, modelId: String? = null, policyDecisionId: String? = null, outcome: String): FabricAuditRecord {
        val id = UUID.randomUUID().toString(); val now = System.currentTimeMillis(); val previous = records.lastOrNull()?.hash.orEmpty()
        val hash = sha256("$id|$now|$action|$taskId|$modelId|$policyDecisionId|$outcome|$previous")
        return FabricAuditRecord(id, now, action, taskId, modelId, policyDecisionId, outcome, previous, hash).also(records::add)
    }
    @Synchronized fun recent(limit: Int = 100) = records.takeLast(limit).reversed()
    @Synchronized fun verifyChain(): Boolean = records.indices.all { index ->
        val record = records[index]; val expectedPrevious = records.getOrNull(index - 1)?.hash.orEmpty()
        record.previousHash == expectedPrevious && record.hash == sha256("${record.id}|${record.timestamp}|${record.action}|${record.taskId}|${record.modelId}|${record.policyDecisionId}|${record.outcome}|${record.previousHash}")
    }
    private fun sha256(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
}
