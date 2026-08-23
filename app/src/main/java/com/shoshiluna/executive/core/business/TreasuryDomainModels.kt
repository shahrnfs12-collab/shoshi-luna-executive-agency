package com.shoshiluna.executive.core.business

import java.util.UUID

enum class ExecutionMode { SIMULATION, REAL }
enum class LedgerEntryType { DEBIT, CREDIT }
enum class DurableTransactionStatus {
    CREATED,
    APPROVED,
    EXECUTION_PENDING,
    EXTERNAL_CONFIRMED,
    LEDGER_COMMITTED,
    FAILED,
    RECONCILED
}

data class FinancialProposal(
    val proposalId: String = UUID.randomUUID().toString(),
    val missionId: String,
    val correlationId: String,
    val amountMinorUnits: Long,
    val currency: String,
    val sourceAccount: String,
    val destinationAccount: String,
    val mode: ExecutionMode = ExecutionMode.SIMULATION
)

data class AuthenticatedApproval(
    val approvalId: String = UUID.randomUUID().toString(),
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
    /** Base64-encoded ECDSA signature over ApprovalPayload.canonicalBytes(). */
    val signatureProof: String
)

data class ApprovalPayload(
    val proposalId: String,
    val missionId: String,
    val amountMinorUnits: Long,
    val currency: String,
    val sourceAccount: String,
    val destinationAccount: String,
    val timestamp: Long,
    val expiryTimestamp: Long,
    val nonce: String,
    val approvedBy: String
) {
    fun canonicalBytes(): ByteArray = listOf(
        proposalId,
        missionId,
        amountMinorUnits.toString(),
        currency.uppercase(),
        sourceAccount,
        destinationAccount,
        timestamp.toString(),
        expiryTimestamp.toString(),
        nonce,
        approvedBy
    ).joinToString("|").toByteArray(Charsets.UTF_8)
}

sealed class ApprovalVerificationResult {
    data object Verified : ApprovalVerificationResult()
    data class Failed(val reason: String) : ApprovalVerificationResult()
}

interface ApprovalProofVerifier {
    suspend fun verify(proposal: FinancialProposal, approval: AuthenticatedApproval): ApprovalVerificationResult
}

data class ExternalTransferReceipt(
    val externalTransactionId: String,
    val proposalId: String,
    val amountMinorUnits: Long,
    val currency: String,
    val sourceAccount: String,
    val destinationAccount: String,
    val confirmed: Boolean,
    val timestamp: Long
)

data class PersistentLedgerEntry(
    val entryId: String,
    val transactionId: String,
    val accountId: String,
    val type: LedgerEntryType,
    val amountMinorUnits: Long,
    val currency: String,
    val mode: ExecutionMode,
    val timestamp: Long
)

data class PersistentDoubleEntryTransaction(
    val transactionId: String,
    val proposalId: String,
    val requestId: String,
    val missionId: String,
    val correlationId: String,
    val externalTransactionId: String?,
    val mode: ExecutionMode,
    val entries: List<PersistentLedgerEntry>,
    val sha256Evidence: String,
    val timestamp: Long
)
