package com.shoshiluna.executive.core.business

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

/**
 * ShoshiLuna Revenue Ledger.
 * Hard Invariant: REAL REVENUE = RECEIVED ONLY.
 */
object RevenueLedger {
    private val _records = MutableStateFlow<List<RevenueRecord>>(emptyList())
    val records = _records.asStateFlow()

    val totalRealRevenue = _records.map { list ->
        list.filter { it.status == RevenueStatus.RECEIVED && it.environment == Environment.REAL }
            .sumOf { it.amount }
    }

    val pipelineValue = _records.map { list ->
        list.filter { it.status == RevenueStatus.PIPELINE }.sumOf { it.amount }
    }

    fun addRecord(record: RevenueRecord) {
        // AI Agents cannot directly set RECEIVED status
        if (record.status == RevenueStatus.RECEIVED && record.verificationRef == null) {
            throw SecurityException("RECEIVED status requires a valid verification reference")
        }
        _records.value = _records.value + record
    }

    /** Test-only deterministic reset. Does not affect production flows. */
    fun resetForTest() {
        _records.value = emptyList()
    }

    fun updateStatus(recordId: String, newStatus: RevenueStatus, verificationRef: String? = null) {
        _records.value = _records.value.map {
            if (it.id == recordId) {
                if (newStatus == RevenueStatus.RECEIVED && verificationRef == null) {
                    throw SecurityException("Transition to RECEIVED requires verification evidence")
                }
                it.copy(status = newStatus, verificationRef = verificationRef ?: it.verificationRef)
            } else it
        }
    }
}
