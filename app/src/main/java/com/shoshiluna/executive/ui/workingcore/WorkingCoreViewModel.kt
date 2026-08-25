package com.shoshiluna.executive.ui.workingcore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shoshiluna.executive.core.agent.CommandBridgeResult
import com.shoshiluna.executive.core.agent.CommandExecutionBridge
import com.shoshiluna.executive.core.agent.DefaultWorkingCoreAiGateway
import com.shoshiluna.executive.core.agent.ExecutiveAgent
import com.shoshiluna.executive.core.agent.WorkingCoreAiGateway
import com.shoshiluna.executive.core.agent.SovereignMemory
import com.shoshiluna.executive.core.agent.SovereignMemoryVault
import com.shoshiluna.executive.core.agent.MemoryPartition
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
import kotlinx.coroutines.launch
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
    val speakMessageId: String? = null
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

    fun selectAgent(agent: ExecutiveAgent) {
        if (_uiState.value.isSending) return
        _uiState.value = _uiState.value.copy(selectedAgent = agent, error = null)
    }

    fun send(prompt: String, speakResponse: Boolean = false) {
        val processed = UserIntentNormalizer.process(prompt)
        val clean = processed.original
        if (clean.isBlank() || _uiState.value.isSending) return
        val agent = _uiState.value.selectedAgent
        val userMessage = WorkingCoreMessage(agent = agent, fromUser = true, text = clean)

        // Explicit USER commands are executed deterministically before the AI path.
        // AI-generated text can never reach this bridge directly.
        when (val command = commandBridge.tryExecute(clean, agent)) {
            is CommandBridgeResult.Executed -> {
                val resultMessage = WorkingCoreMessage(agent = agent, fromUser = false, text = command.message, source = WorkingCoreMessageSource.COMMAND)
                _uiState.value = _uiState.value.copy(
                    isSending = false,
                    messages = _uiState.value.messages + userMessage + resultMessage,
                    error = null,
                    speakMessageId = if (speakResponse) resultMessage.id else null
                )
                return
            }
            is CommandBridgeResult.Rejected -> {
                val resultMessage = WorkingCoreMessage(agent = agent, fromUser = false, text = command.message, source = WorkingCoreMessageSource.COMMAND)
                _uiState.value = _uiState.value.copy(
                    isSending = false,
                    messages = _uiState.value.messages + userMessage + resultMessage,
                    error = null,
                    speakMessageId = if (speakResponse) resultMessage.id else null
                )
                return
            }
            CommandBridgeResult.NotHandled -> Unit
        }

        _uiState.value = _uiState.value.copy(
            connection = AiConnectionState.CONNECTING,
            isSending = true,
            messages = _uiState.value.messages + userMessage,
            error = null,
            speakMessageId = null
        )
        activeRequest?.cancel()
        activeRequest = viewModelScope.launch {
            val routedPrompt = if (isGitHubIntent(processed.normalizedForRouting)) {
                when (val githubContext = buildVerifiedGitHubContext(processed.modelPrompt)) {
                    is GitHubContext.Ready -> githubContext.prompt
                    is GitHubContext.NotConfigured -> {
                        val message = WorkingCoreMessage(
                            agent = agent,
                            fromUser = false,
                            text = "GitHub עדיין לא מחובר באפליקציה. פתחי ‘חיבור GitHub’, הגדירי token והריפו פעם אחת, ואז אוכל לקרוא את המאגר וה-CI ישירות.",
                            source = WorkingCoreMessageSource.COMMAND
                        )
                        _uiState.value = _uiState.value.copy(
                            connection = AiConnectionState.CONNECTED,
                            isSending = false,
                            messages = _uiState.value.messages + message,
                            error = null,
                            speakMessageId = if (speakResponse) message.id else null
                        )
                        return@launch
                    }
                    is GitHubContext.Failed -> {
                        _uiState.value = _uiState.value.copy(
                            connection = AiConnectionState.ERROR,
                            isSending = false,
                            error = githubContext.message,
                            speakMessageId = null
                        )
                        return@launch
                    }
                }
            } else processed.modelPrompt

            when (val result = gateway.send(agent, routedPrompt)) {
                is AiCallResult.Success -> {
                    val answer = WorkingCoreMessage(agent = agent, fromUser = false, text = result.text)
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
                            content = "EXECUTIVE RESPONSE: ${result.text}",
                            importance = 0.45f,
                            evidenceCategory = EvidenceCategory.AI_INFERENCE
                        )
                    )
                    _uiState.value = _uiState.value.copy(
                        connection = AiConnectionState.CONNECTED,
                        isSending = false,
                        messages = _uiState.value.messages + answer,
                        error = null,
                        speakMessageId = if (speakResponse) answer.id else null
                    )
                }
                is AiCallResult.Failure -> {
                    _uiState.value = _uiState.value.copy(
                        connection = result.state,
                        isSending = false,
                        error = result.safeMessage,
                        speakMessageId = null
                    )
                }
            }
        }
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

    fun markSpoken(messageId: String) {
        if (_uiState.value.speakMessageId == messageId) {
            _uiState.value = _uiState.value.copy(speakMessageId = null)
        }
    }

    fun clearError() { _uiState.value = _uiState.value.copy(error = null) }

    override fun onCleared() {
        activeRequest?.cancel()
        super.onCleared()
    }

    private sealed interface GitHubContext {
        data class Ready(val prompt: String) : GitHubContext
        data object NotConfigured : GitHubContext
        data class Failed(val message: String) : GitHubContext
    }
}
