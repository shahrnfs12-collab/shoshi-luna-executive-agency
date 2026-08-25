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

enum class RecoveryAction { RETRY, ALTERNATE_TOOL, ALTERNATE_PROVIDER, REPLAN, ASK_USER, STOP_AT_APPROVAL }

object MissionIntelligence {
    fun recoveryPlan(
        attempts: Int,
        reversible: Boolean,
        alternateToolAvailable: Boolean,
        alternateProviderAvailable: Boolean,
        approvalRequired: Boolean,
        missingIrreducibleUserInput: Boolean
    ): RecoveryAction = when {
        approvalRequired -> RecoveryAction.STOP_AT_APPROVAL
        missingIrreducibleUserInput -> RecoveryAction.ASK_USER
        attempts < 2 && reversible -> RecoveryAction.RETRY
        alternateToolAvailable -> RecoveryAction.ALTERNATE_TOOL
        alternateProviderAvailable -> RecoveryAction.ALTERNATE_PROVIDER
        reversible -> RecoveryAction.REPLAN
        else -> RecoveryAction.ASK_USER
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
