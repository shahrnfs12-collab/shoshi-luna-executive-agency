package com.shoshiluna.executive.core.business

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

/**
 * ShoshiLuna Agency Treasury Ledger.
 * Hard Invariant: ALLOCATION = 5% of VERIFIED NET PROFIT.
 */
object TreasuryLedger {
    private val _allocations = MutableStateFlow<List<TreasuryAllocation>>(emptyList())
    val allocations = _allocations.asStateFlow()

    private val _spendProposals = MutableStateFlow<List<TreasurySpendProposal>>(emptyList())
    val spendProposals = _spendProposals.asStateFlow()

    // Idempotency: track which revenue/cost IDs have already been allocated
    private val allocatedRevenueIds = ConcurrentHashMap.newKeySet<String>()
    private val allocatedCostIds = ConcurrentHashMap.newKeySet<String>()

    val totalAllocatedBalance = _allocations.map { list ->
        list.filter { it.status == TreasuryStatus.ALLOCATED && it.environment == Environment.REAL }
            .sumOf { it.allocationAmount }
    }

    val totalFundedBalance = _allocations.map { list ->
        list.filter { it.status == TreasuryStatus.FUNDED && it.environment == Environment.REAL }
            .sumOf { it.allocationAmount }
    }

    /**
     * Perform the 5% allocation based on verified net profit.
     * Rule: Only RECEIVED revenue and VERIFIED costs.
     */
    fun performAllocation(
        revenueRecords: List<RevenueRecord>,
        costRecords: List<CostRecord>,
        environment: Environment
    ): TreasuryAllocation? {
        val verifiedRevenue = revenueRecords.filter { 
            it.status == RevenueStatus.RECEIVED && 
            it.environment == environment && 
            !allocatedRevenueIds.contains(it.id) 
        }
        val verifiedCosts = costRecords.filter { 
            it.verified && 
            it.environment == environment && 
            !allocatedCostIds.contains(it.id) 
        }

        if (verifiedRevenue.isEmpty()) return null

        val grossRevenue = verifiedRevenue.sumOf { it.amount }
        val costs = verifiedCosts.sumOf { it.amount }
        val netProfit = grossRevenue - costs
        
        if (netProfit <= 0) return null

        val allocationAmount = netProfit * 0.05
        
        val allocation = TreasuryAllocation(
            sourceRevenueIds = verifiedRevenue.map { it.id },
            sourceCostIds = verifiedCosts.map { it.id },
            verifiedGrossRevenue = grossRevenue,
            verifiedCosts = costs,
            verifiedNetProfit = netProfit,
            allocationAmount = allocationAmount,
            environment = environment
        )

        _allocations.value = _allocations.value + allocation
        
        // Mark as allocated to prevent double counting
        verifiedRevenue.forEach { allocatedRevenueIds.add(it.id) }
        verifiedCosts.forEach { allocatedCostIds.add(it.id) }
        
        return allocation
    }

    /** Test-only deterministic reset. Does not affect production flows. */
    fun resetForTest() {
        _allocations.value = emptyList()
        _spendProposals.value = emptyList()
        allocatedRevenueIds.clear()
        allocatedCostIds.clear()
    }

    fun updateAllocationStatus(id: String, newStatus: TreasuryStatus, verificationRef: String? = null) {
        _allocations.value = _allocations.value.map {
            if (it.id == id) {
                if (newStatus == TreasuryStatus.FUNDED && verificationRef == null) {
                    throw SecurityException("Transition to FUNDED requires verification evidence")
                }
                it.copy(status = newStatus, verificationRef = verificationRef ?: it.verificationRef)
            } else it
        }
    }

    fun proposeSpend(proposal: TreasurySpendProposal) {
        _spendProposals.value = _spendProposals.value + proposal
    }

    fun updateSpendStatus(proposalId: String, newStatus: SpendStatus, verificationRef: String? = null) {
        _spendProposals.value = _spendProposals.value.map {
            if (it.id == proposalId) {
                if (newStatus == SpendStatus.VERIFIED && verificationRef.isNullOrBlank()) {
                    throw SecurityException("VERIFIED spend requires verified evidence reference")
                }
                it.copy(status = newStatus, verificationRef = verificationRef ?: it.verificationRef)
            } else it
        }
    }
}
