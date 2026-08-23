package com.shoshiluna.executive.core.business

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

/**
 * ShoshiLuna Cost Engine.
 * Tracks verified business expenses.
 */
object CostEngine {
    private val _costs = MutableStateFlow<List<CostRecord>>(emptyList())
    val costs = _costs.asStateFlow()

    val totalVerifiedCosts = _costs.map { list ->
        list.filter { it.verified && it.environment == Environment.REAL }.sumOf { it.amount }
    }

    fun addCost(record: CostRecord) {
        _costs.value = _costs.value + record
    }

    fun verifyCost(id: String) {
        _costs.value = _costs.value.map {
            if (it.id == id) it.copy(verified = true) else it
        }
    }
}
