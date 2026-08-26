package com.shoshiluna.executive.core.business

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.gson.Gson
import com.shoshiluna.executive.core.agent.ExecutiveAgent
import com.shoshiluna.executive.core.agent.MemoryPartition
import com.shoshiluna.executive.core.agent.SovereignMemory
import com.shoshiluna.executive.core.agent.SovereignMemoryVault
import com.shoshiluna.executive.core.audit.AuditTrail
import com.shoshiluna.executive.core.audit.ExecutiveAudit
import com.shoshiluna.executive.core.network.AiCallResult
import com.shoshiluna.executive.core.network.AiProviderProtocols
import com.shoshiluna.executive.core.network.ChatMessage
import com.shoshiluna.executive.core.network.ResilientAiClient
import com.shoshiluna.executive.core.security.ApiKeyManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import java.util.concurrent.TimeUnit

enum class RevenueAgentRole { SCOUT, VALIDATOR, BUILDER, GROWTH }
enum class RevenueAgentRunStatus { READY, RUNNING, REPORTED, FAILED }

data class RevenueLearningAgent(
    val id: String,
    val role: RevenueAgentRole,
    val responsibility: String,
    val status: RevenueAgentRunStatus = RevenueAgentRunStatus.READY,
    val lastRunAt: Long? = null,
    val lastReport: String? = null,
    val lastEvidenceId: String? = null
)

/** A report is advisory until its evidence is independently verified. */
data class RevenueAgentReport(
    val id: String = UUID.randomUUID().toString(),
    val cycleId: String,
    val agentId: String,
    val role: RevenueAgentRole,
    val recipient: String = ExecutiveAgent.SHOSHI.id,
    val content: String,
    val evidenceId: String,
    val requiresOwnerApprovalForExternalAction: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

object RevenueAgentNetwork {
    const val DAILY_REVENUE_TARGET_USD = 1_000.0
    private const val IMMEDIATE_WORK = "revenue-agent-network-immediate"
    private const val PERIODIC_WORK = "revenue-agent-network-periodic"
    private const val PREFS = "revenue_agent_network_v1"
    private const val REPORTS_KEY = "reports_json"
    private val gson = Gson()

    private val initialAgents = listOf(
        RevenueLearningAgent("revenue-scout", RevenueAgentRole.SCOUT, "Discover legal online revenue opportunities and identify missing evidence."),
        RevenueLearningAgent("revenue-validator", RevenueAgentRole.VALIDATOR, "Challenge demand, competition, cost, risk and evidence quality."),
        RevenueLearningAgent("revenue-builder", RevenueAgentRole.BUILDER, "Design a concrete service or product plan without executing external actions."),
        RevenueLearningAgent("revenue-growth", RevenueAgentRole.GROWTH, "Design ethical acquisition tests, pricing and conversion measurements.")
    )
    private val _agents = MutableStateFlow(initialAgents.associateBy { it.id })
    val agents = _agents.asStateFlow()
    private val _reports = MutableStateFlow<List<RevenueAgentReport>>(emptyList())
    val reports = _reports.asStateFlow()

    fun initialize(context: Context) {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(REPORTS_KEY, null)
        val restored = runCatching { gson.fromJson(saved, Array<RevenueAgentReport>::class.java)?.toList() }.getOrNull().orEmpty()
        _reports.value = restored.takeLast(100)
        if (ApiKeyManager.configuredProviders(context).isNotEmpty()) activate(context)
    }

    fun activate(context: Context): Boolean {
        if (ApiKeyManager.configuredProviders(context).isEmpty()) return false
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val work = WorkManager.getInstance(context.applicationContext)
        work.enqueueUniqueWork(
            IMMEDIATE_WORK,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<RevenueAgentWorker>().setConstraints(constraints).build()
        )
        work.enqueueUniquePeriodicWork(
            PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<RevenueAgentWorker>(6, TimeUnit.HOURS).setConstraints(constraints).build()
        )
        AuditTrail.append(ExecutiveAudit(agentId = ExecutiveAgent.SHOSHI.id, action = "ACTIVATE_REVENUE_AGENT_NETWORK", result = "SCHEDULED: 4 advisory agents; external and financial actions remain approval-gated"))
        return true
    }

    internal fun markRunning(agentId: String) = updateAgent(agentId) { it.copy(status = RevenueAgentRunStatus.RUNNING, lastRunAt = System.currentTimeMillis()) }

    internal fun recordReport(context: Context, agentId: String, cycleId: String, content: String): RevenueAgentReport {
        val agent = requireNotNull(_agents.value[agentId])
        val evidenceId = EvidenceRegistry.record(EvidenceCategory.AI_INFERENCE, content, "RevenueAgent:${agent.role}", cycleId)
        val report = RevenueAgentReport(cycleId = cycleId, agentId = agentId, role = agent.role, content = content, evidenceId = evidenceId)
        _reports.value = (_reports.value + report).takeLast(100)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(REPORTS_KEY, gson.toJson(_reports.value)).apply()
        updateAgent(agentId) { it.copy(status = RevenueAgentRunStatus.REPORTED, lastReport = content, lastEvidenceId = evidenceId) }
        SovereignMemoryVault.record(
            SovereignMemory(
                partition = MemoryPartition.EXECUTIVE_SHARED,
                agentId = ExecutiveAgent.SHOSHI.id,
                content = "[${agent.role} REPORT — UNVERIFIED AI INFERENCE] $content",
                missionId = cycleId,
                evidenceCategory = EvidenceCategory.AI_INFERENCE
            )
        )
        AuditTrail.append(ExecutiveAudit(agentId = agentId, missionId = cycleId, action = "REPORT_TO_SHOSHI", result = "RECORDED_AS_AI_INFERENCE:$evidenceId"))
        return report
    }

    internal fun markFailed(agentId: String, reason: String) {
        updateAgent(agentId) { it.copy(status = RevenueAgentRunStatus.FAILED, lastReport = reason) }
        AuditTrail.append(ExecutiveAudit(agentId = agentId, action = "REVENUE_AGENT_RUN", result = "FAILED:$reason"))
    }

    internal fun resetForTest() { _agents.value = initialAgents.associateBy { it.id }; _reports.value = emptyList() }
    private fun updateAgent(id: String, block: (RevenueLearningAgent) -> RevenueLearningAgent) {
        val current = _agents.value[id] ?: return
        _agents.value = _agents.value + (id to block(current))
    }
}

class RevenueAgentWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (ApiKeyManager.configuredProviders(applicationContext).isEmpty()) return Result.failure()
        val cycleId = "revenue-cycle-${UUID.randomUUID()}"
        RevenueAgentNetwork.agents.value.values.forEach { agent ->
            RevenueAgentNetwork.markRunning(agent.id)
            val result = ResilientAiClient.getCompletionResult(
                applicationContext,
                listOf(
                    ChatMessage("system", systemPrompt(agent)),
                    ChatMessage("user", "Produce this cycle's report for SHOSHI. Daily revenue target: USD ${RevenueAgentNetwork.DAILY_REVENUE_TARGET_USD.toInt()}.")
                ),
                AiProviderProtocols.OPENROUTER_CHAT_MODEL
            )
            when (result) {
                is AiCallResult.Success -> RevenueAgentNetwork.recordReport(applicationContext, agent.id, cycleId, result.text)
                is AiCallResult.Failure -> {
                    RevenueAgentNetwork.markFailed(agent.id, result.safeMessage)
                    if (result.httpCode == 429) return Result.retry()
                }
            }
        }
        return Result.success()
    }

    private fun systemPrompt(agent: RevenueLearningAgent): String = """
        You are ${agent.role}, one of four advisory revenue-learning agents reporting to SHOSHI.
        Responsibility: ${agent.responsibility}
        Find lawful, ethical, low-cost paths toward a USD 1,000/day target. The target is not a guarantee.
        Never claim you browsed, contacted anyone, created an account, published, purchased, transferred money,
        received revenue, or verified demand unless supplied with verifiable evidence. Never request or expose API keys,
        bank routing data, wallet private keys or seed phrases. Separate FACT, ASSUMPTION, AI_INFERENCE and REQUIRED_EVIDENCE.
        Return: opportunity, customer, offer, expected price, estimated cost, evidence gaps, risks, next reversible test,
        and which exact action requires owner approval. Do not execute external or financial actions.
    """.trimIndent()
}
