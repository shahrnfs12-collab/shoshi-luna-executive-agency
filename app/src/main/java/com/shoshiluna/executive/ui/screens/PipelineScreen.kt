package com.shoshiluna.executive.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shoshiluna.executive.core.agent.AgencyOrchestrator
import com.shoshiluna.executive.core.business.PipelineStage
import com.shoshiluna.executive.core.business.PipelineStatus

private val Gold = Color(0xFFD8B45A)
private val Panel = Color(0xFF111111)

@Composable
fun PipelineScreen() {
    val pipelineStates by AgencyOrchestrator.pipelineStates.collectAsState()
    
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Business Pipeline", style = MaterialTheme.typography.headlineMedium, color = Gold, fontWeight = FontWeight.Black)
        Text("12-Stage Super-Agency Workflow", color = Color.Gray, modifier = Modifier.padding(bottom = 16.dp))
        
        if (pipelineStates.isEmpty()) {
            Box(Modifier.fillMaxSize()) {
                Text("No active pipelines. Start a mission to see progress.", color = Color.Gray)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(pipelineStates.values.toList()) { state ->
                    PipelineCard(state.missionId, state.currentStage)
                }
            }
        }
    }
}

@Composable
private fun PipelineCard(missionId: String, currentStage: PipelineStage) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = Panel)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Mission: ${missionId.take(8)}", color = Gold, fontWeight = FontWeight.Bold)
            Text("Current Stage: $currentStage", color = Color.White)
            
            LinearProgressIndicator(
                progress = { (PipelineStage.entries.indexOf(currentStage) + 1).toFloat() / PipelineStage.entries.size },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                color = Gold,
                trackColor = Color.DarkGray
            )
        }
    }
}
