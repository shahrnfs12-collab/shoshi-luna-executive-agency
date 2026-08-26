package com.shoshiluna.executive.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shoshiluna.executive.core.agent.CapabilityBuildPlanner
import com.shoshiluna.executive.core.security.HumanApprovalGateway

@Composable
fun CapabilityApprovalOverlay() {
    val requests by HumanApprovalGateway.requests.collectAsState()
    val pending = requests.values
        .filter { it.userDecision == null && it.requestedAction.startsWith("Build missing capability:") }
        .sortedBy { it.timestamp }
        .firstOrNull() ?: return
    val proposal = CapabilityBuildPlanner.findByApprovalRequestId(pending.id)

    AlertDialog(
        onDismissRequest = {},
        title = { Text("שושי/לונה מבקשות לבנות יכולת") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("סוכן: ${pending.requestingAgentId}", style = MaterialTheme.typography.labelLarge)
                Text("סיכון: ${pending.riskLevel}", style = MaterialTheme.typography.labelLarge)
                if (proposal != null) {
                    Text("יכולת: ${proposal.capabilityName}", style = MaterialTheme.typography.titleSmall)
                    Text("מה חסר: ${proposal.gap}")
                    Text("למה צריך אותה: ${proposal.whyNeeded}")
                    Text("מה ישתנה:", style = MaterialTheme.typography.titleSmall)
                    proposal.plannedChanges.forEach { Text("• $it") }
                    if (proposal.expectedToolsOrDependencies.isNotEmpty()) {
                        Text("כלים/תלויות:", style = MaterialTheme.typography.titleSmall)
                        proposal.expectedToolsOrDependencies.forEach { Text("• $it") }
                    }
                    Text("איך יוודאו שזה עובד:", style = MaterialTheme.typography.titleSmall)
                    proposal.verificationPlan.forEach { Text("• $it") }
                } else {
                    Text(pending.reason)
                }
                Text(
                    "לא תתחיל בנייה לפני אישור מפורש. גם לאחר האישור היכולת לא תסומן כמוכנה לפני אימות.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            Button(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                onClick = { CapabilityBuildPlanner.applyOwnerDecision(pending.id, true) }
            ) { Text("אשר בניית היכולת") }
        },
        dismissButton = {
            OutlinedButton(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                onClick = { CapabilityBuildPlanner.applyOwnerDecision(pending.id, false) }
            ) { Text("דחה") }
        }
    )
}
