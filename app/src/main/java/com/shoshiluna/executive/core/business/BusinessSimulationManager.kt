package com.shoshiluna.executive.core.business

import com.shoshiluna.executive.core.agent.*
import com.shoshiluna.executive.core.empire.EmpireMissionControl
import com.shoshiluna.executive.core.empire.MissionStatus
import java.util.UUID

/**
 * Manages end-to-end business simulations.
 * Hard Boundary: all entities marked environment = SIMULATION.
 */
object BusinessSimulationManager {

    fun runFullCycleSimulation(objective: String) {
        // 1. Mission Discovery
        val mission = EmpireMissionControl.createMission(
            title = "SIMULATION: $objective",
            ownerId = ExecutiveAgent.SHOSHI.id,
            environment = Environment.SIMULATION
        )
        EmpireMissionControl.transitionStatus(mission.id, MissionStatus.DISCOVERED, "Business objective identified")

        // 2. Opportunity Identification
        val oppId = UUID.randomUUID().toString()
        val opportunity = Opportunity(
            id = oppId,
            title = "AI Executive Assistant for SMBs",
            problem = "High overhead for strategy work",
            targetCustomer = "Small Business Owners",
            proposedSolution = "Autonomous agent fleet",
            monetizationModel = "Monthly Tiered Subscription",
            environment = Environment.SIMULATION,
            status = OpportunityStatus.RESEARCHING,
            originatingAgentId = ExecutiveAgent.SHOSHI.id,
            scoring = mapOf("demand" to 85, "feasibility" to 90, "risk" to 20)
        )
        KpiManager.updateOpportunities(listOf(opportunity))
        EmpireMissionControl.transitionStatus(mission.id, MissionStatus.RESEARCHING, "Gathering evidence")

        // 3. Evidence & Specialist Research
        val researcher = SpecialistAgentFactory.createMarketResearcher(ExecutiveAgent.SHOSHI.id, mission.id)
        val evidenceId = UUID.randomUUID().toString()
        val marketEvidence = EvidenceItem(
            id = evidenceId,
            category = EvidenceCategory.EXTERNAL_EVIDENCE,
            content = "Market research shows 40% growth in AI adoption for SMBs",
            source = "Industry Report 2026"
        )
        // Record in Vault
        SovereignMemoryVault.record(
            SovereignMemory(
                id = evidenceId,
                agentId = researcher?.id ?: "RE-001",
                partition = MemoryPartition.EXECUTIVE_SHARED,
                content = marketEvidence.content,
                evidenceCategory = marketEvidence.category,
                missionId = mission.id
            )
        )

        // 4. Council & Validation
        val council = CouncilEngine.startSession(mission.id, "Approve Opportunity: ${opportunity.title}", setOf(ExecutiveAgent.SHOSHI.id, ExecutiveAgent.LUNA.id))
        check(CouncilEngine.castVote(council.id, ExecutiveAgent.SHOSHI.id, "APPROVE"))
        check(CouncilEngine.castVote(council.id, ExecutiveAgent.LUNA.id, "APPROVE"))
        EmpireMissionControl.transitionStatus(mission.id, MissionStatus.EVALUATING, "Research evidence evaluated")
        EmpireMissionControl.transitionStatus(mission.id, MissionStatus.AWAITING_APPROVAL, "Executive Council recommendation requires approval state")
        EmpireMissionControl.transitionStatus(mission.id, MissionStatus.APPROVED, "Executive Council consensus achieved")
        EmpireMissionControl.transitionStatus(mission.id, MissionStatus.EXECUTING, "Approved simulation execution started")

        // 5. Lead Generation & Pipeline
        val lead = Lead(
            name = "Simulated SMB Client",
            contactInfo = "ceo@smb.sim",
            opportunityId = oppId,
            status = LeadStatus.PROPOSAL,
            environment = Environment.SIMULATION,
            missionId = mission.id
        )
        KpiManager.updateLeads(listOf(lead))

        // 6. Revenue Tracking
        val verifiedRev = RevenueRecord(
            amount = 10000.0,
            status = RevenueStatus.RECEIVED,
            missionId = mission.id,
            environment = Environment.SIMULATION,
            verificationRef = "SIM-PAY-001"
        )
        RevenueLedger.addRecord(verifiedRev)

        // 7. Treasury Allocation (Simulation)
        val verifiedCost = CostRecord(
            amount = 2000.0,
            category = "Simulation Infrastructure",
            missionId = mission.id,
            environment = Environment.SIMULATION,
            verified = true
        )
        CostEngine.addCost(verifiedCost)
        
        TreasuryLedger.performAllocation(listOf(verifiedRev), listOf(verifiedCost), Environment.SIMULATION)

        // 8. Simulated Outcome & Learning
        EmpireMissionControl.transitionStatus(mission.id, MissionStatus.VALIDATING, "Simulation output validation")
        EmpireMissionControl.transitionStatus(mission.id, MissionStatus.REVIEW, "Simulation ready for final review")
        EmpireMissionControl.transitionStatus(mission.id, MissionStatus.COMPLETED, "Simulation cycle finished successfully")
    }
}
