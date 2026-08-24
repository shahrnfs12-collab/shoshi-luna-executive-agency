package com.shoshiluna.executive.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shoshiluna.executive.core.fabric.FabricAuditLedger
import com.shoshiluna.executive.core.fabric.ProviderHealthMonitor
import com.shoshiluna.executive.core.fabric.RoutingTelemetry

private val FabricGold = Color(0xFFD8B45A)

@Composable
fun AiFabricDashboardScreen() {
    val health by ProviderHealthMonitor.health.collectAsState()
    val selected by RoutingTelemetry.latest.collectAsState()
    val configuredChecks = health.values.size
    val workingChecks = health.values.count { it.status.name == "CONNECTED" || it.status.name == "TESTED" || it.status.name == "VERIFIED" }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("AI Intelligence Fabric", color = FabricGold, fontWeight = FontWeight.Bold)
            Text("המפתחות והקטלוג מוצפנים ומוסתרים ממסך העבודה.")
            Text("המנוע נבחר אוטומטית לכל משימה; CONNECTED לעולם אינו מוצג כ־VERIFIED.", color = Color.Gray)
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("המנוע המתאים למשימה", fontWeight = FontWeight.Bold)
                    Text(selected?.selectedDisplayName ?: "ייבחר כאשר תישלח משימה")
                    selected?.let {
                        Text("התאמה: ${"%.0f".format(it.score * 100)}% • גיבויים זמינים: ${it.fallbackCount}")
                        Text("יכולות שנדרשו: ${it.capabilities.joinToString()}", color = Color.Gray)
                    }
                }
            }
        }
        item {
            Text("בדיקת המערכת", color = FabricGold, fontWeight = FontWeight.Bold)
            Text(if (configuredChecks == 0) "טרם בוצעה בדיקה חיה" else "$workingChecks מתוך $configuredChecks חיבורים עברו בדיקה חיה")
        }
        item {
            Text("Audit ledger", color = FabricGold, fontWeight = FontWeight.Bold)
            Text("Hash chain: ${if (FabricAuditLedger.verifyChain()) "VALID" else "INVALID"} • in-process records ${FabricAuditLedger.recent().size}")
        }
    }
}
