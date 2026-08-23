package com.shoshiluna.executive.core.business

import com.shoshiluna.executive.core.business.db.*
import java.security.MessageDigest
import java.util.UUID

interface ExternalFinancialAdapter {
    suspend fun executeRealTransfer(
        idempotencyKey: String,
        proposalId: String,
        amountMinorUnits: Long,
        currency: String,
        source: String,
        destination: String
    ): Result<ExternalTransferReceipt>

    suspend fun lookupByIdempotencyKey(key: String): Result<ExternalTransferReceipt?>
}

class TreasuryEngine(
    private val treasuryDao: TreasuryDao,
    private val approvalVerifier: ApprovalProofVerifier,
    private val externalAdapter: ExternalFinancialAdapter? = null,
    private val isEmergencyHaltActive: () -> Boolean
) {
    suspend fun submitAuthenticatedApproval(
        proposal: FinancialProposal,
        approval: AuthenticatedApproval
    ): Result<Unit> {
        if (proposal.mode != ExecutionMode.REAL) {
            return Result.failure(IllegalArgumentException("Human approval records are reserved for REAL proposals"))
        }
        return when (val verification = approvalVerifier.verify(proposal, approval)) {
            ApprovalVerificationResult.Verified -> runCatching {
                treasuryDao.insertApproval(
                    FinancialApprovalEntity(
                        approvalId = approval.approvalId,
                        proposalId = approval.proposalId,
                        missionId = approval.missionId,
                        approvedBy = approval.approvedBy,
                        amountMinorUnits = approval.amountMinorUnits,
                        currency = approval.currency.uppercase(),
                        sourceAccount = approval.sourceAccount,
                        destinationAccount = approval.destinationAccount,
                        timestamp = approval.timestamp,
                        expiryTimestamp = approval.expiryTimestamp,
                        nonce = approval.nonce,
                        authenticationMethod = approval.authenticationMethod,
                        keyAlias = approval.keyAlias,
                        signatureProof = approval.signatureProof,
                        usedAt = null,
                        status = "VERIFIED"
                    )
                )
            }
            is ApprovalVerificationResult.Failed -> Result.failure(SecurityException(verification.reason))
        }
    }

    suspend fun executeDurableTransfer(
        requestId: String,
        proposal: FinancialProposal
    ): Result<PersistentDoubleEntryTransaction> {
        if (isEmergencyHaltActive()) return Result.failure(SecurityException("EMERGENCY_HALT active"))
        validateProposal(requestId, proposal)?.let { return Result.failure(it) }

        val existing = treasuryDao.getIdempotencyRecord(requestId)
        if (existing != null) {
            if (existing.proposalId != proposal.proposalId) {
                return Result.failure(SecurityException("requestId is already bound to a different proposal"))
            }
            return resumeExisting(existing)
        }

        val now = System.currentTimeMillis()
        runCatching {
            treasuryDao.insertIdempotency(
                FinancialIdempotencyEntity(
                    requestId = requestId,
                    proposalId = proposal.proposalId,
                    missionId = proposal.missionId,
                    correlationId = proposal.correlationId,
                    amountMinorUnits = proposal.amountMinorUnits,
                    currency = proposal.currency.uppercase(),
                    sourceAccount = proposal.sourceAccount,
                    destinationAccount = proposal.destinationAccount,
                    mode = proposal.mode,
                    externalTransactionId = null,
                    status = DurableTransactionStatus.CREATED,
                    createdAt = now,
                    updatedAt = now
                )
            )
        }.getOrElse { return Result.failure(it) }

        return if (proposal.mode == ExecutionMode.SIMULATION) {
            treasuryDao.updateIdempotencyStatus(requestId, DurableTransactionStatus.EXECUTION_PENDING, System.currentTimeMillis())
            runCatching { commitLocalDoubleEntry(requestId, proposal, null) }
        } else {
            executeReal(requestId, proposal)
        }
    }

    private suspend fun executeReal(
        requestId: String,
        proposal: FinancialProposal
    ): Result<PersistentDoubleEntryTransaction> {
        val adapter = externalAdapter ?: run {
            treasuryDao.updateIdempotencyStatus(requestId, DurableTransactionStatus.FAILED, System.currentTimeMillis())
            return Result.failure(IllegalStateException("REAL_FINANCIAL_EXECUTION=LOCKED_NOT_CONFIGURED"))
        }

        val approval = treasuryDao.getApprovalForProposal(proposal.proposalId)
            ?: return Result.failure(SecurityException("Verified Human Approval required"))
        if (approval.usedAt != null || approval.status != "VERIFIED") {
            return Result.failure(SecurityException("Approval is already consumed or invalid"))
        }
        when (val verification = approvalVerifier.verify(proposal, approval.toDomain())) {
            ApprovalVerificationResult.Verified -> Unit
            is ApprovalVerificationResult.Failed -> return Result.failure(SecurityException(verification.reason))
        }

        treasuryDao.updateIdempotencyStatus(requestId, DurableTransactionStatus.EXECUTION_PENDING, System.currentTimeMillis())
        val adapterResult = adapter.executeRealTransfer(
            idempotencyKey = requestId,
            proposalId = proposal.proposalId,
            amountMinorUnits = proposal.amountMinorUnits,
            currency = proposal.currency.uppercase(),
            source = proposal.sourceAccount,
            destination = proposal.destinationAccount
        )
        if (adapterResult.isFailure) {
            // A transport failure can be ambiguous; keep EXECUTION_PENDING for explicit reconciliation.
            return Result.failure(adapterResult.exceptionOrNull() ?: IllegalStateException("External transfer result unknown"))
        }

        val receipt = adapterResult.getOrThrow()
        validateReceipt(proposal, receipt)?.let { return Result.failure(it) }
        treasuryDao.confirmExternalAndConsumeApproval(
            requestId = requestId,
            externalTransactionId = receipt.externalTransactionId,
            proposalId = proposal.proposalId,
            now = System.currentTimeMillis()
        )
        return runCatching { commitLocalDoubleEntry(requestId, proposal, receipt.externalTransactionId) }
    }

    suspend fun reconcilePendingTransaction(requestId: String): Result<DurableTransactionStatus> {
        if (isEmergencyHaltActive()) return Result.failure(SecurityException("EMERGENCY_HALT active"))
        val record = treasuryDao.getIdempotencyRecord(requestId)
            ?: return Result.failure(IllegalArgumentException("Unknown requestId"))
        if (record.mode != ExecutionMode.REAL) return Result.failure(IllegalStateException("Reconciliation only applies to REAL transfers"))

        if (record.status == DurableTransactionStatus.LEDGER_COMMITTED) return Result.success(record.status)
        if (record.status == DurableTransactionStatus.EXTERNAL_CONFIRMED) {
            return runCatching {
                commitLocalDoubleEntry(requestId, record.proposalSnapshot(), record.externalTransactionId)
                DurableTransactionStatus.LEDGER_COMMITTED
            }
        }
        if (record.status != DurableTransactionStatus.EXECUTION_PENDING) {
            return Result.failure(IllegalStateException("Cannot reconcile state ${record.status}"))
        }

        val adapter = externalAdapter ?: return Result.failure(IllegalStateException("External adapter not configured"))
        val lookup = adapter.lookupByIdempotencyKey(requestId)
        if (lookup.isFailure) return Result.failure(lookup.exceptionOrNull() ?: IllegalStateException("External reconciliation lookup failed"))
        val receipt = lookup.getOrNull() ?: return Result.failure(
            IllegalStateException("External status remains unknown; transaction left EXECUTION_PENDING")
        )
        val proposal = record.proposalSnapshot()
        validateReceipt(proposal, receipt)?.let { return Result.failure(it) }

        val storedApproval = treasuryDao.getApprovalForProposal(proposal.proposalId)
            ?: return Result.failure(SecurityException("Approval record missing during reconciliation"))
        if (storedApproval.usedAt == null) {
            treasuryDao.confirmExternalAndConsumeApproval(
                requestId = requestId,
                externalTransactionId = receipt.externalTransactionId,
                proposalId = proposal.proposalId,
                now = System.currentTimeMillis()
            )
        } else {
            treasuryDao.updateIdempotencyWithExternalId(
                requestId,
                receipt.externalTransactionId,
                DurableTransactionStatus.EXTERNAL_CONFIRMED,
                System.currentTimeMillis()
            )
        }
        commitLocalDoubleEntry(requestId, proposal, receipt.externalTransactionId)
        return Result.success(DurableTransactionStatus.LEDGER_COMMITTED)
    }

    private suspend fun resumeExisting(record: FinancialIdempotencyEntity): Result<PersistentDoubleEntryTransaction> = when (record.status) {
        DurableTransactionStatus.LEDGER_COMMITTED, DurableTransactionStatus.RECONCILED -> {
            val tx = treasuryDao.getTransactionByRequestId(record.requestId)
                ?: return Result.failure(IllegalStateException("Ledger state exists without transaction"))
            val entries = treasuryDao.getEntriesForTransaction(tx.transactionId)
            Result.success(tx.toDomain(entries))
        }
        DurableTransactionStatus.EXTERNAL_CONFIRMED -> runCatching {
            commitLocalDoubleEntry(record.requestId, record.proposalSnapshot(), record.externalTransactionId)
        }
        DurableTransactionStatus.EXECUTION_PENDING -> Result.failure(
            IllegalStateException("EXECUTION_PENDING: reconcile before retry; do not re-execute")
        )
        DurableTransactionStatus.FAILED -> Result.failure(IllegalStateException("Transaction is FAILED"))
        else -> Result.failure(IllegalStateException("Existing transaction state ${record.status} requires explicit recovery"))
    }

    private fun validateProposal(requestId: String, proposal: FinancialProposal): Exception? {
        if (requestId.isBlank()) return IllegalArgumentException("requestId cannot be blank")
        if (proposal.proposalId.isBlank() || proposal.missionId.isBlank() || proposal.correlationId.isBlank()) {
            return IllegalArgumentException("proposalId, missionId and correlationId are required")
        }
        if (proposal.amountMinorUnits <= 0L) return IllegalArgumentException("amountMinorUnits must be positive")
        if (!proposal.currency.matches(Regex("[A-Z]{3}", RegexOption.IGNORE_CASE))) return IllegalArgumentException("currency must be ISO-like 3 letters")
        if (proposal.sourceAccount.isBlank() || proposal.destinationAccount.isBlank()) return IllegalArgumentException("source/destination accounts required")
        if (proposal.sourceAccount == proposal.destinationAccount) return IllegalArgumentException("source and destination accounts must differ")
        return null
    }

    private fun validateReceipt(proposal: FinancialProposal, receipt: ExternalTransferReceipt): Exception? {
        if (!receipt.confirmed) return IllegalStateException("External receipt is not confirmed")
        if (receipt.externalTransactionId.isBlank()) return IllegalStateException("External receipt ID missing")
        if (receipt.proposalId != proposal.proposalId) return SecurityException("External receipt proposal mismatch")
        if (receipt.amountMinorUnits != proposal.amountMinorUnits) return SecurityException("External receipt amount mismatch")
        if (!receipt.currency.equals(proposal.currency, true)) return SecurityException("External receipt currency mismatch")
        if (receipt.sourceAccount != proposal.sourceAccount || receipt.destinationAccount != proposal.destinationAccount) {
            return SecurityException("External receipt routing mismatch")
        }
        return null
    }

    private suspend fun commitLocalDoubleEntry(
        requestId: String,
        proposal: FinancialProposal,
        externalTransactionId: String?
    ): PersistentDoubleEntryTransaction {
        val existing = treasuryDao.getTransactionByRequestId(requestId)
        if (existing != null) return existing.toDomain(treasuryDao.getEntriesForTransaction(existing.transactionId))

        val txId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val evidencePayload = listOf(
            txId, proposal.proposalId, requestId, proposal.missionId, proposal.correlationId,
            proposal.amountMinorUnits.toString(), proposal.currency.uppercase(), proposal.mode.name,
            externalTransactionId.orEmpty(), now.toString()
        ).joinToString("|")
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(evidencePayload.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

        val tx = FinancialTransactionEntity(
            transactionId = txId,
            proposalId = proposal.proposalId,
            requestId = requestId,
            missionId = proposal.missionId,
            correlationId = proposal.correlationId,
            externalTransactionId = externalTransactionId,
            mode = proposal.mode,
            sha256Evidence = hash,
            timestamp = now
        )
        val entries = listOf(
            LedgerEntryEntity(
                entryId = UUID.randomUUID().toString(), transactionId = txId,
                accountId = proposal.sourceAccount, type = LedgerEntryType.DEBIT,
                amountMinorUnits = proposal.amountMinorUnits, currency = proposal.currency.uppercase(),
                mode = proposal.mode, timestamp = now
            ),
            LedgerEntryEntity(
                entryId = UUID.randomUUID().toString(), transactionId = txId,
                accountId = proposal.destinationAccount, type = LedgerEntryType.CREDIT,
                amountMinorUnits = proposal.amountMinorUnits, currency = proposal.currency.uppercase(),
                mode = proposal.mode, timestamp = now
            )
        )
        treasuryDao.commitAtomicDoubleEntry(tx, entries, requestId)
        return tx.toDomain(entries)
    }
}
