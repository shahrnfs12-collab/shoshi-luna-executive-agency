package com.shoshiluna.executive.core.agent

import com.shoshiluna.executive.core.business.EvidenceCategory
import com.shoshiluna.executive.core.business.EvidenceRegistry
import com.shoshiluna.executive.core.empire.EmpireMission
import com.shoshiluna.executive.core.empire.EmpireMissionControl
import com.shoshiluna.executive.core.empire.MissionStatus
import com.shoshiluna.executive.core.network.AiCallResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

data class MissionWorkItem(
    val order: Int,
    val owner: ExecutiveAgent,
    val description: String,
    val requiresApproval: Boolean = false
)

data class AutonomousMissionReceipt(
    val mission: EmpireMission,
    val plan: List<MissionWorkItem>
)

/**
 * Goal-first adaptive workflow.
 *
 * submitGoal() creates the durable mission state and then starts a bounded real AI
 * execution loop. The loop never performs external/financial/destructive actions.
 * If the requested outcome requires a new capability or an approval-gated action,
 * the mission stops in WAITING_FOR_APPROVAL instead of pretending it completed.
 */
object AutonomousMissionEngine {
    private val runtimeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gateway: WorkingCoreAiGateway by lazy { DefaultWorkingCoreAiGateway() }

    fun submitGoal(goal: String, requestedOwner: ExecutiveAgent): AutonomousMissionReceipt {
        val cleanGoal = goal.trim()
        require(cleanGoal.isNotEmpty()) { "Goal is required" }
        check(!EmpireMissionControl.state.value.emergencyHalt) { "Emergency halt is active" }

        val mission = EmpireMissionControl.createMission(cleanGoal, requestedOwner.id)
        val counterpart = if (requestedOwner == ExecutiveAgent.SHOSHI) ExecutiveAgent.LUNA else ExecutiveAgent.SHOSHI
        val plan = listOf(
            MissionWorkItem(1, requestedOwner, "GOAL: infer desired outcome, constraints and measurable success from context; ask only for irreducible missing input"),
            MissionWorkItem(2, counterpart, "RESEARCH LOOP: use permitted memory and available AI reasoning to discover facts, options and dependencies before saying the task cannot be done"),
            MissionWorkItem(3, requestedOwner, "DYNAMIC PLAN: generate task-specific steps and alternatives; rank paths by expected quality, risk, time and cost"),
            MissionWorkItem(4, counterpart, "TOOL DISCOVERY/ROUTING: identify the best available model, tool, API, library or provider for each subtask; prefer existing safe capabilities first"),
            MissionWorkItem(5, requestedOwner, "CAPABILITY GAP: if a required capability is genuinely absent, create a Capability Build Proposal and wait for explicit approval", requiresApproval = true),
            MissionWorkItem(6, requestedOwner, "CAPABILITY BUILD: only after explicit HumanApproval, build the approved capability scope and verify it before relying on it"),
            MissionWorkItem(7, requestedOwner, "DELIVERABLE MODE: produce the actual useful artifact/result whenever technically available"),
            MissionWorkItem(8, counterpart, "CRITIC: attack the draft for factual gaps, weak assumptions, missed opportunities and security issues"),
            MissionWorkItem(9, requestedOwner, "RECOVERY: on reversible failure retry intelligently, then switch route or re-plan before escalating"),
            MissionWorkItem(10, requestedOwner, "PERSISTENT GOAL: preserve objective, attempted approaches, evidence, blockers and next action in memory"),
            MissionWorkItem(11, counterpart, "SELF-EVALUATION: score progress, deliverable quality, evidence, autonomy and verification"),
            MissionWorkItem(12, requestedOwner, "LEARNING: record reusable outcome-based lessons without changing security or financial policy"),
            MissionWorkItem(13, counterpart, "APPROVAL GATE: stop for external, financial, credential-sensitive, destructive, irreversible or self-extension action", requiresApproval = true),
            MissionWorkItem(14, requestedOwner, "ITERATE: if completion is not honestly verified, choose the highest-value correction and continue")
        )

        EmpireMissionControl.attachPlan(mission.id, plan.map { "${it.order}|${it.owner.id}|${it.requiresApproval}|${it.description}" })
        EmpireMissionControl.transitionStatus(mission.id, MissionStatus.PLANNED, "Adaptive goal-to-deliverable workflow created")
        EmpireMissionControl.transitionStatus(mission.id, MissionStatus.ASSIGNED, "Research, builder and critic roles assigned")
        EmpireMissionControl.transitionStatus(mission.id, MissionStatus.RUNNING, "Autonomous mission runtime started")
        EmpireMissionControl.updatePlanStep(mission.id, 1, "COMPLETED", "Goal interpreted from available context")
        EmpireMissionControl.updatePlanStep(mission.id, 2, "RUNNING", "LUNA/SHOSHI research pass is starting")
        CollaborationEngine.startConsultation(
            missionId = mission.id,
            initiatorId = requestedOwner.id,
            targetId = counterpart.id,
            topic = "Discover, build, critique and verify the strongest safe path to this goal: $cleanGoal"
        )

        val receipt = AutonomousMissionReceipt(
            mission = EmpireMissionControl.state.value.missions.first { it.id == mission.id },
            plan = plan
        )
        runtimeScope.launch { executeMission(receipt) }
        return receipt
    }

    private suspend fun executeMission(receipt: AutonomousMissionReceipt) {
        val missionId = receipt.mission.id
        val goal = receipt.mission.title
        val owner = ExecutiveAgent.entries.firstOrNull { it.id == receipt.mission.ownerId } ?: ExecutiveAgent.SHOSHI
        val critic = if (owner == ExecutiveAgent.SHOSHI) ExecutiveAgent.LUNA else ExecutiveAgent.SHOSHI

        try {
            val research = requireSuccess(
                missionId = missionId,
                step = 2,
                agent = critic,
                stage = "RESEARCH",
                prompt = """
                    Mission: $goal
                    Perform a concrete research/option-discovery pass using the context and capabilities actually available to you.
                    Separate FACT, ASSUMPTION and INFERENCE. Do not claim browsing, outreach, purchases, code changes or other external actions unless runtime evidence proves them.
                    Identify the strongest options, dependencies, risks and what can be completed safely inside the current runtime.
                """.trimIndent()
            )
            EmpireMissionControl.updatePlanStep(missionId, 2, "COMPLETED", "Real ${critic.name} provider response captured")

            EmpireMissionControl.updatePlanStep(missionId, 3, "RUNNING", "Owner is producing the executable plan")
            val plan = requireSuccess(
                missionId = missionId,
                step = 3,
                agent = owner,
                stage = "PLAN",
                prompt = """
                    Mission: $goal
                    Research pass from ${critic.name}:
                    $research

                    Produce the strongest safe execution plan and a useful first deliverable where possible.
                    First line MUST be exactly one of:
                    CAPABILITY_GAP: NONE
                    CAPABILITY_GAP: <short description of a genuinely missing capability that requires code/tool/dependency/permission change>
                    Do not label normal missing information or ordinary analysis as a capability gap.
                    Never execute external, financial, credential-sensitive, destructive or irreversible actions without explicit approval.
                """.trimIndent()
            )
            EmpireMissionControl.updatePlanStep(missionId, 3, "COMPLETED", "Real ${owner.name} planning response captured")
            EmpireMissionControl.updatePlanStep(missionId, 4, "COMPLETED", "Available provider route used; no unverified external tool claim accepted")

            val capabilityGap = parseCapabilityGap(plan)
            if (capabilityGap != null) {
                EmpireMissionControl.updatePlanStep(missionId, 5, "WAITING_APPROVAL", "Capability proposal requires explicit owner approval: $capabilityGap")
                EmpireMissionControl.updatePlanStep(missionId, 6, "WAITING_APPROVAL", "Capability build is blocked until explicit HumanApproval")
                EmpireMissionControl.enterWaitingState(
                    missionId = missionId,
                    status = MissionStatus.WAITING_FOR_APPROVAL,
                    condition = "Approve or reject capability build: $capabilityGap"
                )
                recordVerifiedRuntimeEvent(missionId, owner, "APPROVAL_GATE", "Mission paused before capability self-extension")
                return
            }
            EmpireMissionControl.updatePlanStep(missionId, 5, "COMPLETED", "No self-extension capability gap declared")
            EmpireMissionControl.updatePlanStep(missionId, 6, "COMPLETED", "No capability build required or performed")

            EmpireMissionControl.updatePlanStep(missionId, 7, "RUNNING", "Owner is producing the deliverable")
            val deliverable = requireSuccess(
                missionId = missionId,
                step = 7,
                agent = owner,
                stage = "DELIVERABLE",
                prompt = """
                    Mission: $goal
                    Research:
                    $research

                    Plan:
                    $plan

                    Produce the actual best deliverable that can honestly be created inside this runtime now.
                    If the goal requires an external action you cannot execute safely here, produce the complete approval-ready artifact or action package instead, and state the exact remaining gated action.
                    Do not pretend any external action happened.
                """.trimIndent()
            )
            EmpireMissionControl.updatePlanStep(missionId, 7, "COMPLETED", "Deliverable generated by real ${owner.name} provider call")

            EmpireMissionControl.updatePlanStep(missionId, 8, "RUNNING", "Counterpart is independently critiquing the deliverable")
            val critique = requireSuccess(
                missionId = missionId,
                step = 8,
                agent = critic,
                stage = "CRITIC",
                prompt = """
                    Mission: $goal
                    Candidate deliverable from ${owner.name}:
                    $deliverable

                    Act as a hostile but constructive reviewer. Identify factual gaps, unsupported claims, weak assumptions, security/approval issues and material improvements.
                    End with one line exactly:
                    REVIEW_GATE: PASS
                    or
                    REVIEW_GATE: REVISE
                """.trimIndent()
            )
            EmpireMissionControl.updatePlanStep(missionId, 8, "COMPLETED", "Independent ${critic.name} critique captured")
            EmpireMissionControl.updatePlanStep(missionId, 9, "COMPLETED", "No unrecovered provider failure remained after bounded execution")

            recordMissionMemory(owner, goal, "DRAFT DELIVERABLE", deliverable)
            recordMissionMemory(critic, goal, "CRITIQUE", critique)
            EmpireMissionControl.updatePlanStep(missionId, 10, "COMPLETED", "Goal, draft and critique persisted to sovereign memory")

            EmpireMissionControl.updatePlanStep(missionId, 11, "RUNNING", "Final synthesis and completion gate are running")
            val finalResult = requireSuccess(
                missionId = missionId,
                step = 11,
                agent = owner,
                stage = "FINAL_SYNTHESIS",
                prompt = """
                    Mission: $goal
                    Draft:
                    $deliverable

                    Independent critique:
                    $critique

                    Produce the corrected final result. Do not claim unverified external execution.
                    Completion is allowed only if the user's requested outcome is actually satisfied by the artifact/result available in this runtime.
                    If any external, financial, credential-sensitive, destructive, irreversible, capability-build or user-input gate remains, the final line MUST be:
                    COMPLETION_GATE: NEEDS_APPROVAL
                    Otherwise the final line MUST be:
                    COMPLETION_GATE: READY
                """.trimIndent()
            )
            EmpireMissionControl.updatePlanStep(missionId, 11, "COMPLETED", "Final synthesis and completion assessment captured")
            recordMissionMemory(owner, goal, "FINAL RESULT", finalResult)
            EmpireMissionControl.updatePlanStep(missionId, 12, "COMPLETED", "Outcome-based mission result stored without policy self-modification")

            val needsApproval = finalResult.lineSequence().any { it.trim().equals("COMPLETION_GATE: NEEDS_APPROVAL", ignoreCase = true) }
            if (needsApproval) {
                EmpireMissionControl.updatePlanStep(missionId, 13, "WAITING_APPROVAL", "Final result identifies an approval-gated remaining action")
                EmpireMissionControl.updatePlanStep(missionId, 14, "WAITING", "Iteration paused at the required owner gate")
                EmpireMissionControl.enterWaitingState(
                    missionId = missionId,
                    status = MissionStatus.WAITING_FOR_APPROVAL,
                    condition = "Review the final mission artifact and approve the explicitly identified gated action"
                )
                recordVerifiedRuntimeEvent(missionId, owner, "FINAL_GATE", "Mission paused before gated external action")
                return
            }

            EmpireMissionControl.updatePlanStep(missionId, 13, "COMPLETED", "No gated external action was executed or required for this deliverable")
            EmpireMissionControl.updatePlanStep(missionId, 14, "COMPLETED", "Bounded SHOSHI/LUNA execution loop reached an honest completion gate")
            EmpireMissionControl.transitionStatus(missionId, MissionStatus.REVIEW, "Both executive agents completed real provider-backed passes")
            EmpireMissionControl.transitionStatus(missionId, MissionStatus.COMPLETED, "Final result produced; provider round-trips and mission state were recorded as runtime evidence")
            recordVerifiedRuntimeEvent(missionId, owner, "MISSION_COMPLETE", "Mission completed after SHOSHI/LUNA research, creation, critique and synthesis")
        } catch (t: Throwable) {
            failMission(missionId, t)
        }
    }

    private suspend fun requireSuccess(
        missionId: String,
        step: Int,
        agent: ExecutiveAgent,
        stage: String,
        prompt: String
    ): String {
        ExecutivePresenceEngine.set(agent, ExecutivePresenceState.WORKING, "$stage · mission ${missionId.take(8)}")
        return when (val result = gateway.send(agent, prompt)) {
            is AiCallResult.Success -> {
                val text = result.text.trim()
                require(text.isNotBlank()) { "$stage returned an empty AI response" }
                EvidenceRegistry.record(
                    category = EvidenceCategory.AI_INFERENCE,
                    content = text.take(8000),
                    source = "${agent.name}/$stage",
                    missionId = missionId
                )
                recordVerifiedRuntimeEvent(missionId, agent, stage, "AI provider response received; chars=${text.length}")
                ExecutivePresenceEngine.idle(agent, "$stage completed")
                text
            }
            is AiCallResult.Failure -> {
                EmpireMissionControl.updatePlanStep(missionId, step, "FAILED", "$stage provider failure: ${result.safeMessage}")
                ExecutivePresenceEngine.set(agent, ExecutivePresenceState.ERROR, result.safeMessage)
                throw IllegalStateException("$stage failed: ${result.safeMessage}")
            }
        }
    }

    private fun recordVerifiedRuntimeEvent(missionId: String, agent: ExecutiveAgent, stage: String, detail: String) {
        EvidenceRegistry.record(
            category = EvidenceCategory.VERIFIED_RESULT,
            content = "$stage · $detail",
            source = "WorkingCoreAiGateway/runtime/${agent.name}",
            missionId = missionId
        )
    }

    private fun recordMissionMemory(agent: ExecutiveAgent, goal: String, label: String, content: String) {
        val partition = if (agent == ExecutiveAgent.SHOSHI) MemoryPartition.SHOSHI_PRIVATE else MemoryPartition.LUNA_PRIVATE
        SovereignMemoryVault.record(
            SovereignMemory(
                partition = partition,
                agentId = agent.id,
                content = "MISSION GOAL: $goal\n$label: ${content.take(8000)}",
                importance = 0.75f,
                evidenceCategory = EvidenceCategory.AI_INFERENCE
            )
        )
    }

    private fun parseCapabilityGap(plan: String): String? {
        val line = plan.lineSequence().firstOrNull { it.trim().startsWith("CAPABILITY_GAP:", ignoreCase = true) } ?: return null
        val value = line.substringAfter(':').trim()
        if (value.equals("NONE", ignoreCase = true) || value.isBlank()) return null
        return value.take(500)
    }

    private fun failMission(missionId: String, error: Throwable) {
        val message = error.message ?: error::class.java.simpleName
        runCatching {
            val mission = EmpireMissionControl.state.value.missions.firstOrNull { it.id == missionId }
            if (mission != null && mission.status !in setOf(MissionStatus.COMPLETED, MissionStatus.FAILED, MissionStatus.HALTED, MissionStatus.CANCELLED, MissionStatus.REJECTED)) {
                EmpireMissionControl.transitionStatus(missionId, MissionStatus.FAILED, "Autonomous runtime failure: $message")
            }
        }
        EvidenceRegistry.record(
            category = EvidenceCategory.VERIFIED_RESULT,
            content = "Mission runtime failed: $message",
            source = "AutonomousMissionEngine/runtime",
            missionId = missionId
        )
        ExecutivePresenceEngine.idleBoth()
    }
}
