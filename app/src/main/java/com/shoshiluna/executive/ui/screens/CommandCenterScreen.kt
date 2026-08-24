package com.shoshiluna.executive.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shoshiluna.executive.core.agent.MultiAgentCoordinator
import com.shoshiluna.executive.core.empire.EmpireMissionControl
import com.shoshiluna.executive.core.engine.TitanEngine
import com.shoshiluna.executive.core.health.SystemHealthManager
import com.shoshiluna.executive.ui.theme.Gold

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommandCenterScreen() {
    val state by EmpireMissionControl.state.collectAsState()
    val shoshi by MultiAgentCoordinator.shoshi.collectAsState()
    val luna by MultiAgentCoordinator.luna.collectAsState()
    val healths by SystemHealthManager.engineHealths.collectAsState()
    val systemStatus by TitanEngine.systemStatus.collectAsState()
    
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("מרכז השליטה של שושי ולונה", color = Gold, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Black)
            )
        },
        containerColor = Color.Black
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                ExecutiveStatusCard("SHOSHI", shoshi.role.name, systemStatus)
            }
            item {
                ExecutiveStatusCard("LUNA", luna.role.name, systemStatus)
            }
            item {
                SystemHealthSection(healths)
            }
            item {
                Text("משימות פעילות", color = Gold, style = MaterialTheme.typography.titleMedium)
            }
            items(state.missions) { mission ->
                MissionCard(mission.title, mission.status.name)
            }
        }
    }
}

@Composable
fun ExecutiveStatusCard(name: String, role: String, status: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(name, color = Gold, style = MaterialTheme.typography.headlineSmall)
            Text(role, color = Color.White.copy(alpha = 0.7f))
            Text("מצב: $status", color = if (status == "HEALTHY") Color.Green else Color(0xFFFFC866), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun SystemHealthSection(healths: Map<String, com.shoshiluna.executive.core.health.EngineHealth>) {
    Column {
        Text("בריאות המערכת", color = Gold, style = MaterialTheme.typography.titleMedium)
        healths.forEach { (name, health) ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(name, color = Color.White)
                Text(health.status.name, color = if (health.status.name == "ONLINE" || health.status.name == "RUNNING") Color.Green else Color.Red)
            }
        }
    }
}

@Composable
fun MissionCard(title: String, status: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0D0D0D))
    ) {
        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, color = Color.White)
            Text(status, color = Gold)
        }
    }
}
