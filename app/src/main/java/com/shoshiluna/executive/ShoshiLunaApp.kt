package com.shoshiluna.executive

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shoshiluna.executive.core.agent.*
import com.shoshiluna.executive.core.empire.EmpireMissionControl
import com.shoshiluna.executive.core.empire.MissionStatus
import com.shoshiluna.executive.core.presentation.PresenceStateEngine
import com.shoshiluna.executive.core.presentation.SpatialRenderer
import com.shoshiluna.executive.core.presentation.VoiceEngine
import com.shoshiluna.executive.core.health.RuntimeReadiness
import com.shoshiluna.executive.core.hardening.MissionTransitionPolicy
import com.shoshiluna.executive.core.security.HumanApprovalGateway
import com.shoshiluna.executive.ui.screens.BusinessCommandScreen
import com.shoshiluna.executive.ui.screens.PipelineScreen
import com.shoshiluna.executive.ui.screens.DreamCommandCenterScreen
import com.shoshiluna.executive.ui.screens.DreamCouncilScreen
import com.shoshiluna.executive.ui.screens.ExternalNetworkScreen
import com.shoshiluna.executive.ui.screens.AiFabricDashboardScreen
import com.shoshiluna.executive.ui.workingcore.WorkingCoreChatScreen
import com.shoshiluna.executive.ui.NotificationPermissionGate

private val Gold = Color(0xFFD8B45A)
private val DeepGold = Color(0xFF8B6A25)
private val Ink = Color(0xFF060606)
private val Panel = Color(0xFF111111)
private val Soft = Color(0xFFE7E0D0)

@Composable
fun ShoshiLunaApp(initialTab: Int = 0, highlightedMissionId: String? = null) {
    val context = LocalContext.current
    NotificationPermissionGate()
    LaunchedEffect(context) {
        VoiceEngine.initialize(context)
    }
    DisposableEffect(Unit) {
        onDispose { VoiceEngine.shutdown() }
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Gold,
            secondary = DeepGold,
            background = Ink,
            surface = Panel,
            onPrimary = Color.Black,
            onBackground = Soft,
            onSurface = Soft,
        )
    ) {
        var tab by rememberSaveable { mutableIntStateOf(initialTab) }
        LaunchedEffect(initialTab) { tab = initialTab.coerceIn(0, 5) }
        Scaffold(
            containerColor = Ink,
            bottomBar = {
                NavigationBar(containerColor = Color(0xFF0B0B0B)) {
                    listOf(
                        Triple(Icons.Default.SpaceDashboard, "מרכז", 0),
                        Triple(Icons.AutoMirrored.Filled.Chat, "צ׳אט", 1),
                        Triple(Icons.Default.TaskAlt, "משימות", 2),
                        Triple(Icons.Default.Groups, "מועצה", 3),
                        Triple(Icons.Default.BusinessCenter, "עסקים", 4),
                        Triple(Icons.Default.MoreHoriz, "עוד", 5)
                    ).forEach { (icon, label, index) ->
                        NavigationBarItem(selected = tab == index, onClick = { tab = index }, icon = { Icon(icon, null) }, label = { Text(label) })
                    }
                }
            }
        ) { pad ->
            Box(Modifier.padding(pad).fillMaxSize()) {
                when (tab) {
                    0 -> DreamCommandCenterScreen()
                    1 -> WorkingCoreChatScreen()
                    2 -> MissionsScreen(highlightedMissionId)
                    3 -> DreamCouncilScreen()
                    4 -> BusinessCommandScreen()
                    else -> MoreExecutiveScreen()
                }
            }
        }
    }
}

@Composable
private fun Header(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(18.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium, color = Gold, fontWeight = FontWeight.Black)
        Text(subtitle, color = Color(0xFFB8B0A0))
    }
}

@Composable
private fun CommandCenter() {
    val state by EmpireMissionControl.state.collectAsState()
    val context = LocalContext.current
    val readiness = remember { RuntimeReadiness.inspect(context) }
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF15110A), Ink))),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item { Header("SHOSHI • LUNA", "EXECUTIVE AGENCY • COMMAND CENTER") }
        item {
            Image(
                painter = painterResource(R.drawable.command_center),
                contentDescription = "SHOSHI and LUNA command center",
                modifier = Modifier.fillMaxWidth().height(220.dp).padding(horizontal = 16.dp).clip(RoundedCornerShape(24.dp)),
                contentScale = ContentScale.Crop
            )
        }
        item {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ExecutiveCard(ExecutiveAgent.SHOSHI, R.drawable.shoshi_executive, Modifier.weight(1f))
                ExecutiveCard(ExecutiveAgent.LUNA, R.drawable.luna_executive, Modifier.weight(1f))
            }
        }
        item {
            ElevatedCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp), colors = CardDefaults.elevatedCardColors(containerColor = Panel)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Agency Status", color = Gold, fontWeight = FontWeight.Bold)
                    Text("Trading / Wallet / Brokerage: NOT PRESENT")
                    Text("Mission Control: ${if (state.emergencyHalt) "HALTED" else "ACTIVE"}")
                    Text("AI Key: ${if (readiness.items.firstOrNull { it.name == "AI provider" }?.ready == true) "CONFIGURED" else "NOT CONFIGURED"}")
                    Text("Presentation: 2D PRODUCTION UI • 3D DEFERRED")
                    Text("Memory Isolation: SHOSHI / LUNA / SHARED")
                    Text("Voice: Native TTS code path • device verification required")
                    Text("Digital Human: PHOTOREAL MASTER VISUALS ACTIVE")
                    Text("Emergency Halt: ${if (state.emergencyHalt) "ACTIVE" else "CLEAR"}")
                    Button(onClick = { if (state.emergencyHalt) EmpireMissionControl.releaseHalt() else EmpireMissionControl.emergencyHalt() }) {
                        Text(if (state.emergencyHalt) "חזרה לפעילות" else "EMERGENCY HALT")
                    }
                }
            }
        }
    }
}

@Composable
private fun ExecutiveCard(executive: ExecutiveAgent, image: Int, modifier: Modifier) {
    ElevatedCard(modifier, colors = CardDefaults.elevatedCardColors(containerColor = Panel)) {
        Column {
            Box(Modifier.fillMaxWidth().height(260.dp).background(Color.Black), contentAlignment = Alignment.Center) {
                Image(
                    painter = painterResource(image),
                    contentDescription = executive.name,
                    modifier = Modifier.fillMaxSize().padding(4.dp),
                    contentScale = ContentScale.Fit
                )
            }
            Column(Modifier.padding(12.dp)) {
                Text(executive.name, color = Gold, fontWeight = FontWeight.Black)
                Text(if (executive == ExecutiveAgent.SHOSHI) "Chief Growth & Strategy Executive" else "Chief Partnerships & Operations Executive", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun AgentsScreen() {
    val subAgents by MultiAgentCoordinator.subAgents.collectAsState()
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Header("הסוכנות המבצעות", "SHOSHI ו־LUNA עם סוכנים מומחים כפופים") }
        items(subAgents.values.toList()) { agent ->
            ElevatedCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), colors = CardDefaults.elevatedCardColors(containerColor = Panel)) {
                Column(Modifier.padding(16.dp)) {
                    Text(agent.name, color = Gold, fontWeight = FontWeight.Bold)
                    Text("סוכנת אם: ${agent.parentId ?: "ללא"}")
                    Text("תפקיד: ${agent.role}")
                    Text("כלל הרשאה: הרשאות הסוכן הכפוף אינן רחבות משל סוכנת האם", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun MissionsScreen(highlightedMissionId: String? = null) {
    val state by EmpireMissionControl.state.collectAsState()
    val approvals by HumanApprovalGateway.requests.collectAsState()
    val listState = rememberLazyListState()
    var title by remember { mutableStateOf("") }
    var owner by remember { mutableStateOf(ExecutiveAgent.SHOSHI) }
    LaunchedEffect(highlightedMissionId, state.missions.size) {
        val index = state.missions.indexOfFirst { it.id == highlightedMissionId }
        if (index >= 0) listState.animateScrollToItem(index + 2)
    }
    LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Header("מרכז המשימות", "ניהול עבודה מבוסס ראיות") }
        item {
            ElevatedCard(Modifier.fillMaxWidth().padding(16.dp), colors = CardDefaults.elevatedCardColors(containerColor = Panel)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(title, { title = it }, label = { Text("מטרה / משימה") }, modifier = Modifier.fillMaxWidth())
                    Text("שושי ולונה יחלקו את העבודה ביניהן, ימשיכו ברקע ויבקשו אישור לפני סיום או פעולה רגישה.", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(owner == ExecutiveAgent.SHOSHI, { owner = ExecutiveAgent.SHOSHI }, label = { Text("SHOSHI") })
                        FilterChip(owner == ExecutiveAgent.LUNA, { owner = ExecutiveAgent.LUNA }, label = { Text("LUNA") })
                    }
                    Button(enabled = !state.emergencyHalt && title.isNotBlank(), onClick = { 
                        AutonomousMissionEngine.submitGoal(title, owner)
                        title = "" 
                    }) { Text("התחילי משימה אוטונומית") }
                }
            }
        }
        items(state.missions, key = { it.id }) { mission ->
            val pendingApproval = approvals.values.firstOrNull { it.missionId == mission.id && it.userDecision == null }
            ElevatedCard(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).then(
                    if (mission.id == highlightedMissionId) Modifier.border(2.dp, Gold, RoundedCornerShape(12.dp)) else Modifier
                ),
                colors = CardDefaults.elevatedCardColors(containerColor = Panel)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(mission.title, color = Gold, fontWeight = FontWeight.Bold)
                    Text("${mission.ownerId} • ${mission.status} • נדרשות ראיות")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(
                            enabled = !state.emergencyHalt && MissionTransitionPolicy.canTransition(mission.status, MissionStatus.RUNNING),
                            onClick = { EmpireMissionControl.transitionStatus(mission.id, MissionStatus.RUNNING, "Manual Start") }
                        ) { Text("הפעל") }
                        TextButton(
                            enabled = !state.emergencyHalt && mission.status == MissionStatus.RUNNING && mission.workPlan.isEmpty(),
                            onClick = { CollaborationEngine.startConsultation(mission.id, ExecutiveAgent.SHOSHI.id, ExecutiveAgent.LUNA.id, mission.title) }
                        ) { Text("שיתוף פעולה") }
                        TextButton(
                            enabled = !state.emergencyHalt && pendingApproval == null && mission.status != MissionStatus.REVIEW && MissionTransitionPolicy.canTransition(mission.status, MissionStatus.COMPLETED),
                            onClick = { EmpireMissionControl.transitionStatus(mission.id, MissionStatus.COMPLETED, "Manual Complete") }
                        ) { Text("הושלם") }
                    }
                    if (pendingApproval != null) {
                        Text("נדרש אישור שלך לפני סיום המשימה", color = Gold, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { HumanApprovalGateway.provideDecision(pendingApproval.id, true) }) { Text("אישור התוצר") }
                            OutlinedButton(onClick = { HumanApprovalGateway.provideDecision(pendingApproval.id, false) }) { Text("דחייה") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MoreExecutiveScreen() {
    var section by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Header("מערכות הסוכנות", "מומחים • צנרת • זיכרון • נוכחות")
        ScrollableTabRow(selectedTabIndex = section, containerColor = Color(0xFF0B0B0B), contentColor = Gold) {
            listOf("סוכנים", "צנרת", "רשת", "זיכרון", "נוכחות", "AI Fabric").forEachIndexed { index, label ->
                Tab(selected = section == index, onClick = { section = index }, text = { Text(label) })
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (section) {
                0 -> AgentsScreen()
                1 -> PipelineScreen()
                2 -> ExternalNetworkScreen()
                3 -> MemoryScreen()
                4 -> PresenceScreen()
                else -> AiFabricDashboardScreen()
            }
        }
    }
}

@Composable
private fun CollaborationScreen() {
    val conversations by CommunicationBus.conversations.collectAsState()
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Header("שיתוף פעולה", "תקשורת בזמן אמת בין SHOSHI ל־LUNA") }
        
        if (conversations.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("אין כרגע שיתופי פעולה פעילים.", color = Color.Gray)
                }
            }
        }
        
        conversations.values.forEach { conversation ->
            item {
                Text("שיחה: ${conversation.id.take(8)}", color = Gold, modifier = Modifier.padding(16.dp))
            }
            items(conversation.messages) { message ->
                MessageBubble(message)
            }
        }
    }
}

@Composable
private fun MessageBubble(message: InternalMessage) {
    val isShoshi = message.senderId == ExecutiveAgent.SHOSHI.id
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalAlignment = if (isShoshi) Alignment.Start else Alignment.End
    ) {
        Surface(
            color = if (isShoshi) Color(0xFF1A1A1A) else Color(0xFF2A2A2A),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, if (isShoshi) Gold.copy(alpha = 0.5f) else Color.Gray)
        ) {
            Column(Modifier.padding(12.dp)) {
                Text(if (isShoshi) "SHOSHI" else "LUNA", color = Gold, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                Text(message.content, color = Color.White)
                Text(
                    "${message.type} • ${message.status}",
                    color = Color.Gray,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun MemoryScreen() {
    val memories by SovereignMemoryVault.memories.collectAsState()
    var text by remember { mutableStateOf("") }
    var owner by remember { mutableStateOf<ExecutiveAgent?>(ExecutiveAgent.SHOSHI) }
    var partition by remember { mutableStateOf(MemoryPartition.SHOSHI_PRIVATE) }
    
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Header("זיכרון ריבוני", "זיכרון פרטי ל־SHOSHI / פרטי ל־LUNA / משותף") }
        item {
            ElevatedCard(Modifier.fillMaxWidth().padding(16.dp), colors = CardDefaults.elevatedCardColors(containerColor = Panel)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(text, { text = it }, label = { Text("זיכרון חדש") }, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(partition == MemoryPartition.SHOSHI_PRIVATE, { partition = MemoryPartition.SHOSHI_PRIVATE; owner = ExecutiveAgent.SHOSHI }, label = { Text("SHOSHI") })
                        FilterChip(partition == MemoryPartition.LUNA_PRIVATE, { partition = MemoryPartition.LUNA_PRIVATE; owner = ExecutiveAgent.LUNA }, label = { Text("LUNA") })
                        FilterChip(partition == MemoryPartition.EXECUTIVE_SHARED, { partition = MemoryPartition.EXECUTIVE_SHARED; owner = null }, label = { Text("משותף") })
                    }
                    Button(enabled = text.isNotBlank(), onClick = { 
                        SovereignMemoryVault.record(SovereignMemory(partition = partition, agentId = owner?.id ?: "SHARED", content = text.trim()))
                        text = "" 
                    }) { Text("שמור") }
                }
            }
        }
        items(memories, key = { it.id }) { memory ->
            ElevatedCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), colors = CardDefaults.elevatedCardColors(containerColor = Panel)) {
                Column(Modifier.padding(14.dp)) {
                    Text(memory.partition.name, color = Gold, fontWeight = FontWeight.Bold)
                    Text(memory.content)
                }
            }
        }
    }
}

@Composable
private fun PhotorealPresenceCard(name: String, image: Int, state: String, modifier: Modifier = Modifier) {
    ElevatedCard(modifier, colors = CardDefaults.elevatedCardColors(containerColor = Panel)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            Image(
                painter = painterResource(image),
                contentDescription = "$name photoreal executive",
                modifier = Modifier.fillMaxSize().padding(6.dp),
                contentScale = ContentScale.Fit
            )
            Column(
                Modifier.align(Alignment.BottomStart)
                    .padding(10.dp)
                    .background(Ink.copy(alpha = 0.78f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 7.dp)
            ) {
                Text(name, color = Gold, fontWeight = FontWeight.Black)
                Text(state, color = Color.White, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun PresenceScreen() {
    val shoshiState by PresenceStateEngine.shoshiPresence.collectAsState()
    val lunaState by PresenceStateEngine.lunaPresence.collectAsState()
    val voiceState by VoiceEngine.voiceState.collectAsState()
    
    Column(Modifier.fillMaxSize()) {
        Header("נוכחות דיגיטלית", "ניטור דמויות, תנועה וקול בזמן אמת")
        
        Box(Modifier.fillMaxWidth().height(420.dp).padding(horizontal = 16.dp)) {
            SpatialRenderer(Modifier.fillMaxSize())
            Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("SHOSHI • ${shoshiState.name}", color = Gold, modifier = Modifier.background(Ink.copy(alpha = 0.75f)).padding(8.dp))
                Text("LUNA • ${lunaState.name}", color = Gold, modifier = Modifier.background(Ink.copy(alpha = 0.75f)).padding(8.dp))
            }
        }

        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                ElevatedCard(Modifier.fillMaxWidth().padding(16.dp), colors = CardDefaults.elevatedCardColors(containerColor = Panel)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("מערכת הקול", color = Gold, fontWeight = FontWeight.Black)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("מצב:", color = Color.White)
                            Text(voiceState.name, color = if (voiceState.name == "ERROR") Color.Red else Color.Green)
                        }
                    }
                }
            }
            item {
                ElevatedCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp), colors = CardDefaults.elevatedCardColors(containerColor = Panel)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("בדיקת נוכחות חזותית", color = Gold, fontWeight = FontWeight.Black)
                        Text("נכסי המאסטר: אומתו — SHOSHI ו־LUNA")
                        val readiness = RuntimeReadiness.inspect(LocalContext.current)
                        readiness.items.filter { it.name.startsWith("3D") }.forEach { item ->
                            Text("${item.name}: ${if (item.ready) "READY" else "NOT READY"} — ${item.detail}")
                        }
                        Text("מנוע התצוגה: נכלל בחבילה; הזמינות נקבעת לפי בדיקת המכשיר")
                        Text("זהות קולית: מוגדרת; מצב ההרצה מוצג למעלה")
                    }
                }
            }
        }
    }
}
