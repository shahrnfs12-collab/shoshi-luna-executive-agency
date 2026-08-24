package com.shoshiluna.executive.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.shoshiluna.executive.R
import com.shoshiluna.executive.core.agent.*
import com.shoshiluna.executive.core.audit.AuditTrail
import com.shoshiluna.executive.core.business.EvidenceRegistry
import com.shoshiluna.executive.core.business.ArtifactFactory
import com.shoshiluna.executive.core.business.ToolGateway
import com.shoshiluna.executive.core.empire.EmpireMissionControl
import com.shoshiluna.executive.core.empire.MissionStatus
import com.shoshiluna.executive.core.dream.ExecutiveBriefEngine
import com.shoshiluna.executive.core.external.TrustedExternalAgentRegistry
import com.shoshiluna.executive.core.health.RuntimeReadiness
import com.shoshiluna.executive.core.security.HumanApprovalGateway
import com.shoshiluna.executive.core.evolution.EvolutionEngine
import com.shoshiluna.executive.core.evolution.LearningStatus
import kotlin.math.roundToInt

private val DreamGold = Color(0xFFE2C06B)
private val DreamInk = Color(0xFF050608)
private val DreamGlass = Color(0xB3151820)
private val DreamGreen = Color(0xFF70E1A1)
private val DreamAmber = Color(0xFFFFC866)
private val DreamRed = Color(0xFFFF7A7A)

@Composable
fun DreamCommandCenterScreen() {
    val context = LocalContext.current
    val empire by EmpireMissionControl.state.collectAsState()
    val council by CouncilEngine.sessions.collectAsState()
    val approvals by HumanApprovalGateway.requests.collectAsState()
    val memories by SovereignMemoryVault.memories.collectAsState()
    val evidence by EvidenceRegistry.items.collectAsState()
    val externalAgents by TrustedExternalAgentRegistry.agents.collectAsState()
    val reputations by AgentReputationEngine.reputations.collectAsState()
    val learnings by EvolutionEngine.learnings.collectAsState()
    val readiness = remember { RuntimeReadiness.inspect(context) }
    val aiReady = readiness.items.firstOrNull { it.name == "AI provider" }?.ready == true
    val spatialReady = readiness.items.filter { it.name.startsWith("3D ") }.all { it.ready }
    val registeredTools = remember { ToolGateway.registeredTools() }

    val activeMissions = empire.missions.count { it.status !in terminalMissionStatuses }
    val waitingMissions = empire.missions.count { it.status in waitingMissionStatuses }
    val pendingApprovals = approvals.values.count { it.userDecision == null }
    val activeCouncil = council.values.count { it.isActive }
    val trustedExternal = externalAgents.values.count { it.trustStatus.name == "TRUSTED" }
    val verifiedEvidence = evidence.values.count { it.category.name == "VERIFIED_RESULT" }
    val brief = remember(empire, approvals, council, evidence) {
        ExecutiveBriefEngine.build(
            missions = empire.missions,
            approvals = approvals.values,
            councilSessions = council.values,
            evidence = evidence.values,
            emergencyHalt = empire.emergencyHalt
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF17120B), DreamInk, Color.Black))),
        contentPadding = PaddingValues(bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Box(Modifier.fillMaxWidth().height(330.dp)) {
                Image(
                    painter = painterResource(R.drawable.command_center),
                    contentDescription = "SHOSHI LUNA Executive Command Center",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(Color.Transparent, Color(0x33000000), DreamInk))
                    )
                )
                Column(
                    Modifier.align(Alignment.BottomStart).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("SHOSHI • LUNA", color = DreamGold, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black)
                    Text("עולם הסוכנות הניהולית", color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text(
                        when {
                            empire.emergencyHalt -> "מצב כללי • עצירת חירום"
                            aiReady -> "מצב כללי • פעיל"
                            else -> "הליבה מוכנה • AI טרם אומת"
                        },
                        color = if (empire.emergencyHalt) DreamRed else if (aiReady) DreamGreen else DreamAmber,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DreamAgentCard(
                    name = "SHOSHI",
                    role = "Growth • Strategy • Orchestration",
                    image = R.drawable.shoshi_executive,
                    score = reputations[ExecutiveAgent.SHOSHI.id]?.takeIf { it.totalTasks > 0 }?.let { AgentReputationEngine.getScore(it.agentId) },
                    modifier = Modifier.weight(1f)
                )
                DreamAgentCard(
                    name = "LUNA",
                    role = "Partnerships • Operations • Challenge",
                    image = R.drawable.luna_executive,
                    score = reputations[ExecutiveAgent.LUNA.id]?.takeIf { it.totalTasks > 0 }?.let { AgentReputationEngine.getScore(it.agentId) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Text("תמונת מצב ניהולית", color = DreamGold, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 18.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DreamMetric("משימות", activeMissions.toString(), if (activeMissions > 0) DreamGreen else Color.LightGray, Modifier.weight(1f))
                DreamMetric("ממתינות", waitingMissions.toString(), if (waitingMissions > 0) DreamAmber else Color.LightGray, Modifier.weight(1f))
                DreamMetric("אישורים", pendingApprovals.toString(), if (pendingApprovals > 0) DreamAmber else DreamGreen, Modifier.weight(1f))
                DreamMetric("מועצה", activeCouncil.toString(), if (activeCouncil > 0) DreamGreen else Color.LightGray, Modifier.weight(1f))
            }
        }

        item {
            DreamGlassCard("EXECUTIVE BRIEF • NOW") {
                Text(brief.headline, color = Color.White, fontWeight = FontWeight.Bold)
                if (brief.priorities.isNotEmpty()) {
                    Text("Priorities", color = DreamGold, style = MaterialTheme.typography.labelLarge)
                    brief.priorities.forEach { Text("• $it", color = Color.LightGray, style = MaterialTheme.typography.bodySmall) }
                }
                if (brief.risks.isNotEmpty()) {
                    Text("Risks / Blocks", color = DreamAmber, style = MaterialTheme.typography.labelLarge)
                    brief.risks.forEach { Text("• $it", color = Color.LightGray, style = MaterialTheme.typography.bodySmall) }
                }
                Text("Next best action", color = DreamGold, style = MaterialTheme.typography.labelLarge)
                Text(brief.nextBestAction, color = DreamGreen, fontWeight = FontWeight.SemiBold)
            }
        }

        item {
            DreamGlassCard("LIVE EXECUTIVE NETWORK") {
                DreamStatusLine("AI Provider", readiness.items.firstOrNull { it.name == "AI provider" }?.ready == true, readiness.items.firstOrNull { it.name == "AI provider" }?.detail ?: "Unknown")
                DreamStatusLine("Trusted External Agents", trustedExternal > 0, "$trustedExternal trusted / ${externalAgents.size} registered")
                DreamStatusLine("Verified Evidence", verifiedEvidence > 0, "$verifiedEvidence verified records")
                DreamStatusLine(
                    "Execution Tools",
                    registeredTools.any { ToolGateway.hasRuntimeAdapter(it.id) },
                    "${registeredTools.count { ToolGateway.hasRuntimeAdapter(it.id) }} connected / ${registeredTools.size} registered"
                )
                DreamStatusLine("Sovereign Memory", true, "${memories.size} protected memories")
                DreamStatusLine("2D Executive Presence", true, "Production UI")
                DreamStatusLine("Spatial 3D World", spatialReady, if (spatialReady) "SHOSHI and LUNA GLB assets packaged and runtime-gated" else "One or more GLB assets are unavailable")
            }
        }

        if (pendingApprovals > 0) {
            item {
                DreamGlassCard("APPROVAL FIREWALL • ACTION REQUIRED") {
                    approvals.values.filter { it.userDecision == null }.take(3).forEach { approval ->
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(approval.requestedAction, color = Color.White, fontWeight = FontWeight.Bold)
                            Text("${approval.riskLevel} • ${approval.reason}", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { HumanApprovalGateway.provideDecision(approval.id, true) }) { Text("אישור") }
                                OutlinedButton(onClick = { HumanApprovalGateway.provideDecision(approval.id, false) }) { Text("דחייה") }
                            }
                            HorizontalDivider(color = Color.White.copy(alpha = 0.10f))
                        }
                    }
                }
            }
        }

        item {
            DreamGlassCard("בקרת משימות • עכשיו") {
                if (empire.missions.isEmpty()) {
                    Text("אין משימות פעילות. פתחי צ׳אט ובקשי מ-SHOSHI או LUNA ליצור משימה.", color = Color.LightGray)
                } else {
                    empire.missions.take(5).forEach { mission ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Radar, null, tint = missionStatusColor(mission.status), modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(mission.title, color = Color.White, fontWeight = FontWeight.SemiBold)
                                Text("${mission.status} • ${if (mission.ownerId == ExecutiveAgent.SHOSHI.id) "SHOSHI" else if (mission.ownerId == ExecutiveAgent.LUNA.id) "LUNA" else mission.ownerId}", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                                if (mission.workPlan.isNotEmpty()) {
                                    Spacer(Modifier.height(5.dp))
                                    mission.workPlan.forEach { encoded ->
                                        val parts = encoded.split("|", limit = 4)
                                        val owner = parts.getOrNull(1)?.let {
                                            when (it) {
                                                ExecutiveAgent.SHOSHI.id -> "SHOSHI"
                                                ExecutiveAgent.LUNA.id -> "LUNA"
                                                else -> it
                                            }
                                        } ?: "SYSTEM"
                                        val approval = parts.getOrNull(2) == "true"
                                        val description = parts.getOrNull(3) ?: encoded
                                        val stepId = parts.firstOrNull().orEmpty()
                                        val progress = mission.workPlanProgress[stepId] ?: "PENDING"
                                        Text(
                                            "${parts.firstOrNull() ?: "•"}. $owner — $description · $progress${if (approval) " · דורש אישור" else ""}",
                                            color = if (approval) DreamAmber else Color.LightGray,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }

        item {
            DreamGlassCard("TRUST & EVIDENCE") {
                Text("Every external action, sensitive decision and verified result must leave an evidence/audit trail.", color = Color.LightGray)
                Spacer(Modifier.height(8.dp))
                val recent = remember(evidence.size, empire.missions.size, approvals.size) { AuditTrail.recent(5) }
                if (recent.isEmpty()) Text("No audit events yet.", color = Color.Gray)
                recent.forEach { audit ->
                    Text("• ${audit.action} → ${audit.result}", color = Color.White, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        if (ArtifactFactory.artifacts.isNotEmpty()) {
            item {
                DreamGlassCard("DELIVERABLES • REAL OUTPUTS") {
                    ArtifactFactory.artifacts.sortedByDescending { it.timestamp }.take(5).forEach { artifact ->
                        Text("${artifact.type} • ${artifact.creatorAgentId}", color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text(artifact.content.take(280), color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                        Text("SHA-256 ${artifact.hash.take(16)}…", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                        HorizontalDivider(color = Color.White.copy(alpha = 0.10f))
                    }
                }
            }
        }

        item {
            DreamGlassCard("התפתחות • למידה מאומתת") {
                if (learnings.isEmpty()) {
                    Text("אין עדיין לקחים מאומתים. למידה נוצרת רק מתוצאה עם ראיות.", color = Color.Gray)
                } else {
                    learnings.values.sortedByDescending { it.createdAt }.take(6).forEach { learning ->
                        Text(learning.lesson, color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text("${learning.status} • ראיות ${learning.evidenceIds.size}", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                        when (learning.status) {
                            LearningStatus.PROPOSED -> OutlinedButton(onClick = {
                                EvolutionEngine.requestActivation(learning.id, ExecutiveAgent.SHOSHI.id)
                            }) { Text("בקשי אישור להפעלת הלקח") }
                            LearningStatus.AWAITING_APPROVAL -> {
                                val approved = learning.approvalRequestId?.let(HumanApprovalGateway::isApproved) == true
                                if (approved) Button(onClick = { EvolutionEngine.finalizeActivation(learning.id) }) {
                                    Text("הפעילי לקח שאושר")
                                } else Text("ממתין להחלטת בעלים", color = DreamAmber)
                            }
                            LearningStatus.ACTIVE -> Text("פעיל ומשפיע על עבודה עתידית", color = DreamGreen)
                            LearningStatus.REJECTED -> Text("נדחה", color = DreamRed)
                        }
                        HorizontalDivider(color = Color.White.copy(alpha = 0.10f))
                    }
                }
            }
        }

        item {
            DreamGlassCard("SAFETY CORE") {
                Text(
                    if (empire.emergencyHalt) "All non-terminal missions are HALTED. External execution remains blocked." else "Permission Kernel • Human Approval • Financial Firewall • Evidence Gate • External Quarantine",
                    color = if (empire.emergencyHalt) DreamRed else DreamGreen,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = if (empire.emergencyHalt) DreamGreen else DreamRed),
                    onClick = {
                        if (empire.emergencyHalt) EmpireMissionControl.releaseHalt() else EmpireMissionControl.emergencyHalt()
                    }
                ) {
                    Icon(if (empire.emergencyHalt) Icons.Default.PlayArrow else Icons.Default.StopCircle, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (empire.emergencyHalt) "RELEASE HALT" else "EMERGENCY HALT")
                }
            }
        }
    }
}

@Composable
private fun DreamAgentCard(name: String, role: String, image: Int, score: Float?, modifier: Modifier = Modifier) {
    ElevatedCard(modifier, colors = CardDefaults.elevatedCardColors(containerColor = DreamGlass), shape = RoundedCornerShape(22.dp)) {
        Column {
            Image(painterResource(image), name, Modifier.fillMaxWidth().height(235.dp), contentScale = ContentScale.Crop)
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(name, color = DreamGold, fontWeight = FontWeight.Black)
                Text(role, color = Color.LightGray, style = MaterialTheme.typography.labelSmall)
                Text(
                    score?.let { "Reliability ${(it.coerceIn(0f, 1f) * 100).roundToInt()}%" } ?: "Reliability: טרם נמדד",
                    color = if (score == null) Color.Gray else DreamGreen,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
private fun DreamMetric(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Surface(modifier, color = DreamGlass, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(vertical = 12.dp, horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, color = color, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Text(label, color = Color.Gray, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun DreamGlassCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = DreamGlass),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, color = DreamGold, fontWeight = FontWeight.Black)
            content()
        }
    }
}

@Composable
private fun DreamStatusLine(label: String, ready: Boolean, detail: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(if (ready) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked, null, tint = if (ready) DreamGreen else DreamAmber, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(label, color = Color.White, fontWeight = FontWeight.SemiBold)
            Text(detail, color = Color.Gray, style = MaterialTheme.typography.labelSmall)
        }
    }
}

private fun missionStatusColor(status: MissionStatus): Color = when {
    status in terminalMissionStatuses -> if (status == MissionStatus.COMPLETED) DreamGreen else DreamRed
    status in waitingMissionStatuses -> DreamAmber
    else -> DreamGreen
}

private val waitingMissionStatuses = setOf(
    MissionStatus.WAITING, MissionStatus.AWAITING_APPROVAL, MissionStatus.WAITING_FOR_USER,
    MissionStatus.WAITING_FOR_APPROVAL, MissionStatus.WAITING_FOR_EXTERNAL_RESPONSE,
    MissionStatus.WAITING_FOR_DATA, MissionStatus.WAITING_FOR_DEPENDENCY, MissionStatus.PAUSED
)
private val terminalMissionStatuses = setOf(
    MissionStatus.COMPLETED, MissionStatus.FAILED, MissionStatus.REJECTED,
    MissionStatus.CANCELLED, MissionStatus.HALTED
)
