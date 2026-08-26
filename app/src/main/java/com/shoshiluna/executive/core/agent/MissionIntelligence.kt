package com.shoshiluna.executive.core.agent

/** Executive mission intelligence shared by SHOSHI/LUNA.
 * Pure/deterministic policy layer: no external action is executed here.
 */
data class MissionCompletionScore(
    val goalProgress: Int,
    val deliverableQuality: Int,
    val evidenceStrength: Int,
    val autonomy: Int,
    val verification: Int
) {
    val total: Int = listOf(goalProgress, deliverableQuality, evidenceStrength, autonomy, verification)
        .map { it.coerceIn(0, 100) }
        .average().toInt()
    val readyToDeclareDone: Boolean = total >= 85 && evidenceStrength >= 70 && verification >= 70
}

data class MissionLearningRecord(
    val goalClass: String,
    val approach: String,
    val outcomeScore: Int,
    val failureReason: String? = null,
    val reusableLesson: String
)

data class FailedApproach(
    val approach: String,
    val reason: String,
    val timestamp: Long = System.currentTimeMillis()
)

enum class RecoveryAction { RETRY, ALTERNATE_TOOL, ALTERNATE_PROVIDER, REPLAN, BUILD_CAPABILITY_PROPOSAL, ASK_USER, STOP_AT_APPROVAL, DECLARE_FAILED }

object MissionIntelligence {
    /**
     * Failure is a last resort. A reversible mission must exhaust reasonable,
     * materially different safe alternatives before it may be declared failed.
     * Repeating the same failing action indefinitely is explicitly not allowed.
     */
    fun recoveryPlan(
        attempts: Int,
        reversible: Boolean,
        alternateToolAvailable: Boolean,
        alternateProviderAvailable: Boolean,
        approvalRequired: Boolean,
        missingIrreducibleUserInput: Boolean,
        missingCapability: Boolean = false,
        distinctAlternativesTried: Int = 0,
        reasonableAlternativesRemaining: Boolean = true
    ): RecoveryAction = when {
        approvalRequired -> RecoveryAction.STOP_AT_APPROVAL
        missingIrreducibleUserInput -> RecoveryAction.ASK_USER
        attempts < 2 && reversible -> RecoveryAction.RETRY
        alternateToolAvailable -> RecoveryAction.ALTERNATE_TOOL
        alternateProviderAvailable -> RecoveryAction.ALTERNATE_PROVIDER
        missingCapability -> RecoveryAction.BUILD_CAPABILITY_PROPOSAL
        reversible && reasonableAlternativesRemaining -> RecoveryAction.REPLAN
        !reversible -> RecoveryAction.ASK_USER
        distinctAlternativesTried >= 2 && !reasonableAlternativesRemaining -> RecoveryAction.DECLARE_FAILED
        else -> RecoveryAction.REPLAN
    }

    fun mayDeclareFailure(
        hardSafetyOrPolicyBlock: Boolean,
        irreducibleExternalBlock: Boolean,
        reasonableAlternativesRemaining: Boolean,
        distinctAlternativesTried: Int
    ): Boolean {
        if (hardSafetyOrPolicyBlock || irreducibleExternalBlock) return true
        return !reasonableAlternativesRemaining && distinctAlternativesTried >= 2
    }

    fun failureSummary(history: List<FailedApproach>): String = buildString {
        append("Attempted alternatives: ")
        if (history.isEmpty()) append("none recorded")
        else history.forEachIndexed { index, item ->
            if (index > 0) append(" | ")
            append(index + 1).append("). ").append(item.approach).append(" -> ").append(item.reason)
        }
    }

    fun score(
        goalProgress: Int,
        deliverableQuality: Int,
        evidenceStrength: Int,
        userInterventionCount: Int,
        verification: Int
    ): MissionCompletionScore {
        val autonomy = (100 - userInterventionCount.coerceAtLeast(0) * 12).coerceIn(0, 100)
        return MissionCompletionScore(goalProgress, deliverableQuality, evidenceStrength, autonomy, verification)
    }

    fun completionDecision(score: MissionCompletionScore): String = when {
        score.readyToDeclareDone -> "VERIFIED_COMPLETE"
        score.total >= 65 -> "ITERATE_BEFORE_DONE"
        else -> "REPLAN_REQUIRED"
    }
}
