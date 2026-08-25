package com.shoshiluna.executive.ui.workingcore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shoshiluna.executive.core.agent.CommandBridgeResult
import com.shoshiluna.executive.core.agent.CommandExecutionBridge
import com.shoshiluna.executive.core.agent.DefaultWorkingCoreAiGateway
import com.shoshiluna.executive.core.agent.ExecutiveAgent
import com.shoshiluna.executive.core.agent.ExecutivePresence
import com.shoshiluna.executive.core.agent.ExecutivePresenceEngine
import com.shoshiluna.executive.core.agent.ExecutivePresenceState
import com.shoshiluna.executive.core.agent.MemoryPartition
import com.shoshiluna.executive.core.agent.SovereignMemory
import com.shoshiluna.executive.core.agent.SovereignMemoryVault
import com.shoshiluna.executive.core.agent.WorkingCoreAiGateway
import com.shoshiluna.executive.core.business.EvidenceCategory
import com.shoshiluna.executive.core.business.EvidenceRegistry
import com.shoshiluna.executive.core.github.GitHubCredentialStore
import com.shoshiluna.executive.core.language.UserIntentNormalizer
import com.shoshiluna.executive.core.network.AiCallResult
import com.shoshiluna.executive.core.network.AiConnectionState
import com.shoshiluna.executive.core.network.ConnectorRegistry
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

enum class WorkingCoreMessageSource { USER, AI, COMMAND }

data class WorkingCoreMessage(
    val id: String = UUID.randomUUID().toString(),
    val agent: ExecutiveAgent,
    val fromUser: Boolean,
    val text: String,
    val source: WorkingCoreMessageSource = if (fromUser) WorkingCoreMessageSource.USER else WorkingCoreMessageSource.AI
)

data class WorkingCoreUiState(
    val selectedAgent: ExecutiveAgent = ExecutiveAgent.SHOSHI,
    val connection: AiConnectionState = AiConnectionState.IDLE,
    val isSending: Boolean = false,
    val messages: List<WorkingCoreMessage> = emptyList(),
    val error: String? = null,
    val speakMessageId: String? = null,
    val sharedRoomActive: Boolean = false,
    val presence: Map<ExecutiveAgent, ExecutivePresence> = ExecutiveAgent.entries.associateWith { ExecutivePresence(it) }
)

class WorkingCoreViewModel(
    private val gateway: WorkingCoreAiGateway,
    private val commandBridge: CommandExecutionBridge
) : ViewModel() {
    constructor() : this(DefaultWorkingCoreAiGateway(), CommandExecutionBridge())
    constructor(gateway: WorkingCoreAiGateway) : this(gateway, CommandExecutionBridge())

    private val _uiState = MutableStateFlow(WorkingCoreUiState())
    val uiState: StateFlow<WorkingCoreUiState> = _uiState.asStateFlow()
    private var activeRequest: Job? = null

    init {
        viewModelScope.launch {
            ExecutivePresenceEngine.presence.collect { snapshot ->
                _uiState.update { it.copy(presence = snapshot) }
            }
        }
    }

    fun selectAgent(agent: ExecutiveAgent) {
        if (_uiState.value.isSending) return
        _uiState.value = _uiState.value.copy(selectedAgent = agent, error = null, sharedRoomActive = false)
    }

    fun send(prompt: String, speakResponse: Boolean = false) {
        val processed = UserIntentNormalizer.process(prompt)
        val clean = processed.original
        if (clean.isBlank() || _uiState.value.isSending) return

        if (isSharedRoomIntent(processed.normalizedForRouting)) {
            sendSharedRoom(clean, processed.modelPrompt, speakResponse)
            return
        }

        val agent = _uiState.value.selectedAgent
        val userMessage = WorkingCoreMessage(agent = agent, fromUser = true, text = clean)

        when (val command = commandBridge.tryExecute(clean, agent)) {
            is CommandBridgeResult.Executed -> {
                val resultMessage = WorkingCoreMessage(agent = agent, fromUser = false, text = command.message, source = WorkingCoreMessageSource.COMMAND)
                _uiState.value = _uiState.value.copy(
                    isSending = false,
                    messages = _uiState.value.messages + userMessage + resultMessage,
                    error = null,
                    speakMessageId = if (speakResponse) resultMessage.id else null,
                    sharedRoomActive = false
                )
                ExecutivePresenceEngine.idle(agent)
                return
            }
            is CommandBridgeResult.Rejected -> {
                val resultMessage = WorkingCoreMessage(agent = agent, fromUser = false, text = command.message, source = WorkingCoreMessageSource.COMMAND)
                _uiState.value = _uiState.value.copy(
                    isSending = false,
                    messages = _uiState.value.messages + userMessage + resultMessage,
                    error = null,
                    speakMessageId = if (speakResponse) resultMessage.id else null,
                    sharedRoomActive = false
                )
                ExecutivePresenceEngine.idle(agent)
                return
            }
            CommandBridgeResult.NotHandled -> Unit
        }

        _uiState.value = _uiState.value.copy(
            connection = AiConnectionState.CONNECTING,
            isSending = true,
            messages = _uiState.value.messages + userMessage,
            error = null,
            speakMessageId = null,
            sharedRoomActive = false
        )
        ExecutivePresenceEngine.set(agent, ExecutivePresenceState.THINKING, "Analyzing your request")
        val other = if (agent == ExecutiveAgent.SHOSHI) ExecutiveAgent.LUNA else ExecutiveAgent.SHOSHI
        ExecutivePresenceEngine.idle(other)

        activeRequest?.cancel()
        activeRequest = viewModelScope.launch {
            val routedPrompt = routePromptOrFinish(processed.normalizedForRouting, processed.modelPrompt, agent, speakResponse)
                ?: return@launch

            ExecutivePresenceEngine.set(agent, ExecutivePresenceState.WORKING, "Using tools and AI provider")
            val result = boundedGatewaySend(agent, routedPrompt)
            applySingleAgentResult(agent, clean, result, speakResponse)
        }
    }

    private fun sendSharedRoom(clean: String, modelPrompt: String, speakResponse: Boolean) {
        val userMessage = WorkingCoreMessage(
            agent = ExecutiveAgent.SHOSHI,
            fromUser = true,
            text = clean
        )
        _uiState.value = _uiState.value.copy(
            connection = AiConnectionState.CONNECTING,
            isSending = true,
            messages = _uiState.value.messages + userMessage,
            error = null,
            speakMessageId = null,
            sharedRoomActive = true
        )
        ExecutivePresenceEngine.setBoth(ExecutivePresenceState.COLLABORATING, "Shared executive room active")

        activeRequest?.cancel()
        activeRequest = viewModelScope.launch {
            val routedPrompt = routePromptOrFinish(modelPrompt.lowercase(), modelPrompt, ExecutiveAgent.SHOSHI, speakResponse)
                ?: return@launch

            ExecutivePresenceEngine.set(ExecutiveAgent.SHOSHI, ExecutivePresenceState.THINKING, "Preparing first position")
            ExecutivePresenceEngine.set(ExecutiveAgent.LUNA, ExecutivePresenceState.LISTENING, "Reviewing SHOSHI's position")
            val shoshiResult = boundedGatewaySend(
                ExecutiveAgent.SHOSHI,
                "Shared executive room. Give your strongest initial position on this request. Be concrete and evidence-aware:\n$routedPrompt"
            )
            if (shoshiResult is AiCallResult.Failure) {
                failSharedRoom(shoshiResult)
                return@launch
            }
            val shoshiText = (shoshiResult as AiCallResult.Success).text
            val shoshiMessage = WorkingCoreMessage(ExecutiveAgent.SHOSHI, false, shoshiText)
            _uiState.update { it.copy(messages = it.messages + shoshiMessage) }

            ExecutivePresenceEngine.set(ExecutiveAgent.SHOSHI, ExecutivePresenceState.LISTENING, "Listening to LUNA's challenge")
            ExecutivePresenceEngine.set(ExecutiveAgent.LUNA, ExecutivePresenceState.THINKING, "Challenging and improving the plan")
            val lunaResult = boundedGatewaySend(
                ExecutiveAgent.LUNA,
                "Shared executive room. User request:\n$routedPrompt\n\nSHOSHI's position:\n$shoshiText\n\nChallenge weak assumptions, add overlooked alternatives, and propose concrete improvements."
            )
            if (lunaResult is AiCallResult.Failure) {
                val partial = WorkingCoreMessage(
                    ExecutiveAgent.LUNA,
                    false,
                    "LUNA could not complete her review: ${lunaResult.safeMessage}",
                    WorkingCoreMessageSource.COMMAND
                )
                _uiState.update {
                    it.copy(
                        connection = lunaResult.state,
                        isSending = false,
                        messages = it.messages + partial,
                        error = lunaResult.safeMessage,
                        sharedRoomActive = false
                    )
                }
                ExecutivePresenceEngine.set(ExecutiveAgent.LUNA, ExecutivePresenceState.ERROR, lunaResult.safeMessage)
                ExecutivePresenceEngine.idle(ExecutiveAgent.SHOSHI)
                return@launch
            }
            val lunaText = (lunaResult as AiCallResult.Success).text
            val lunaMessage = WorkingCoreMessage(ExecutiveAgent.LUNA, false, lunaText)
            _uiState.update { it.copy(messages = it.messages + lunaMessage) }

            ExecutivePresenceEngine.setBoth(ExecutivePresenceState.COLLABORATING, "Synthesizing joint decision")
            val synthesisResult = boundedGatewaySend(
                ExecutiveAgent.SHOSHI,
                "Shared executive room final synthesis. User request:\n$routedPrompt\n\nSHOSHI initial position:\n$shoshiText\n\nLUNA review:\n$lunaText\n\nProduce one joint executive decision. Preserve disagreements that materially matter, choose the best path, and state the next action."
            )

            when (synthesisResult) {
                is AiCallResult.Success -> {
                    val finalMessage = WorkingCoreMessage(ExecutiveAgent.SHOSHI, false, synthesisResult.text)
                    recordMemory(ExecutiveAgent.SHOSHI, clean, synthesisResult.text)
                    recordMemory(ExecutiveAgent.LUNA, clean, lunaText)
                    _uiState.update {
                        it.copy(
                            connection = AiConnectionState.CONNECTED,
                            isSending = false,
                            messages = it.messages + finalMessage,
                            error = null,
                            speakMessageId = if (speakResponse) finalMessage.id else null,
                            sharedRoomActive = false
                        )
                    }
                    ExecutivePresenceEngine.set(ExecutiveAgent.SHOSHI, if (speakResponse) ExecutivePresenceState.SPEAKING else ExecutivePresenceState.IDLE, "Joint decision ready")
                    ExecutivePresenceEngine.idle(ExecutiveAgent.LUNA, "Joint decision ready")
                }
                is AiCallResult.Failure -> {
                    val fallback = WorkingCoreMessage(
                        ExecutiveAgent.SHOSHI,
                        false,
                        "הסיכום המשותף נכשל, אבל שני הסבבים נשמרו. ${synthesisResult.safeMessage}",
                        WorkingCoreMessageSource.COMMAND
                    )
                    _uiState.update {
                        it.copy(
                            connection = synthesisResult.state,
                            isSending = false,
                            messages = it.messages + fallback,
                            error = synthesisResult.safeMessage,
                            sharedRoomActive = false
                        )
                    }
                    ExecutivePresenceEngine.setBoth(ExecutivePresenceState.ERROR, synthesisResult.safeMessage)
                }
            }
        }
    }

    private suspend fun routePromptOrFinish(
        normalizedForRouting: String,
        modelPrompt: String,
        agent: ExecutiveAgent,
        speakResponse: Boolean
    ): String? {
        if (!isGitHubIntent(normalizedForRouting)) return modelPrompt
        return when (val githubContext = buildVerifiedGitHubContext(modelPrompt)) {
            is GitHubContext.Ready -> githubContext.prompt
            is GitHubContext.NotConfigured -> {
                val message = WorkingCoreMessage(
                    agent = agent,
                    fromUser = false,
                    text = "GitHub עדיין לא מחובר באפליקציה. פתחי ‘חיבור GitHub’, הגדירי token והריפו פעם אחת, ואז אוכל לקרוא את המאגר וה-CI ישירות.",
                    source = WorkingCoreMessageSource.COMMAND
                )
                _uiState.update {
                    it.copy(
                        connection = AiConnectionState.CONNECTED,
                        isSending = false,
                        messages = it.messages + message,
                        error = null,
                        speakMessageId = if (speakResponse) message.id else null,
                        sharedRoomActive = false
                    )
                }
                ExecutivePresenceEngine.idleBoth()
                null
            }
            is GitHubContext.Failed -> {
                _uiState.update {
                    it.copy(
                        connection = AiConnectionState.ERROR,
                        isSending = false,
                        error = githubContext.message,
                        speakMessageId = null,
                        sharedRoomActive = false
                    )
                }
                ExecutivePresenceEngine.setBoth(ExecutivePresenceState.ERROR, githubContext.message)
                null
            }
        }
    }

    private suspend fun boundedGatewaySend(agent: ExecutiveAgent, prompt: String): AiCallResult =
        withTimeoutOrNull(AI_REQUEST_TIMEOUT_MS) { gateway.send(agent, prompt) }
            ?: AiCallResult.Failure(
                AiConnectionState.OFFLINE,
                "AI request timed out after trying the configured provider route. Try again; the next request will re-evaluate available providers."
            )

    private fun applySingleAgentResult(
        agent: ExecutiveAgent,
        clean: String,
        result: AiCallResult,
        speakResponse: Boolean
    ) {
        when (result) {
            is AiCallResult.Success -> {
                val answer = WorkingCoreMessage(agent = agent, fromUser = false, text = result.text)
                recordMemory(agent, clean, result.text)
                _uiState.update {
                    it.copy(
                        connection = AiConnectionState.CONNECTED,
                        isSending = false,
                        messages = it.messages + answer,
                        error = null,
                        speakMessageId = if (speakResponse) answer.id else null,
                        sharedRoomActive = false
                    )
                }
                ExecutivePresenceEngine.set(
                    agent,
                    if (speakResponse) ExecutivePresenceState.SPEAKING else ExecutivePresenceState.IDLE,
                    if (speakResponse) "Speaking response" else "Ready"
                )
            }
            is AiCallResult.Failure -> {
                _uiState.update {
                    it.copy(
                        connection = result.state,
                        isSending = false,
                        error = result.safeMessage,
                        speakMessageId = null,
                        sharedRoomActive = false
                    )
                }
                ExecutivePresenceEngine.set(agent, ExecutivePresenceState.ERROR, result.safeMessage)
            }
        }
    }

    private fun failSharedRoom(failure: AiCallResult.Failure) {
        _uiState.update {
            it.copy(
                connection = failure.state,
                isSending = false,
                error = failure.safeMessage,
                sharedRoomActive = false
            )
        }
        ExecutivePresenceEngine.setBoth(ExecutivePresenceState.ERROR, failure.safeMessage)
    }

    private fun recordMemory(agent: ExecutiveAgent, clean: String, response: String) {
        val partition = if (agent == ExecutiveAgent.SHOSHI) MemoryPartition.SHOSHI_PRIVATE else MemoryPartition.LUNA_PRIVATE
        SovereignMemoryVault.record(
            SovereignMemory(
                partition = partition,
                agentId = agent.id,
                content = "USER REQUEST: $clean",
                importance = 0.55f,
                evidenceCategory = EvidenceCategory.USER_PROVIDED
            )
        )
        SovereignMemoryVault.record(
            SovereignMemory(
                partition = partition,
                agentId = agent.id,
                content = "EXECUTIVE RESPONSE: $response",
                importance = 0.45f,
                evidenceCategory = EvidenceCategory.AI_INFERENCE
            )
        )
    }

    private suspend fun buildVerifiedGitHubContext(userPrompt: String): GitHubContext {
        if (!GitHubCredentialStore.isConfigured()) return GitHubContext.NotConfigured
        val repository = GitHubCredentialStore.repository() ?: return GitHubContext.NotConfigured
        val correlationId = UUID.randomUUID().toString()
        val response = runCatching {
            ConnectorRegistry.callConnector("github", mapOf("operation" to "repo"))
        }.getOrElse { error ->
            return GitHubContext.Failed("GitHub connector failed: ${error.message ?: error.javaClass.simpleName}")
        }
        if (response.contains("\"ok\":false")) {
            return GitHubContext.Failed("GitHub connection was reached but repository access failed. Open GitHub connection settings and run the real connection test again.")
        }

        EvidenceRegistry.record(
            category = EvidenceCategory.VERIFIED_RESULT,
            content = "Live GitHub repository access verified for $repository",
            source = "GitHubConnector:repo",
            correlationId = correlationId
        )

        val compactResponse = response.take(5000)
        return GitHubContext.Ready(
            """
            VERIFIED TOOL CONTEXT — GITHUB LIVE ACCESS
            Repository: $repository
            Evidence correlation: $correlationId
            A real GitHub API repository request succeeded immediately before this AI call.
            You therefore HAVE live read access to this configured repository through the GitHub connector.
            Do not claim that you lack GitHub access, clone access, repository visibility, or tool connectivity.
            Use the verified repository context below when answering. For any write operation (branch, commit, PR, rerun), the GitHub connector requires the app's approval flow; never pretend a write happened unless the connector returns success and evidence exists.

            LIVE REPOSITORY RESPONSE:
            $compactResponse

            ORIGINAL USER REQUEST WITH TYPO-AWARE INTERPRETATION RULES:
            $userPrompt
            """.trimIndent()
        )
    }

    private fun isGitHubIntent(text: String): Boolean {
        val normalized = text.lowercase()
        return listOf("github", "גיטהאב", "repo", "repository", "ci/cd", "workflow", "pull request", "commit", "branch").any { it in normalized }
    }

    private fun isSharedRoomIntent(text: String): Boolean {
        val normalized = text.lowercase()
        return listOf(
            "שושי ולונה",
            "לונה ושושי",
            "שתיכן",
            "שתיהם",
            "שתיהן",
            "both of you",
            "shoshi and luna",
            "luna and shoshi",
            "executive room",
            "חדר משותף",
            "דיון ביניכן",
            "תדונו"
        ).any { it in normalized }
    }

    fun markSpoken(messageId: String) {
        if (_uiState.value.speakMessageId == messageId) {
            val speakingAgent = _uiState.value.messages.firstOrNull { it.id == messageId }?.agent
            _uiState.value = _uiState.value.copy(speakMessageId = null)
            speakingAgent?.let { ExecutivePresenceEngine.idle(it) }
        }
    }

    fun clearError() { _uiState.value = _uiState.value.copy(error = null) }

    override fun onCleared() {
        activeRequest?.cancel()
        ExecutivePresenceEngine.idleBoth()
        super.onCleared()
    }

    private sealed interface GitHubContext {
        data class Ready(val prompt: String) : GitHubContext
        data object NotConfigured : GitHubContext
        data class Failed(val message: String) : GitHubContext
    }

    private companion object {
        const val AI_REQUEST_TIMEOUT_MS = 75_000L
    }
}
