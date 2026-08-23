package com.shoshiluna.executive.core.business.db

import androidx.room.*
import com.shoshiluna.executive.core.business.DurableTransactionStatus
import com.shoshiluna.executive.core.business.LedgerEntryType

@Dao
interface TreasuryDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertIdempotency(record: FinancialIdempotencyEntity)

    @Query("SELECT * FROM financial_idempotency WHERE requestId = :requestId LIMIT 1")
    suspend fun getIdempotencyRecord(requestId: String): FinancialIdempotencyEntity?

    @Query("UPDATE financial_idempotency SET status = :status, updatedAt = :updatedAt WHERE requestId = :requestId")
    suspend fun updateIdempotencyStatus(requestId: String, status: DurableTransactionStatus, updatedAt: Long): Int

    @Query("UPDATE financial_idempotency SET externalTransactionId = :extId, status = :status, updatedAt = :updatedAt WHERE requestId = :requestId")
    suspend fun updateIdempotencyWithExternalId(requestId: String, extId: String, status: DurableTransactionStatus, updatedAt: Long): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertApproval(approval: FinancialApprovalEntity)

    @Query("SELECT * FROM financial_approvals WHERE proposalId = :proposalId LIMIT 1")
    suspend fun getApprovalForProposal(proposalId: String): FinancialApprovalEntity?

    @Query("UPDATE financial_approvals SET usedAt = :usedAt, status = 'CONSUMED' WHERE proposalId = :proposalId AND usedAt IS NULL AND status = 'VERIFIED'")
    suspend fun markApprovalUsed(proposalId: String, usedAt: Long): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTransaction(tx: FinancialTransactionEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertLedgerEntries(entries: List<LedgerEntryEntity>)

    @Query("SELECT * FROM financial_transactions WHERE requestId = :requestId LIMIT 1")
    suspend fun getTransactionByRequestId(requestId: String): FinancialTransactionEntity?

    @Query("SELECT * FROM financial_ledger_entries WHERE transactionId = :transactionId ORDER BY entryId")
    suspend fun getEntriesForTransaction(transactionId: String): List<LedgerEntryEntity>

    @Transaction
    suspend fun confirmExternalAndConsumeApproval(
        requestId: String,
        externalTransactionId: String,
        proposalId: String,
        now: Long
    ) {
        val changed = updateIdempotencyWithExternalId(
            requestId,
            externalTransactionId,
            DurableTransactionStatus.EXTERNAL_CONFIRMED,
            now
        )
        if (changed != 1) error("Idempotency record missing while confirming external transfer")
        val consumed = markApprovalUsed(proposalId, now)
        if (consumed != 1) error("Approval could not be consumed atomically")
    }

    @Transaction
    suspend fun commitAtomicDoubleEntry(
        tx: FinancialTransactionEntity,
        entries: List<LedgerEntryEntity>,
        requestId: String
    ) {
        require(entries.size == 2) { "Exactly two ledger entries required" }
        val debits = entries.filter { it.type == LedgerEntryType.DEBIT }
        val credits = entries.filter { it.type == LedgerEntryType.CREDIT }
        require(debits.size == 1 && credits.size == 1) { "Exactly one debit and one credit required" }
        require(debits.first().amountMinorUnits > 0L) { "Ledger amount must be positive" }
        require(debits.first().amountMinorUnits == credits.first().amountMinorUnits) { "Debit/credit parity failed" }
        require(debits.first().currency == credits.first().currency) { "Ledger currency mismatch" }
        require(debits.first().transactionId == tx.transactionId && credits.first().transactionId == tx.transactionId) {
            "Ledger entries must belong to transaction"
        }
        insertTransaction(tx)
        insertLedgerEntries(entries)
        val changed = updateIdempotencyStatus(requestId, DurableTransactionStatus.LEDGER_COMMITTED, System.currentTimeMillis())
        if (changed != 1) error("Idempotency record missing during ledger commit")
    }
}
