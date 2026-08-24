package com.shoshiluna.executive.ui.workingcore

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shoshiluna.executive.R
import com.shoshiluna.executive.core.agent.ExecutiveAgent
import com.shoshiluna.executive.core.network.AiConnectionState
import com.shoshiluna.executive.core.network.AiProviderId
import com.shoshiluna.executive.core.network.AiProviderRegistry
import com.shoshiluna.executive.core.presentation.VoiceEngine
import com.shoshiluna.executive.core.security.ApiKeyManager
import com.shoshiluna.executive.core.voice.AndroidSpeechToTextEngine
import com.shoshiluna.executive.core.voice.AudioPermissionState
import com.shoshiluna.executive.core.voice.SttState
import com.shoshiluna.executive.core.voice.rememberAudioPermissionFlow
import java.util.Locale

private val ChatGold = Color(0xFFD8B45A)
private val ChatPanel = Color(0xFF111111)

@Composable
fun WorkingCoreChatScreen(vm: WorkingCoreViewModel = viewModel()) {
    val context = LocalContext.current
    val ui by vm.uiState.collectAsState()
    val stt = remember(context) { AndroidSpeechToTextEngine(context, Locale.forLanguageTag("he-IL")) }
    val sttState by stt.state.collectAsState()
    var input by remember { mutableStateOf("") }
    var apiKeyInput by remember { mutableStateOf("") }
    var providerSelection by remember { mutableStateOf(ApiKeyManager.activeProvider(context) ?: AiProviderId.OPENAI) }
    var keyStatus by remember { mutableStateOf<String?>(null) }
    var showApiSettings by remember { mutableStateOf(ApiKeyManager.configuredProviders(context).isEmpty()) }

    fun beginListening() {
        VoiceEngine.stopSpeech()
        VoiceEngine.onUserSpeechDetected()
        stt.startListening { transcript ->
            input = transcript
            vm.send(transcript, speakResponse = true)
            input = ""
        }
    }

    val (permissionState, requestPermission) = rememberAudioPermissionFlow(onGranted = { beginListening() })

    DisposableEffect(stt) {
        onDispose {
            stt.release()
            VoiceEngine.stopSpeech()
        }
    }

    LaunchedEffect(ui.speakMessageId, ui.messages) {
        val id = ui.speakMessageId ?: return@LaunchedEffect
        val msg = ui.messages.firstOrNull { it.id == id && !it.fromUser } ?: return@LaunchedEffect
        VoiceEngine.speak(msg.text, msg.agent.name)
        vm.markSpoken(id)
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF060606))) {
        Text(
            "SHOSHI • LUNA — AI Chat",
            color = ChatGold,
            fontWeight = FontWeight.Black,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(16.dp)
        )

        ConnectionBanner(ui.connection, ui.error)

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AgentSelector(
                agent = ExecutiveAgent.SHOSHI,
                selected = ui.selectedAgent == ExecutiveAgent.SHOSHI,
                image = R.drawable.shoshi_executive,
                enabled = !ui.isSending,
                modifier = Modifier.weight(1f),
                onClick = { vm.selectAgent(ExecutiveAgent.SHOSHI) }
            )
            AgentSelector(
                agent = ExecutiveAgent.LUNA,
                selected = ui.selectedAgent == ExecutiveAgent.LUNA,
                image = R.drawable.luna_executive,
                enabled = !ui.isSending,
                modifier = Modifier.weight(1f),
                onClick = { vm.selectAgent(ExecutiveAgent.LUNA) }
            )
        }

        if (showApiSettings) {
            ElevatedCard(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = ChatPanel)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("חיבור ספקי AI", color = ChatGold, fontWeight = FontWeight.Bold)
                    Text(
                        ApiKeyManager.configuredProviders(context).takeIf { it.isNotEmpty() }
                            ?.joinToString(prefix = "מפתחות שמורים ומוצפנים: ") { it.name }
                            ?: "לא הוגדר מפתח. בקשות AI אינן יכולות לפעול.",
                        color = Color.LightGray
                    )
                    Text(
                        "Production security gate: provider API secrets must move to a server-side relay before public release.",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text("בחרי ספק — הזיהוי האוטומטי יעדכן את הבחירה כשאפשר:", color = Color.LightGray)
                    AiProviderId.entries.filter { it != AiProviderId.CUSTOM }.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { provider ->
                                FilterChip(
                                    selected = providerSelection == provider,
                                    onClick = { providerSelection = provider; keyStatus = null },
                                    label = { Text(provider.name) }
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = { value ->
                            apiKeyInput = value
                            keyStatus = runCatching { AiProviderRegistry.detect(value) }.getOrNull()?.let { detected ->
                                detected.provider?.also { providerSelection = it }
                                if (detected.requiresUserSelection) "לא ניתן לזהות בוודאות — בחרי ספק" else "זוהה: ${detected.provider?.name}"
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("API key") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation()
                    )
                    keyStatus?.let { Text(it, color = ChatGold, style = MaterialTheme.typography.bodySmall) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            enabled = apiKeyInput.isNotBlank(),
                            onClick = {
                                ApiKeyManager.saveProviderKey(context, providerSelection, apiKeyInput)
                                apiKeyInput = ""
                                keyStatus = "נשמר מוצפן עבור ${providerSelection.name}; החיבור יאומת בבקשה הבאה"
                            }
                        ) { Text("שמור מוצפן") }
                        OutlinedButton(onClick = {
                            ApiKeyManager.clearProviderKey(context, providerSelection)
                            apiKeyInput = ""
                            keyStatus = "המפתח של ${providerSelection.name} נמחק"
                        }) { Text("מחק ספק נבחר") }
                    }
                }
            }
        } else {
            TextButton(
                onClick = { showApiSettings = true },
                modifier = Modifier.padding(horizontal = 12.dp)
            ) { Text("הגדרות חיבור AI") }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (ui.messages.isEmpty()) {
                item {
                    Text(
                        "בחרי SHOSHI או LUNA. בקשה מפורשת כמו ‘תבנו לי תוכנית שיווק’ תיפתח כמשימה, תחולק בין שתיהן ותופיע במרכז השליטה. פקודות ‘משימות’, ‘סטטוס’ ו‘עצור הכל’ מבוצעות מקומית.",
                        color = Color.Gray
                    )
                }
            }
            items(ui.messages, key = { it.id }) { msg -> WorkingCoreBubble(msg) }
            ui.error?.let { error ->
                item {
                    Text("שגיאה: $error", color = MaterialTheme.colorScheme.error)
                }
            }
        }

        val listening = sttState is SttState.Listening || sttState is SttState.Partial
        sttStatusText(sttState)?.let { status ->
            Text(status, color = Color.Gray, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        }

        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                label = { Text("כתבי הודעה ל-${ui.selectedAgent.name}") },
                enabled = !ui.isSending,
                maxLines = 4
            )
            IconButton(
                enabled = !ui.isSending,
                onClick = {
                    if (listening) {
                        stt.cancel()
                        VoiceEngine.stopSpeech()
                    } else if (permissionState == AudioPermissionState.READY) {
                        beginListening()
                    } else {
                        requestPermission()
                    }
                }
            ) {
                Icon(if (listening) Icons.Default.Stop else Icons.Default.Mic, contentDescription = "Voice")
            }
            IconButton(
                enabled = input.isNotBlank() && !ui.isSending,
                onClick = {
                    vm.send(input, speakResponse = false)
                    input = ""
                }
            ) { Icon(Icons.Default.Send, contentDescription = "Send") }
        }
    }
}

@Composable
private fun ConnectionBanner(state: AiConnectionState, error: String?) {
    val (label, color) = when (state) {
        AiConnectionState.IDLE -> "AI: NOT YET VERIFIED" to Color.Gray
        AiConnectionState.CONNECTING -> "AI: CONNECTING" to Color(0xFFFFC107)
        AiConnectionState.CONNECTED -> "AI: CONNECTED — REAL RESPONSE RECEIVED" to Color(0xFF4CAF50)
        AiConnectionState.API_KEY_MISSING -> "AI: API KEY MISSING" to Color(0xFFFF9800)
        AiConnectionState.OFFLINE -> "AI: OFFLINE" to Color.Red
        AiConnectionState.RATE_LIMITED -> "AI: RATE LIMITED" to Color(0xFFFF9800)
        AiConnectionState.ERROR -> "AI: ERROR" to Color.Red
    }
    Surface(color = color.copy(alpha = 0.16f), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(label, color = color, fontWeight = FontWeight.Bold)
            if (!error.isNullOrBlank()) Text(error, color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun AgentSelector(
    agent: ExecutiveAgent,
    selected: Boolean,
    image: Int,
    enabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    ElevatedCard(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        colors = CardDefaults.elevatedCardColors(containerColor = if (selected) Color(0xFF2A2214) else ChatPanel)
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(image),
                contentDescription = agent.name,
                modifier = Modifier.size(58.dp),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.width(8.dp))
            Column {
                Text(agent.name, color = ChatGold, fontWeight = FontWeight.Black)
                Text(if (selected) "SELECTED" else "Select", color = Color.LightGray, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun WorkingCoreBubble(message: WorkingCoreMessage) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.fromUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = if (message.fromUser) Color(0xFF263238) else Color(0xFF17130D),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.widthIn(max = 520.dp)
        ) {
            Column(Modifier.padding(12.dp)) {
                val label = when (message.source) {
                    WorkingCoreMessageSource.USER -> "YOU → ${message.agent.name}"
                    WorkingCoreMessageSource.AI -> "${message.agent.name} · AI"
                    WorkingCoreMessageSource.COMMAND -> "SYSTEM COMMAND · ${message.agent.name}"
                }
                Text(label, color = ChatGold, fontWeight = FontWeight.Bold)
                Text(message.text, color = Color.White)
            }
        }
    }
}

private fun sttStatusText(state: SttState): String? = when (state) {
    SttState.Idle -> null
    SttState.Listening -> "STT: מקשיבה..."
    is SttState.Partial -> "STT: ${state.text}"
    is SttState.Final -> "STT: ${state.text}"
    is SttState.Error -> "STT ERROR: ${state.message}"
    SttState.PermissionRequired -> "STT: נדרשת הרשאת מיקרופון"
    SttState.ServiceUnavailable -> "STT: שירות זיהוי דיבור אינו זמין במכשיר"
}
