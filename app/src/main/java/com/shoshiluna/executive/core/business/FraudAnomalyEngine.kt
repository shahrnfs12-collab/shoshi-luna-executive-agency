package com.shoshiluna.executive.core.business

import com.shoshiluna.executive.core.audit.AuditTrail
import com.shoshiluna.executive.core.audit.ExecutiveAudit
import java.util.UUID

/**
 * ShoshiLuna Fraud & Anomaly Detection Engine.
 * Audits ledger mutations and payment verification.
 */
object FraudAnomalyEngine {

    fun auditRevenueRecord(record: RevenueRecord): Boolean {
        // Rule: Detect impossible balances (e.g., negative real revenue)
        if (record.amount < 0 && record.status == RevenueStatus.RECEIVED) {
            reportFraud("Negative Received Revenue Detected", record.id)
            return false
        }

        // Rule: Detect duplicate payment references
        val existing = RevenueLedger.records.value.filter { 
            it.verificationRef != null && it.verificationRef == record.verificationRef && it.id != record.id 
        }
        if (existing.isNotEmpty()) {
            reportFraud("Duplicate Payment Reference Detected", record.id)
            return false
        }

        return true
    }

    private fun reportFraud(reason: String, resourceId: String) {
        AuditTrail.append(
            ExecutiveAudit(
                agentId = "SYSTEM",
                action = "FRAUD_DETECTED",
                result = "REASON: $reason",
                permissionDecisionId = resourceId
            )
        )
    }
}
