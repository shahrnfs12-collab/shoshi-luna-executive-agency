package com.shoshiluna.executive.core.audit

import java.util.UUID

data class ExecutiveAudit(
    val auditId: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val agentId: String,
    val missionId: String? = null,
    val action: String,
    val result: String,
    val permissionDecisionId: String? = null
)

object AuditTrail {
    private val entries = ArrayDeque<ExecutiveAudit>()

    @Synchronized 
    fun append(entry: ExecutiveAudit) { 
        entries.addFirst(entry)
        while (entries.size > 5000) entries.removeLast() 
    }

    @Synchronized 
    fun recent(limit: Int = 100): List<ExecutiveAudit> = entries.take(limit)
}
