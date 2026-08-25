package com.shoshiluna.desktop

enum class Agent { SHOSHI, LUNA }
enum class ApprovalKind { EXTERNAL_COMMITMENT, MONEY_SPEND, CAPABILITY_BUILD, IRREVERSIBLE_ACTION }

data class MissionPlan(val goal: String, val steps: List<String>)
data class SpendDecision(val allowedToPropose: Boolean, val reason: String)

object DesktopPolicies {
    fun plan(goal: String): MissionPlan {
        val clean = goal.trim()
        require(clean.isNotEmpty()) { "Goal is required" }
        return MissionPlan(clean, listOf(
            "Infer outcome and constraints from context",
            "Research permitted sources and discover alternatives",
            "Rank paths by expected value, risk, time and cost",
            "Execute reversible zero-cost authorized work",
            "If capability is missing: search free/open-source options first",
            "If paid capability remains necessary: run cost-benefit/ROI and request owner approval",
            "Critique result, recover from failures and try materially different alternatives",
            "Verify evidence before declaring completion"
        ))
    }

    fun evaluateCapabilitySpend(
        neededForMission: Boolean,
        freeAdequateAlternativeExists: Boolean,
        expectedBenefit: Double,
        totalCost: Double
    ): SpendDecision = when {
        !neededForMission -> SpendDecision(false, "Spend is not required for a mission capability")
        freeAdequateAlternativeExists -> SpendDecision(false, "Adequate free alternative exists")
        totalCost <= 0.0 -> SpendDecision(true, "No monetary spend required")
        expectedBenefit <= totalCost -> SpendDecision(false, "Expected benefit does not exceed total cost")
        else -> SpendDecision(true, "Paid capability may be proposed; explicit owner approval is still required")
    }
}
