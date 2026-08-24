package com.shoshiluna.executive.core.fabric

import android.content.Context
import com.shoshiluna.executive.core.business.SpendStatus
import com.shoshiluna.executive.core.business.TreasuryLedger
import com.shoshiluna.executive.core.business.TreasurySpendProposal
import com.shoshiluna.executive.core.network.AiCallResult
import com.shoshiluna.executive.core.network.AiProviderId
import com.shoshiluna.executive.core.network.ChatMessage
import com.shoshiluna.executive.core.network.OpenAiClient
import com.shoshiluna.executive.core.security.ApiKeyManager
import com.shoshiluna.executive.core.security.ApprovalRiskLevel
import com.shoshiluna.executive.core.security.HumanApprovalGateway
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class BrainAssignment(val role: String, val modelId: String, val providerId: String, val objective: String)
data class BrainContribution(val assignment: BrainAssignment, val result: AiCallResult)
data class BrainCouncilResult(val finalResult: AiCallResult, val contributions: List<BrainContribution>, val usedModels: Set<String>)

/** Uses distinct configured models for specialist work, then asks a separate judge to synthesize. */
object AgencyBrainCouncil {
    @Volatile private var appContext: Context? = null
    fun initialize(context: Context) { appContext = context.applicationContext }
    private val roles = listOf(
        "RESEARCHER" to "Find relevant facts, evidence gaps and current constraints.",
        "SPECIALIST" to "Produce the strongest domain-specific solution and concrete execution plan.",
        "RED_TEAM" to "Challenge assumptions, find failure modes, security risks and hidden costs."
    )

    suspend fun execute(topic: String, sharedContext: List<String>, missionId: String): BrainCouncilResult = coroutineScope {
        val context = appContext ?: return@coroutineScope BrainCouncilResult(
            AiCallResult.Failure(com.shoshiluna.executive.core.network.AiConnectionState.OFFLINE, "Brain council is not initialized."), emptyList(), emptySet())
        val configured = ApiKeyManager.configuredProviders(context)
        if (configured.isEmpty()) return@coroutineScope BrainCouncilResult(
            AiCallResult.Failure(com.shoshiluna.executive.core.network.AiConnectionState.API_KEY_MISSING, "No AI provider key is configured."), emptyList(), emptySet())
        val task = FabricTaskClassifier.classify(topic)
        val providerIds = configured.map { it.fabricId }.toSet()
        val decision = MetaRouter.route(task.copy(consensusCount = 4), providerIds).getOrElse {
            return@coroutineScope BrainCouncilResult(AiCallResult.Failure(com.shoshiluna.executive.core.network.AiConnectionState.ERROR, it.message ?: "No eligible model"), emptyList(), emptySet())
        }
        val distinct = (listOf(decision.primary) + decision.fallbacks).distinctBy { it.model.id }.take(roles.size)
        val contributions = distinct.mapIndexed { index, candidate ->
            val (role, objective) = roles[index]
            async {
                val assignment = BrainAssignment(role, candidate.model.id, candidate.model.providerId, objective)
                BrainContribution(assignment, callModel(context, configured, candidate.model,
                    "Mission $missionId\nRole: $role\nObjective: $objective\nTask: $topic\nShared context:\n${sharedContext.joinToString("\n")}"))
            }
        }.awaitAll()
        val successful = contributions.mapNotNull { (it.result as? AiCallResult.Success)?.let { success -> it.assignment to success.text } }
        if (successful.isEmpty()) return@coroutineScope BrainCouncilResult(
            contributions.lastOrNull()?.result ?: AiCallResult.Failure(com.shoshiluna.executive.core.network.AiConnectionState.ERROR, "Brain council produced no live result"), contributions, emptySet())
        val judge = decision.primary.model
        val evidencePacket = successful.joinToString("\n\n") { (assignment, text) -> "[${assignment.role} • ${assignment.modelId}]\n$text" }
        val finalResult = callModel(context, configured, judge,
            "Act as the verifier/judge. Synthesize the council evidence below into one concrete answer. Resolve disagreements, preserve uncertainty, reject unsupported claims, and list next actions.\n\n$evidencePacket")
        FabricAuditLedger.append("BRAIN_COUNCIL", task.id, judge.id, decision.policyDecisionId,
            "MODELS=${successful.joinToString { it.first.modelId }};JUDGE=${judge.id}")
        BrainCouncilResult(finalResult, contributions, successful.map { it.first.modelId }.toSet())
    }

    private suspend fun callModel(context: Context, configured: Set<AiProviderId>, model: ModelDescriptor, prompt: String): AiCallResult {
        val provider = configured.firstOrNull { it.fabricId == model.providerId }
            ?: return AiCallResult.Failure(com.shoshiluna.executive.core.network.AiConnectionState.API_KEY_MISSING, "Provider unavailable")
        return OpenAiClient.getCompletionResult(listOf(ChatMessage("user", prompt)), providerOverride = provider,
            resolvedModelOverride = if (provider == AiProviderId.OPENROUTER) model.id else model.id.substringAfter('/'))
    }

    private val AiProviderId.fabricId: String get() = when (this) {
        AiProviderId.OPENAI -> "openai"; AiProviderId.ANTHROPIC -> "anthropic"
        AiProviderId.GOOGLE_GEMINI -> "google_gemini"; AiProviderId.OPENROUTER -> "openrouter"; AiProviderId.CUSTOM -> "custom"
    }
}

data class AiProcurementProposal(val spendProposalId: String, val approvalId: String, val modelId: String, val monthlyCostUsd: Double, val availableFundedUsd: Double)

/** SHOSHI may discover and justify growth purchases, but cannot charge or hold payment credentials. */
object AiProcurementPlanner {
    fun propose(missionId: String, modelId: String, monthlyCostUsd: Double, expectedBenefit: String): Result<AiProcurementProposal> {
        require(monthlyCostUsd > 0.0 && expectedBenefit.isNotBlank())
        val model = ModelRegistry.get(modelId) ?: return Result.failure(IllegalArgumentException("Model is not documented"))
        val available = TreasuryLedger.availableFundedBalance()
        if (monthlyCostUsd > available) return Result.failure(IllegalStateException("Insufficient FUNDED treasury balance"))
        val spend = TreasurySpendProposal(missionId = missionId, requesterAgentId = "SHOSHI", amount = monthlyCostUsd,
            description = "AI capability purchase: ${model.displayName}. Benefit: $expectedBenefit", status = SpendStatus.PROPOSED)
        TreasuryLedger.proposeSpend(spend)
        val approval = HumanApprovalGateway.requestApproval("רכישת גישת AI: ${model.displayName} — $$monthlyCostUsd",
            "SHOSHI", ApprovalRiskLevel.HIGH, "Uses verified FUNDED treasury; expected benefit: $expectedBenefit", missionId)
        FabricAuditLedger.append("AI_PROCUREMENT_PROPOSED", missionId, modelId, approval, "AMOUNT_USD=$monthlyCostUsd;NO_PAYMENT_EXECUTED")
        return Result.success(AiProcurementProposal(spend.id, approval, modelId, monthlyCostUsd, available))
    }
}

data class GrowthCapabilityNeed(
    val id: String = UUID.randomUUID().toString(),
    val missionId: String,
    val modelId: String,
    val quotedMonthlyUsd: Double,
    val quoteEvidence: String,
    val expectedBenefit: String
)

/** Queues evidence-backed capability needs before revenue exists and proposes them only after the 5% fund is truly FUNDED. */
object AgencyGrowthAutopilot {
    private val queued = ConcurrentHashMap<String, GrowthCapabilityNeed>()
    private val proposed = ConcurrentHashMap.newKeySet<String>()

    fun registerNeed(need: GrowthCapabilityNeed) {
        require(need.quotedMonthlyUsd > 0.0 && need.quoteEvidence.isNotBlank() && need.expectedBenefit.isNotBlank())
        requireNotNull(ModelRegistry.get(need.modelId)) { "Model must be documented before procurement" }
        queued[need.id] = need
        FabricAuditLedger.append("AI_GROWTH_NEED_QUEUED", need.missionId, need.modelId, outcome = "WAITING_FOR_5_PERCENT_FUNDED")
    }

    /** Returns proposals created now. Never performs a purchase. */
    fun evaluateFundedGrowthPool(): List<AiProcurementProposal> = queued.values
        .sortedBy { it.quotedMonthlyUsd }
        .mapNotNull { need ->
            if (need.id in proposed) return@mapNotNull null
            AiProcurementPlanner.propose(need.missionId, need.modelId, need.quotedMonthlyUsd,
                "${need.expectedBenefit}; quote evidence=${need.quoteEvidence}").getOrNull()?.also { proposed += need.id }
        }

    fun resetForTest() { queued.clear(); proposed.clear() }
}

/** Converts an owner-approved growth proposal into the durable financial pipeline; payment still requires biometric proof and a configured adapter. */
object AiPurchasePreparation {
    fun prepareFinancialProposal(procurement: AiProcurementProposal, missionId: String, vendorAccount: String): Result<com.shoshiluna.executive.core.business.FinancialProposal> {
        if (!HumanApprovalGateway.isApproved(procurement.approvalId)) return Result.failure(SecurityException("Owner approval required"))
        val spend = TreasuryLedger.spendProposals.value.firstOrNull { it.id == procurement.spendProposalId }
            ?: return Result.failure(IllegalArgumentException("Spend proposal missing"))
        if (spend.status != SpendStatus.PROPOSED) return Result.failure(SecurityException("Spend proposal is not eligible"))
        if (procurement.monthlyCostUsd > procurement.availableFundedUsd || procurement.monthlyCostUsd > TreasuryLedger.availableFundedBalance() + spend.amount) {
            return Result.failure(SecurityException("5% FUNDED growth balance is insufficient"))
        }
        TreasuryLedger.updateSpendStatus(spend.id, SpendStatus.APPROVED)
        return Result.success(com.shoshiluna.executive.core.business.FinancialProposal(
            proposalId = spend.id, missionId = missionId, correlationId = procurement.approvalId,
            amountMinorUnits = (procurement.monthlyCostUsd * 100).toLong(), currency = "USD",
            sourceAccount = "AGENCY_5_PERCENT_GROWTH_FUND", destinationAccount = vendorAccount,
            mode = com.shoshiluna.executive.core.business.ExecutionMode.REAL
        ))
    }
}
