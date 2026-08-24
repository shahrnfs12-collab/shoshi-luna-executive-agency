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
import com.shoshiluna.executive.core.attachments.ChatAttachment
import com.shoshiluna.executive.core.business.EvidenceCategory
import com.shoshiluna.executive.core.network.AiCallResult
import com.shoshiluna.executive.core.network.AiConnectionState
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
    val speakMessageId: String? = null,
    val routingLabel: String? = null
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

    fun send(prompt: String, attachments: List<ChatAttachment> = emptyList(), speakResponse: Boolean = false) {
        val clean = prompt.trim()
        if (clean.isBlank() || _uiState.value.isSending) return
        val agent = _uiState.value.selectedAgent
        val attachmentLabel = attachments.takeIf { it.isNotEmpty() }?.joinToString(
            prefix = "\n\nמצורפים שנבדקו:\n", separator = "\n\n"
        ) {
            buildString {
                append("- ${it.displayName} (${it.mimeType}, ${it.sizeBytes} bytes)")
                it.safeTextPreview?.let { preview -> append("\n--- תוכן בטוח לניתוח ---\n").append(preview) }
            }
        }.orEmpty()
        val visiblePrompt = clean + attachmentLabel
        val userMessage = WorkingCoreMessage(agent = agent, fromUser = true, text = visiblePrompt)

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
            // Attachments are deliberately represented as verified metadata here. Binary files are never
            // executed; provider-specific image/document extraction is handled by a separate sandboxed tool.
            val visualInputs = attachments.flatMap { it.visualInputs }
            when (val result = gateway.sendWithImages(agent, visiblePrompt, visualInputs)) {
                is AiCallResult.Success -> {
                    val answer = WorkingCoreMessage(agent = agent, fromUser = false, text = result.text)
                    val partition = if (agent == ExecutiveAgent.SHOSHI) MemoryPartition.SHOSHI_PRIVATE else MemoryPartition.LUNA_PRIVATE
                    SovereignMemoryVault.record(
                        SovereignMemory(
                            partition = partition,
                            agentId = agent.id,
                            content = "USER REQUEST: $visiblePrompt",
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
                        routingLabel = listOfNotNull(result.taskProfile?.name, result.provider?.name, result.model).joinToString(" • ").ifBlank { null },
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
}
