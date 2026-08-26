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
import com.shoshiluna.executive.core.business.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BusinessCommandScreen() {
    val kpis by KpiManager.kpis.collectAsState(BusinessKpis())
    val revenueRecords by RevenueLedger.records.collectAsState()
    val allocatedBalance by TreasuryLedger.totalAllocatedBalance.collectAsState(0.0)
    val fundedBalance by TreasuryLedger.totalFundedBalance.collectAsState(0.0)
    val allocations by TreasuryLedger.allocations.collectAsState()
    val revenueAgents by RevenueAgentNetwork.agents.collectAsState()
    val agentReports by RevenueAgentNetwork.reports.collectAsState()
    
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("BUSINESS ENGINE", color = Color(0xFFD8B45A), fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Black)
            )
        },
        containerColor = Color.Black
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                KpiDashboard(kpis)
            }
            item {
                TreasurySummary(allocatedBalance, fundedBalance, allocations.lastOrNull()?.distribution)
            }
            item {
                RevenueAgentNetworkSummary(revenueAgents.values.toList(), agentReports)
            }
            item {
                Text("REVENUE LEDGER", color = Color(0xFFD8B45A), style = MaterialTheme.typography.titleMedium)
            }
            items(revenueRecords) { record ->
                RevenueCard(record)
            }
            item {
                Text("BUSINESS PIPELINE", color = Color(0xFFD8B45A), style = MaterialTheme.typography.titleMedium)
            }
            item {
                PipelineSummary(kpis)
            }
            item {
                Button(
                    onClick = { BusinessSimulationManager.runFullCycleSimulation("Growth Strategy") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("RUN BUSINESS SIMULATION")
                }
            }
        }
    }
}

@Composable
fun RevenueAgentNetworkSummary(agents: List<RevenueLearningAgent>, reports: List<RevenueAgentReport>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF111111))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("REVENUE LEARNING NETWORK", color = Color(0xFFD8B45A), fontWeight = FontWeight.Bold)
            KpiRow("DAILY TARGET — NOT GUARANTEED", "$${RevenueAgentNetwork.DAILY_REVENUE_TARGET_USD.toInt()}", Color.Cyan)
            agents.sortedBy { it.role.ordinal }.forEach { agent ->
                KpiRow(agent.role.name, agent.status.name, when (agent.status) { RevenueAgentRunStatus.REPORTED -> Color.Green; RevenueAgentRunStatus.FAILED -> Color.Red; RevenueAgentRunStatus.RUNNING -> Color.Cyan; else -> Color.White })\n                if (agent.status == RevenueAgentRunStatus.FAILED && !agent.lastReport.isNullOrBlank()) {\n                    Text("Reason: ${agent.lastReport}", color = Color(0xFFFF9A9A), style = MaterialTheme.typography.labelSmall)\n                }
            }
            Text("Reports to SHOSHI: ${reports.size} • AI findings remain unverified until evidence review.", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
            Text("Publishing, outreach, purchases and transfers require owner approval.", color = Color.Yellow, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun TreasurySummary(allocated: Double, funded: Double, distribution: ProfitDistributionBreakdown?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF111111))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("AGENCY TREASURY", color = Color(0xFFD8B45A), fontWeight = FontWeight.Bold)
            KpiRow("DEVELOPMENT RESERVE (20%)", "$${allocated}", Color.Yellow)
            KpiRow("FUNDED (Verified Real)", "$${funded}", Color.Green)
            HorizontalDivider(color = Color.DarkGray)
            KpiRow("OWNER BANK (40%)", "$${distribution?.bankAmount ?: 0.0}", Color.White)
            KpiRow("DIGITAL WALLET (40%)", "$${distribution?.digitalWalletAmount ?: 0.0}", Color.White)
            KpiRow("SHOSHI/LUNA DEVELOPMENT (20%)", "$${distribution?.developmentAmount ?: 0.0}", Color.Yellow)
            Text(
                "Calculated from verified net profit. Every external transfer requires owner approval; routing details are not exposed to AI agents.",
                color = Color.Gray,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
fun KpiDashboard(kpis: BusinessKpis) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF111111))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("AGENCY KPIs", color = Color(0xFFD8B45A), fontWeight = FontWeight.Bold)
            KpiRow("REAL REVENUE (VERIFIED)", "$${kpis.realReceivedRevenue}", Color.Green)
            KpiRow("VERIFIED COSTS", "$${kpis.verifiedCosts}", Color.Red)
            KpiRow("VERIFIED NET RESULT", "$${kpis.verifiedNetResult}", if (kpis.verifiedNetResult >= 0) Color.Green else Color.Red)
            HorizontalDivider(color = Color.DarkGray)
            KpiRow("ACTIVE MISSIONS", kpis.activeMissions.toString(), Color.White)
            KpiRow("VALIDATED OPPS", kpis.validatedOpportunities.toString(), Color.Cyan)
            KpiRow("CONV RATE", "${(kpis.conversionRate * 100).toInt()}%", Color.White)
        }
    }
}

@Composable
fun PipelineSummary(kpis: BusinessKpis) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF111111))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            KpiRow("PIPELINE VALUE", "$${kpis.pipelineValue}", Color.Cyan)
            KpiRow("CONTRACTED", "$${kpis.contractedValue}", Color.Yellow)
            KpiRow("INVOICED", "$${kpis.invoicedValue}", Color.White)
            KpiRow("TOTAL LEADS", kpis.totalLeads.toString(), Color.White)
            KpiRow("QUALIFIED LEADS", kpis.qualifiedLeads.toString(), Color.Cyan)
            KpiRow("PROPOSALS", kpis.proposals.toString(), Color.White)
            KpiRow("RESPONSE RATE", "${(kpis.responseRate * 100).toInt()}%", Color.White)
        }
    }
}

@Composable
fun KpiRow(label: String, value: String, color: Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color.Gray)
        Text(value, color = color, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun RevenueCard(record: RevenueRecord) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A))
    ) {
        Row(Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(record.status.name, color = if (record.status == RevenueStatus.RECEIVED) Color.Green else Color.Gray)
                Text(if (record.environment == Environment.SIMULATION) "SIMULATION" else "REAL", color = Color.Yellow, style = MaterialTheme.typography.labelSmall)
            }
            Text("$${record.amount}", color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}
