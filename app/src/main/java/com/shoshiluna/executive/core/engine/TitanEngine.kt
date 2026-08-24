package com.shoshiluna.executive.core.engine

import android.content.Context
import com.shoshiluna.executive.core.agent.SovereignMemoryVault
import com.shoshiluna.executive.core.empire.EmpireMissionControl
import com.shoshiluna.executive.core.business.ArtifactFactory
import com.shoshiluna.executive.core.evolution.EvolutionEngine
import com.shoshiluna.executive.core.security.HumanApprovalGateway
import com.shoshiluna.executive.core.fabric.AgencyBrainCouncil
import com.shoshiluna.executive.core.fabric.AgencyGrowthAutopilot
import com.shoshiluna.executive.core.fabric.ModelRegistry
import com.shoshiluna.executive.core.business.BuiltInToolBootstrap
import com.shoshiluna.executive.core.network.OpenAiClient
import com.shoshiluna.executive.core.external.ExternalAgentGateway
import com.shoshiluna.executive.core.external.ExternalOperationJournal
import com.shoshiluna.executive.core.external.GenericHttpsAgentAdapter
import com.shoshiluna.executive.core.external.TrustedExternalAgentRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * ShoshiLuna Executive Engine.
 * Thread-safe atomic state management for the agency.
 */
object TitanEngine {
    private val startTime = System.currentTimeMillis()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var appContext: Context? = null

    private val _networkLatency = MutableStateFlow(0L)
    val networkLatency = _networkLatency.asStateFlow()

    private val _cpuLoad = MutableStateFlow(5)
    val cpuLoad = _cpuLoad.asStateFlow()

    private val _isChatActive = MutableStateFlow(false)
    val isChatActive = _isChatActive.asStateFlow()

    private val _systemStatus = MutableStateFlow("HEALTHY")
    val systemStatus = _systemStatus.asStateFlow()
    private val _initializationFailures = MutableStateFlow<Map<String, String>>(emptyMap())
    val initializationFailures = _initializationFailures.asStateFlow()

    fun initialize(context: Context) {
        appContext = context.applicationContext
        val failures = linkedMapOf<String, String>()
        fun boot(name: String, block: () -> Unit) {
            runCatching(block).onFailure { failures[name] = it::class.java.simpleName }
        }
        boot("missions") { EmpireMissionControl.initialize(context) }
        boot("artifacts") { ArtifactFactory.initialize(context) }
        boot("evolution") { EvolutionEngine.initialize(context) }
        boot("approvals") { HumanApprovalGateway.initialize(context) }
        boot("brain_council") { AgencyBrainCouncil.initialize(context) }
        boot("growth_fund") { AgencyGrowthAutopilot.initialize(context) }
        boot("model_registry") { ModelRegistry.initialize(context) }
        boot("tools") { BuiltInToolBootstrap.initialize() }
        boot("memory") { SovereignMemoryVault.initialize(context) }
        boot("ai_network") { OpenAiClient.initialize(context) }
        boot("external_journal") { ExternalOperationJournal.initialize(context) }
        boot("trusted_agents") { TrustedExternalAgentRegistry.initialize(context) }
        boot("external_gateway") { ExternalAgentGateway.registerAdapter(GenericHttpsAgentAdapter()) }
        _initializationFailures.value = failures
        _systemStatus.value = if (failures.isEmpty()) "HEALTHY" else "DEGRADED"
        // Core network + memory + controlled external-agent runtime are initialized from the application context.
    }

    fun setChatActive(active: Boolean) { 
        _isChatActive.value = active 
    }

    fun applicationContext(): Context? = appContext

    fun reportLatency(latency: Long) {
        _networkLatency.value = latency
    }

    fun updateCpuLoad(load: Int) {
        _cpuLoad.value = load
    }

    fun getUptimeString(): String {
        val seconds = (System.currentTimeMillis() - startTime) / 1000
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
    }
}
