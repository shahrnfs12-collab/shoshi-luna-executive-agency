package com.shoshiluna.executive.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shoshiluna.executive.core.agent.CouncilEngine
import com.shoshiluna.executive.core.agent.ExecutiveAgent
import com.shoshiluna.executive.core.empire.EmpireMissionControl

private val CouncilGold = Color(0xFFE2C06B)
private val CouncilPanel = Color(0xCC12151B)

@Composable
fun DreamCouncilScreen() {
    val sessions by CouncilEngine.sessions.collectAsState()
    val empire by EmpireMissionControl.state.collectAsState()
    var agenda by remember { mutableStateOf("") }
    var missionId by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF14100A), Color.Black))),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("מועצת שושי ולונה", color = CouncilGold, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            Text("מרחב מבוקר לדיון, מחלוקת, אתגר והסלמה", color = Color.Gray)
        }
        item {
            ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = CouncilPanel), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("כינוס המועצה", color = CouncilGold, fontWeight = FontWeight.Bold)
                    if (empire.missions.isEmpty()) {
                        Text("יש ליצור תחילה משימה; כל דיון מקושר למשימה מסוימת.", color = Color.Gray)
                    } else {
                        var expanded by remember { mutableStateOf(false) }
                        Box {
                            OutlinedButton(onClick = { expanded = true }) {
                                Text(empire.missions.find { it.id == missionId }?.title ?: "בחרי משימה")
                            }
                            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                empire.missions.take(20).forEach { mission ->
                                    DropdownMenuItem(text = { Text(mission.title) }, onClick = { missionId = mission.id; expanded = false })
                                }
                            }
                        }
                        OutlinedTextField(agenda, { agenda = it }, label = { Text("נושא לדיון") }, modifier = Modifier.fillMaxWidth())
                        Button(
                            enabled = missionId.isNotBlank() && agenda.isNotBlank() && !empire.emergencyHalt,
                            onClick = {
                                CouncilEngine.startSession(
                                    missionId = missionId,
                                    agenda = agenda,
                                    participants = setOf(ExecutiveAgent.SHOSHI.id, ExecutiveAgent.LUNA.id)
                                )
                                agenda = ""
                            }
                        ) { Text("כנסי מועצה") }
                    }
                }
            }
        }
        if (sessions.isEmpty()) {
            item { Text("אין עדיין דיוני מועצה.", color = Color.Gray) }
        }
        items(sessions.values.sortedByDescending { it.id }) { session ->
            ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = CouncilPanel), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(session.agenda, color = Color.White, fontWeight = FontWeight.Bold)
                    Text("דיון ${session.id.take(8)} • ${if (session.isActive) "פעיל" else session.outcome}", color = CouncilGold)
                    Text("SHOSHI: ${session.votes[ExecutiveAgent.SHOSHI.id] ?: "ממתינה לעמדה"}", color = Color.LightGray)
                    Text("LUNA: ${session.votes[ExecutiveAgent.LUNA.id] ?: "ממתינה לעמדה"}", color = Color.LightGray)
                    if (session.isActive) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { CouncilEngine.castVote(session.id, ExecutiveAgent.SHOSHI.id, "SUPPORT") }) { Text("שושי תומכת") }
                            OutlinedButton(onClick = { CouncilEngine.castVote(session.id, ExecutiveAgent.LUNA.id, "CHALLENGE") }) { Text("לונה מאתגרת") }
                        }
                    }
                    if (!session.isActive && session.outcome != null) {
                        Text("תוצאה: ${session.outcome}", color = if (session.outcome.name.contains("DISAGREEMENT")) Color(0xFFFFC866) else Color(0xFF70E1A1), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
