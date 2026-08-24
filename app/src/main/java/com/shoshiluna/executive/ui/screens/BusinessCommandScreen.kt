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
import com.shoshiluna.executive.core.fabric.AgencyGrowthAutopilot

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BusinessCommandScreen() {
    val kpis by KpiManager.kpis.collectAsState(BusinessKpis())
    val revenueRecords by RevenueLedger.records.collectAsState()
    val allocatedBalance by TreasuryLedger.totalAllocatedBalance.collectAsState(0.0)
    val fundedBalance by TreasuryLedger.totalFundedBalance.collectAsState(0.0)
    val allocations by TreasuryLedger.allocations.collectAsState()
    val spendProposals by TreasuryLedger.spendProposals.collectAsState()
    val growthNeeds by AgencyGrowthAutopilot.needs.collectAsState()
    val availableGrowth = remember(allocations, spendProposals) { TreasuryLedger.availableFundedBalance() }
    
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
                TreasurySummary(allocatedBalance, fundedBalance)
            }
            item {
                AiGrowthFundCard(availableGrowth, growthNeeds.size, spendProposals)
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
private fun AiGrowthFundCard(available: Double, waitingNeeds: Int, proposals: List<TreasurySpendProposal>) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF111111))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("קרן צמיחת AI • 5%", color = Color(0xFFD8B45A), fontWeight = FontWeight.Bold)
            KpiRow("זמין לרכישות", "$${"%.2f".format(available)}", if (available > 0) Color.Green else Color.Gray)
            KpiRow("צרכים שמחכים להכנסה", waitingNeeds.toString(), Color.Cyan)
            KpiRow("הצעות רכישה", proposals.size.toString(), Color.Yellow)
            Text("רכישה נפתחת רק מרווח אמיתי שהתקבל, אומת והועבר לקרן FUNDED.", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
            proposals.takeLast(5).reversed().forEach { proposal ->
                HorizontalDivider(color = Color.DarkGray)
                Text(proposal.description, color = Color.White, style = MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("$${"%.2f".format(proposal.amount)}", color = Color.White, fontWeight = FontWeight.Bold)
                    Text(proposal.status.name, color = when (proposal.status) {
                        SpendStatus.VERIFIED -> Color.Green
                        SpendStatus.REJECTED -> Color.Red
                        else -> Color.Yellow
                    })
                }
            }
            if (proposals.any { it.status == SpendStatus.PROPOSED }) {
                Text("ממתין לאישור שלך במרכז הבקרה. אישור רגיל אינו מחייב; תשלום אמיתי דורש גם אימות ביומטרי.", color = Color(0xFFD8B45A), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun TreasurySummary(allocated: Double, funded: Double) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF111111))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("AGENCY TREASURY", color = Color(0xFFD8B45A), fontWeight = FontWeight.Bold)
            KpiRow("ALLOCATED (5% Reserve)", "$${allocated}", Color.Yellow)
            KpiRow("FUNDED (Verified Real)", "$${funded}", Color.Green)
            Text(
                "Note: Allocated funds are accounting reserves. Funded funds are verified physical balances.",
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
