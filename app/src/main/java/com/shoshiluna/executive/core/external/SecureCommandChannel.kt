package com.shoshiluna.executive.core.external

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

enum class CloudCommandStatus { RECEIVED, ACCEPTED, APPROVAL_REQUIRED, REJECTED, REPLAYED }

data class SignedCloudCommand(
    val id: String = UUID.randomUUID().toString(),
    val issuedAt: Long,
    val expiresAt: Long,
    val nonce: String,
    val targetAgentId: String,
    val instruction: String,
    val signatureHex: String
) {
    fun canonicalPayload(): String = listOf(id, issuedAt, expiresAt, nonce, targetAgentId, instruction).joinToString("\n")
}

data class CloudCommandDecision(val status: CloudCommandStatus, val reason: String)

/**
 * Provider-neutral command-channel gate. It does not open a socket or contain a server secret.
 * A future owner-controlled HTTPS backend may deliver signed envelopes through this gate.
 */
class SecureCommandChannel(
    private val now: () -> Long = System::currentTimeMillis,
    private val acceptedClockSkewMs: Long = 60_000L
) {
    private val consumedNonces = LinkedHashSet<String>()

    @Synchronized
    fun verify(command: SignedCloudCommand, sharedSecret: ByteArray): CloudCommandDecision {
        if (sharedSecret.size < 32) return CloudCommandDecision(CloudCommandStatus.REJECTED, "Channel secret must be at least 256 bits")
        if (command.instruction.isBlank() || command.targetAgentId !in setOf("SHOSHI", "LUNA")) {
            return CloudCommandDecision(CloudCommandStatus.REJECTED, "Invalid target or empty instruction")
        }
        val current = now()
        if (command.expiresAt <= command.issuedAt || current + acceptedClockSkewMs < command.issuedAt || current - acceptedClockSkewMs > command.expiresAt) {
            return CloudCommandDecision(CloudCommandStatus.REJECTED, "Command is outside its validity window")
        }
        if (command.nonce in consumedNonces) return CloudCommandDecision(CloudCommandStatus.REPLAYED, "Nonce was already consumed")
        val expected = hmacHex(sharedSecret, command.canonicalPayload())
        val supplied = runCatching { command.signatureHex.hexToBytes() }.getOrNull()
            ?: return CloudCommandDecision(CloudCommandStatus.REJECTED, "Malformed signature")
        if (!MessageDigest.isEqual(expected.hexToBytes(), supplied)) return CloudCommandDecision(CloudCommandStatus.REJECTED, "Signature mismatch")
        consumedNonces += command.nonce
        while (consumedNonces.size > 1_000) consumedNonces.remove(consumedNonces.first())
        val approval = command.instruction.contains(Regex("(?i)\\b(pay|purchase|publish|send|transfer|withdraw|delete|deploy|contact)\\b"))
        return if (approval) CloudCommandDecision(CloudCommandStatus.APPROVAL_REQUIRED, "External, financial or irreversible instruction")
        else CloudCommandDecision(CloudCommandStatus.ACCEPTED, "Authenticated reversible internal instruction")
    }

    companion object {
        internal fun hmacHex(secret: ByteArray, payload: String): String {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(secret, "HmacSHA256"))
            return mac.doFinal(payload.toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it) }
        }
        private fun String.hexToBytes(): ByteArray {
            require(length % 2 == 0)
            return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        }
    }
}
