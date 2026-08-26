package com.shoshiluna.executive.core.engine

import android.content.Context
import com.shoshiluna.executive.core.agent.SovereignMemoryVault
import com.shoshiluna.executive.core.empire.EmpireMissionControl
import com.shoshiluna.executive.core.business.ArtifactFactory
import com.shoshiluna.executive.core.evolution.EvolutionEngine
import com.shoshiluna.executive.core.security.HumanApprovalGateway
import com.shoshiluna.executive.core.business.BuiltInToolBootstrap
import com.shoshiluna.executive.core.business.RevenueAgentNetwork
import com.shoshiluna.executive.core.network.OpenAiClient
import com.shoshiluna.executive.core.network.ConnectorRegistry
import com.shoshiluna.executive.core.external.ExternalAgentGateway
import com.shoshiluna.executive.core.external.ExternalOperationJournal
import com.shoshiluna.executive.core.external.GenericHttpsAgentAdapter
import com.shoshiluna.executive.core.external.OpenAiResponsesAgentAdapter
import com.shoshiluna.executive.core.external.TrustedExternalAgentRegistry
import com.shoshiluna.executive.core.github.GitHubCredentialStore
import com.shoshiluna.executive.core.github.GitHubConnectorAdapter
import com.shoshiluna.executive.core.video.VideoGenerationCredentialStore
import com.shoshiluna.executive.core.video.VideoWorkspace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

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

    fun initialize(context: Context) {
        appContext = context.applicationContext
        EmpireMissionControl.initialize(context)
        ArtifactFactory.initialize(context)
        EvolutionEngine.initialize(context)
        HumanApprovalGateway.initialize(context)
        VideoWorkspace.initialize(context)
        VideoGenerationCredentialStore.initialize(context)
        BuiltInToolBootstrap.initialize()
        RevenueAgentNetwork.initialize(context)
        SovereignMemoryVault.initialize(context)
        OpenAiClient.initialize(context)
        ExternalOperationJournal.initialize(context)
        ExternalAgentGateway.registerAdapter(GenericHttpsAgentAdapter())
        ExternalAgentGateway.registerAdapter(OpenAiResponsesAgentAdapter())
        TrustedExternalAgentRegistry.bootstrapCodexOne()
        GitHubCredentialStore.initialize(context)
        ConnectorRegistry.register(GitHubConnectorAdapter())
    }

    fun setChatActive(active: Boolean) { _isChatActive.value = active }
    fun applicationContext(): Context? = appContext
    fun reportLatency(latency: Long) { _networkLatency.value = latency }
    fun updateCpuLoad(load: Int) { _cpuLoad.value = load }
    fun getUptimeString(): String {
        val seconds = (System.currentTimeMillis() - startTime) / 1000
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
    }
}
