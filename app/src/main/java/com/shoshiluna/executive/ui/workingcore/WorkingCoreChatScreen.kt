package com.shoshiluna.executive.ui.workingcore

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
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
import com.shoshiluna.executive.core.attachments.AttachmentInspection
import com.shoshiluna.executive.core.attachments.ChatAttachment
import com.shoshiluna.executive.core.attachments.SafeAttachmentInspector
import com.shoshiluna.executive.core.network.AiConnectionState
import com.shoshiluna.executive.core.network.AiProviderId
import com.shoshiluna.executive.core.network.AiProviderRegistry
import com.shoshiluna.executive.core.network.AiCallResult
import com.shoshiluna.executive.core.network.ChatMessage
import com.shoshiluna.executive.core.network.OpenAiClient
import com.shoshiluna.executive.core.presentation.VoiceEngine
import com.shoshiluna.executive.core.security.ApiKeyManager
import com.shoshiluna.executive.core.voice.AndroidSpeechToTextEngine
import com.shoshiluna.executive.core.voice.AudioPermissionState
import com.shoshiluna.executive.core.voice.SttState
import com.shoshiluna.executive.core.voice.rememberAudioPermissionFlow
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val ChatGold = Color(0xFFD8B45A)
private val ChatPanel = Color(0xFF111111)

@Composable
fun WorkingCoreChatScreen(vm: WorkingCoreViewModel = viewModel()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ui by vm.uiState.collectAsState()
    val stt = remember(context) { AndroidSpeechToTextEngine(context, Locale.forLanguageTag("he-IL")) }
    val sttState by stt.state.collectAsState()
    var input by remember { mutableStateOf("") }
    var apiKeyInput by remember { mutableStateOf("") }
    var providerSelection by remember { mutableStateOf(ApiKeyManager.activeProvider(context) ?: AiProviderId.OPENAI) }
    var keyStatus by remember { mutableStateOf<String?>(null) }
    var customEndpoint by remember { mutableStateOf("") }
    var customModel by remember { mutableStateOf("") }
    var providerHealth by remember { mutableStateOf<Map<AiProviderId, String>>(emptyMap()) }
    var testingAllProviders by remember { mutableStateOf(false) }
    var showApiSettings by remember { mutableStateOf(ApiKeyManager.configuredProviders(context).isEmpty()) }
    var attachments by remember { mutableStateOf<List<ChatAttachment>>(emptyList()) }
    var attachmentStatus by remember { mutableStateOf<String?>(null) }
    val attachmentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        scope.launch {
            val availableSlots = (SafeAttachmentInspector.MAX_FILES - attachments.size).coerceAtLeast(0)
            val selected = uris.take(availableSlots)
            val results = withContext(Dispatchers.IO) {
                selected.map { uri -> SafeAttachmentInspector.inspect(context.contentResolver, uri) }
            }
            val accepted = results.filterIsInstance<AttachmentInspection.Accepted>().map { it.attachment }
            attachments = (attachments + accepted).distinctBy { it.uri }.take(SafeAttachmentInspector.MAX_FILES)
            val rejected = results.filterIsInstance<AttachmentInspection.Rejected>()
            attachmentStatus = when {
                rejected.isNotEmpty() -> rejected.joinToString(" • ") { it.reason }
                accepted.isNotEmpty() -> "${accepted.size} קבצים נבדקו ומוכנים לצירוף"
                uris.isNotEmpty() && availableSlots == 0 -> "אפשר לצרף עד ${SafeAttachmentInspector.MAX_FILES} קבצים"
                else -> null
            }
        }
    }

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
        ui.routingLabel?.let { route ->
            Surface(color = ChatGold.copy(alpha = 0.10f), modifier = Modifier.fillMaxWidth()) {
                Text(
                    "נתב המוחות • $route",
                    color = ChatGold,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
        }

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
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 430.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("חיבור ספקי AI", color = ChatGold, fontWeight = FontWeight.Bold)
                        TextButton(onClick = { showApiSettings = false }) { Text("סגירה") }
                    }
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
                    AiProviderId.entries.chunked(2).forEach { row ->
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
                    if (providerSelection == AiProviderId.CUSTOM) {
                        OutlinedTextField(customEndpoint, { customEndpoint = it }, Modifier.fillMaxWidth(), label = { Text("כתובת HTTPS של OpenAI-compatible API") }, singleLine = true)
                        OutlinedTextField(customModel, { customModel = it }, Modifier.fillMaxWidth(), label = { Text("מזהה מודל") }, singleLine = true)
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
                            enabled = apiKeyInput.isNotBlank() && (providerSelection != AiProviderId.CUSTOM || (customEndpoint.isNotBlank() && customModel.isNotBlank())),
                            onClick = {
                                if (providerSelection == AiProviderId.CUSTOM) {
                                    ApiKeyManager.saveCustomProvider(context, apiKeyInput, customEndpoint, customModel)
                                } else ApiKeyManager.saveProviderKey(context, providerSelection, apiKeyInput)
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
                    OutlinedButton(
                        enabled = ApiKeyManager.hasProviderKey(context, providerSelection),
                        onClick = {
                            ApiKeyManager.setActiveProvider(context, providerSelection)
                            keyStatus = "בודקת חיבור אמיתי ל-${providerSelection.name}…"
                            scope.launch {
                                keyStatus = when (val result = OpenAiClient.getCompletionResult(
                                    messages = listOf(ChatMessage("user", "Reply with exactly: OK"))
                                )) {
                                    is AiCallResult.Success -> "החיבור ל-${providerSelection.name} אומת בתשובת שרת אמיתית"
                                    is AiCallResult.Failure -> result.safeMessage
                                }
                            }
                        }
                    ) { Text("בדיקת חיבור אמיתית") }
                    Button(
                        enabled = ApiKeyManager.configuredProviders(context).isNotEmpty() && !testingAllProviders,
                        onClick = {
                            testingAllProviders = true
                            providerHealth = emptyMap()
                            scope.launch {
                                val results = linkedMapOf<AiProviderId, String>()
                                ApiKeyManager.configuredProviders(context).forEach { provider ->
                                    results[provider] = "בודקת…"
                                    providerHealth = results.toMap()
                                    results[provider] = when (val result = OpenAiClient.getCompletionResult(
                                        messages = listOf(ChatMessage("user", "Reply with exactly: OK")),
                                        providerOverride = provider
                                    )) {
                                        is AiCallResult.Success -> "מחובר • התקבלה תשובה אמיתית"
                                        is AiCallResult.Failure -> "${result.state} • ${result.safeMessage}"
                                    }
                                    providerHealth = results.toMap()
                                }
                                testingAllProviders = false
                            }
                        }
                    ) { Text(if (testingAllProviders) "בודקת את כל המוחות…" else "בדיקת כל המוחות") }
                    providerHealth.forEach { (provider, status) ->
                        val connected = status.startsWith("מחובר")
                        Text(
                            "${provider.name}: $status",
                            color = if (connected) Color(0xFF70E1A1) else ChatGold,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Text(
                        "אפשר לגלול בתוך חלון ההגדרות עד לכפתורי השמירה והבדיקה.",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodySmall
                    )
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

        if (attachments.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                attachments.forEach { attachment ->
                    InputChip(
                        selected = true,
                        onClick = {},
                        label = { Text("${attachment.displayName} • ${attachment.summary}", maxLines = 1) },
                        trailingIcon = {
                            IconButton(onClick = { attachments = attachments - attachment }) {
                                Icon(Icons.Default.Close, contentDescription = "הסרת קובץ")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        attachmentStatus?.let { status ->
            Text(status, color = ChatGold, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp))
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
                enabled = !ui.isSending && attachments.size < SafeAttachmentInspector.MAX_FILES,
                onClick = { attachmentPicker.launch(arrayOf("*/*")) }
            ) { Icon(Icons.Default.AttachFile, contentDescription = "צירוף תמונה או קובץ") }
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
                enabled = (input.isNotBlank() || attachments.isNotEmpty()) && !ui.isSending,
                onClick = {
                    vm.send(input.ifBlank { "נתחי את הקבצים המצורפים" }, attachments, speakResponse = false)
                    input = ""
                    attachments = emptyList()
                    attachmentStatus = null
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
