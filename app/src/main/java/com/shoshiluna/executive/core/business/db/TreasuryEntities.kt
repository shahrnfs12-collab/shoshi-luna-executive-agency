package com.shoshiluna.executive.core.business.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.shoshiluna.executive.core.business.*

@Entity(
    tableName = "financial_idempotency",
    indices = [
        Index(value = ["proposalId"]),
        Index(value = ["externalTransactionId"], unique = true)
    ]
)
data class FinancialIdempotencyEntity(
    @PrimaryKey val requestId: String,
    val proposalId: String,
    val missionId: String,
    val correlationId: String,
    val amountMinorUnits: Long,
    val currency: String,
    val sourceAccount: String,
    val destinationAccount: String,
    val mode: ExecutionMode,
    val externalTransactionId: String?,
    val status: DurableTransactionStatus,
    val createdAt: Long,
    val updatedAt: Long
) {
    fun proposalSnapshot(): FinancialProposal = FinancialProposal(
        proposalId = proposalId,
        missionId = missionId,
        correlationId = correlationId,
        amountMinorUnits = amountMinorUnits,
        currency = currency,
        sourceAccount = sourceAccount,
        destinationAccount = destinationAccount,
        mode = mode
    )
}

@Entity(
    tableName = "financial_approvals",
    indices = [Index(value = ["proposalId"], unique = true)]
)
data class FinancialApprovalEntity(
    @PrimaryKey val approvalId: String,
    val proposalId: String,
    val missionId: String,
    val approvedBy: String,
    val amountMinorUnits: Long,
    val currency: String,
    val sourceAccount: String,
    val destinationAccount: String,
    val timestamp: Long,
    val expiryTimestamp: Long,
    val nonce: String,
    val authenticationMethod: String,
    val keyAlias: String,
    val signatureProof: String,
    val usedAt: Long?,
    val status: String
) {
    fun toDomain() = AuthenticatedApproval(
        approvalId = approvalId,
        proposalId = proposalId,
        missionId = missionId,
        approvedBy = approvedBy,
        amountMinorUnits = amountMinorUnits,
        currency = currency,
        sourceAccount = sourceAccount,
        destinationAccount = destinationAccount,
        timestamp = timestamp,
        expiryTimestamp = expiryTimestamp,
        nonce = nonce,
        authenticationMethod = authenticationMethod,
        keyAlias = keyAlias,
        signatureProof = signatureProof
    )
}

@Entity(
    tableName = "financial_transactions",
    indices = [
        Index(value = ["requestId"], unique = true),
        Index(value = ["proposalId"]),
        Index(value = ["externalTransactionId"], unique = true)
    ]
)
data class FinancialTransactionEntity(
    @PrimaryKey val transactionId: String,
    val proposalId: String,
    val requestId: String,
    val missionId: String,
    val correlationId: String,
    val externalTransactionId: String?,
    val mode: ExecutionMode,
    val sha256Evidence: String,
    val timestamp: Long
) {
    fun toDomain(entries: List<LedgerEntryEntity>) = PersistentDoubleEntryTransaction(
        transactionId = transactionId,
        proposalId = proposalId,
        requestId = requestId,
        missionId = missionId,
        correlationId = correlationId,
        externalTransactionId = externalTransactionId,
        mode = mode,
        entries = entries.map { it.toDomain() },
        sha256Evidence = sha256Evidence,
        timestamp = timestamp
    )
}

@Entity(
    tableName = "financial_ledger_entries",
    foreignKeys = [
        ForeignKey(
            entity = FinancialTransactionEntity::class,
            parentColumns = ["transactionId"],
            childColumns = ["transactionId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index(value = ["transactionId"]), Index(value = ["accountId"]), Index(value = ["mode"])]
)
data class LedgerEntryEntity(
    @PrimaryKey val entryId: String,
    val transactionId: String,
    val accountId: String,
    val type: LedgerEntryType,
    val amountMinorUnits: Long,
    val currency: String,
    val mode: ExecutionMode,
    val timestamp: Long
) {
    fun toDomain() = PersistentLedgerEntry(
        entryId = entryId,
        transactionId = transactionId,
        accountId = accountId,
        type = type,
        amountMinorUnits = amountMinorUnits,
        currency = currency,
        mode = mode,
        timestamp = timestamp
    )
}
