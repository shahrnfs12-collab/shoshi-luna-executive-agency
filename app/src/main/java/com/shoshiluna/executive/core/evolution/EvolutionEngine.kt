package com.shoshiluna.executive.core.evolution

import com.shoshiluna.executive.core.business.EvidenceRegistry
import com.shoshiluna.executive.core.security.ApprovalRiskLevel
import com.shoshiluna.executive.core.security.HumanApprovalGateway
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

enum class LearningStatus { PROPOSED, AWAITING_APPROVAL, ACTIVE, REJECTED }

data class VerifiedLearning(
    val id: String = UUID.randomUUID().toString(),
    val missionId: String,
    val lesson: String,
    val evidenceIds: List<String>,
    val status: LearningStatus = LearningStatus.PROPOSED,
    val approvalRequestId: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Evidence-gated evolution. Learning may improve future prompts/workflows, but it
 * never edits executable application code or grants itself permissions.
 */
object EvolutionEngine {
    private val _learnings = MutableStateFlow<Map<String, VerifiedLearning>>(emptyMap())
    val learnings = _learnings.asStateFlow()
    @Volatile private var appContext: Context? = null
    private val gson = Gson()

    @Synchronized
    fun initialize(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        val json = prefs().getString(KEY_LEARNINGS, null) ?: return
        val type = object : TypeToken<Map<String, VerifiedLearning>>() {}.type
        runCatching { gson.fromJson<Map<String, VerifiedLearning>>(json, type) }.getOrNull()?.let { _learnings.value = it }
    }

    @Synchronized
    fun propose(missionId: String, lesson: String, evidenceIds: List<String>): VerifiedLearning {
        require(missionId.isNotBlank()) { "missionId is required" }
        require(lesson.isNotBlank()) { "lesson is required" }
        require(evidenceIds.isNotEmpty()) { "Verified evidence is required for learning" }
        require(evidenceIds.all(EvidenceRegistry::isVerified)) { "Unverified evidence cannot train the agency" }
        val learning = VerifiedLearning(missionId = missionId, lesson = lesson.trim(), evidenceIds = evidenceIds.distinct())
        _learnings.value = _learnings.value + (learning.id to learning)
        persist()
        return learning
    }

    @Synchronized
    fun requestActivation(id: String, agentId: String): String {
        val learning = requireNotNull(_learnings.value[id]) { "Unknown learning id" }
        check(learning.status == LearningStatus.PROPOSED) { "Learning is not awaiting activation" }
        val approvalId = HumanApprovalGateway.requestApproval(
            action = "Activate verified agency learning: ${learning.lesson}",
            agentId = agentId,
            risk = ApprovalRiskLevel.MEDIUM,
            reason = "Persistent behavior changes require owner approval"
        )
        _learnings.value = _learnings.value + (id to learning.copy(status = LearningStatus.AWAITING_APPROVAL, approvalRequestId = approvalId))
        persist()
        return approvalId
    }

    @Synchronized
    fun finalizeActivation(id: String): VerifiedLearning {
        val learning = requireNotNull(_learnings.value[id]) { "Unknown learning id" }
        val approvalId = requireNotNull(learning.approvalRequestId) { "Activation was not requested" }
        check(HumanApprovalGateway.isApproved(approvalId)) { "Owner approval was not granted" }
        val active = learning.copy(status = LearningStatus.ACTIVE)
        _learnings.value = _learnings.value + (id to active)
        persist()
        return active
    }

    fun activeLessons(): List<VerifiedLearning> = _learnings.value.values.filter { it.status == LearningStatus.ACTIVE }
    @Synchronized fun resetForTest() { _learnings.value = emptyMap() }

    private fun persist() {
        val context = appContext ?: return
        check(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_LEARNINGS, gson.toJson(_learnings.value)).commit()) { "Evolution persistence failed" }
    }
    private fun prefs() = requireNotNull(appContext).getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private const val PREFS_NAME = "agency_evolution_v1"
    private const val KEY_LEARNINGS = "learnings"
}
