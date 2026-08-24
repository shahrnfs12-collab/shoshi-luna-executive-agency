package com.shoshiluna.executive.core.security

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

enum class ApprovalRiskLevel {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

data class ApprovalRequest(
    val id: String = UUID.randomUUID().toString(),
    val requestedAction: String,
    val requestingAgentId: String,
    val riskLevel: ApprovalRiskLevel,
    val reason: String,
    val timestamp: Long = System.currentTimeMillis(),
    var userDecision: Boolean? = null,
    var executionResult: String? = null
)

/**
 * ShoshiLuna Human Approval Gateway.
 * Absolute firewall for sensitive autonomous actions.
 */
object HumanApprovalGateway {
    
    private val _requests = MutableStateFlow<Map<String, ApprovalRequest>>(emptyMap())
    val requests = _requests.asStateFlow()
    @Volatile private var appContext: Context? = null
    private val gson = Gson()

    @Synchronized
    fun initialize(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        val json = prefs().getString(KEY_REQUESTS, null) ?: return
        val type = object : TypeToken<Map<String, ApprovalRequest>>() {}.type
        runCatching { gson.fromJson<Map<String, ApprovalRequest>>(json, type) }.getOrNull()?.let { _requests.value = it }
    }

    @Synchronized
    fun requestApproval(
        action: String,
        agentId: String,
        risk: ApprovalRiskLevel,
        reason: String
    ): String {
        val request = ApprovalRequest(
            requestedAction = action,
            requestingAgentId = agentId,
            riskLevel = risk,
            reason = reason
        )
        _requests.value = _requests.value + (request.id to request)
        persist()
        return request.id
    }

    @Synchronized
    fun provideDecision(requestId: String, decision: Boolean) {
        val request = _requests.value[requestId] ?: return
        _requests.value = _requests.value + (requestId to request.copy(userDecision = decision))
        persist()
    }

    @Synchronized
    fun isApproved(requestId: String): Boolean {
        return _requests.value[requestId]?.userDecision == true
    }

    @Synchronized
    fun resetForTest() { _requests.value = emptyMap() }

    private fun persist() {
        val context = appContext ?: return
        check(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_REQUESTS, gson.toJson(_requests.value)).commit()) { "Approval persistence failed" }
    }
    private fun prefs() = requireNotNull(appContext).getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private const val PREFS_NAME = "agency_approvals_v1"
    private const val KEY_REQUESTS = "requests"
}
