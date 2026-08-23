package com.shoshiluna.executive.core.business

import java.security.KeyStore
import java.security.Signature
import java.util.Base64

/**
 * Verifies an approval proof using a public key stored in AndroidKeyStore.
 * Key creation and BiometricPrompt user authentication are intentionally kept outside this verifier;
 * callers must create a user-authentication-bound key and sign the exact canonical payload.
 */
class AndroidKeystoreApprovalVerifier(
    private val expectedAuthMethod: String = "BIOMETRIC_KEYSTORE_ECDSA",
    private val keyStoreProvider: () -> KeyStore = {
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    },
    private val nowMillis: () -> Long = { System.currentTimeMillis() }
) : ApprovalProofVerifier {

    override suspend fun verify(
        proposal: FinancialProposal,
        approval: AuthenticatedApproval
    ): ApprovalVerificationResult {
        if (approval.proposalId != proposal.proposalId) return fail("Proposal ID mismatch")
        if (approval.missionId != proposal.missionId) return fail("Mission ID mismatch")
        if (approval.amountMinorUnits != proposal.amountMinorUnits) return fail("Amount mismatch")
        if (!approval.currency.equals(proposal.currency, ignoreCase = true)) return fail("Currency mismatch")
        if (approval.sourceAccount != proposal.sourceAccount) return fail("Source account mismatch")
        if (approval.destinationAccount != proposal.destinationAccount) return fail("Destination account mismatch")
        if (approval.timestamp > approval.expiryTimestamp) return fail("Invalid approval time range")
        val now = nowMillis()
        if (now > approval.expiryTimestamp) return fail("Approval expired")
        if (approval.timestamp > now + 60_000L) return fail("Approval timestamp is in the future")
        if (approval.nonce.length < 16) return fail("Approval nonce is too short")
        if (approval.authenticationMethod != expectedAuthMethod) return fail("Unexpected authentication method")
        if (approval.keyAlias.isBlank() || approval.signatureProof.isBlank()) return fail("Missing key alias or signature")

        return try {
            val keyStore = keyStoreProvider()
            val certificate = keyStore.getCertificate(approval.keyAlias)
                ?: return fail("Keystore certificate not found for alias")
            val signatureBytes = Base64.getDecoder().decode(approval.signatureProof)
            val payload = ApprovalPayload(
                proposalId = proposal.proposalId,
                missionId = proposal.missionId,
                amountMinorUnits = proposal.amountMinorUnits,
                currency = proposal.currency,
                sourceAccount = proposal.sourceAccount,
                destinationAccount = proposal.destinationAccount,
                timestamp = approval.timestamp,
                expiryTimestamp = approval.expiryTimestamp,
                nonce = approval.nonce,
                approvedBy = approval.approvedBy
            ).canonicalBytes()

            val verifier = Signature.getInstance("SHA256withECDSA")
            verifier.initVerify(certificate.publicKey)
            verifier.update(payload)
            if (verifier.verify(signatureBytes)) ApprovalVerificationResult.Verified
            else fail("ECDSA approval signature verification failed")
        } catch (e: Exception) {
            fail("Approval proof verification error: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun fail(reason: String) = ApprovalVerificationResult.Failed(reason)
}
