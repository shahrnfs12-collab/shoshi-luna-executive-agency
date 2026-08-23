package com.shoshiluna.executive.core.business

import com.shoshiluna.executive.core.empire.EmpireMissionControl
import com.shoshiluna.executive.core.empire.MissionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine

data class BusinessKpis(
    val realReceivedRevenue: Double = 0.0,
    val verifiedCosts: Double = 0.0,
    val verifiedNetResult: Double = 0.0,
    val pipelineValue: Double = 0.0,
    val contractedValue: Double = 0.0,
    val invoicedValue: Double = 0.0,
    val activeOpportunities: Int = 0,
    val validatedOpportunities: Int = 0,
    val rejectedOpportunities: Int = 0,
    val activeMissions: Int = 0,
    val totalLeads: Int = 0,
    val qualifiedLeads: Int = 0,
    val proposals: Int = 0,
    val wonLeads: Int = 0,
    val conversionRate: Double = 0.0,
    val responseRate: Double = 0.0
)

/**
 * ShoshiLuna KPI Engine.
 * Derives business metrics from real records.
 */
object KpiManager {
    private val _opportunities = MutableStateFlow<List<Opportunity>>(emptyList())
    private val _leads = MutableStateFlow<List<Lead>>(emptyList())

    val kpis = combine(
        RevenueLedger.records,
        CostEngine.costs,
        _opportunities,
        _leads,
        EmpireMissionControl.state
    ) { revenue, costs, opps, leads, empireState ->
        val realReceived = revenue.filter { it.status == RevenueStatus.RECEIVED && it.environment == Environment.REAL }.sumOf { it.amount }
        val realCosts = costs.filter { it.verified && it.environment == Environment.REAL }.sumOf { it.amount }
        
        val pipeline = revenue.filter { it.status == RevenueStatus.PIPELINE }.sumOf { it.amount }
        val contracted = revenue.filter { it.status == RevenueStatus.COMMITTED }.sumOf { it.amount }
        val invoiced = revenue.filter { it.status == RevenueStatus.INVOICED }.sumOf { it.amount }

        val activeMissions = empireState.missions.count { it.status != MissionStatus.COMPLETED && it.status != MissionStatus.FAILED }
        val won = leads.count { it.status == LeadStatus.WON }
        val responded = leads.count { it.status == LeadStatus.RESPONDED }
        val contacted = leads.count { it.status == LeadStatus.CONTACTED }
        
        BusinessKpis(
            realReceivedRevenue = realReceived,
            verifiedCosts = realCosts,
            verifiedNetResult = realReceived - realCosts,
            pipelineValue = pipeline,
            contractedValue = contracted,
            invoicedValue = invoiced,
            activeOpportunities = opps.count { it.status != OpportunityStatus.REJECTED },
            validatedOpportunities = opps.count { it.status == OpportunityStatus.VALIDATED },
            rejectedOpportunities = opps.count { it.status == OpportunityStatus.REJECTED },
            activeMissions = activeMissions,
            totalLeads = leads.size,
            qualifiedLeads = leads.count { it.status == LeadStatus.QUALIFIED },
            proposals = leads.count { it.status == LeadStatus.PROPOSAL },
            wonLeads = won,
            conversionRate = if (leads.isNotEmpty()) won.toDouble() / leads.size else 0.0,
            responseRate = if (contacted > 0) responded.toDouble() / contacted else 0.0
        )
    }

    fun updateOpportunities(list: List<Opportunity>) { _opportunities.value = list }
    fun updateLeads(list: List<Lead>) { _leads.value = list }
}
