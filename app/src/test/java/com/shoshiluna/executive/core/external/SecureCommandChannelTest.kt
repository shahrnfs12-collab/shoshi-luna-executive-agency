package com.shoshiluna.executive.core.external

import org.junit.Assert.assertEquals
import org.junit.Test

class SecureCommandChannelTest {
    private val now = 2_000_000L
    private val secret = ByteArray(32) { (it + 1).toByte() }

    @Test fun `accepts signed internal instruction once`() {
        val unsigned = SignedCloudCommand(issuedAt = now - 1_000, expiresAt = now + 30_000, nonce = "n-1", targetAgentId = "SHOSHI", instruction = "Review agent reports", signatureHex = "")
        val signed = unsigned.copy(signatureHex = SecureCommandChannel.hmacHex(secret, unsigned.canonicalPayload()))
        val channel = SecureCommandChannel(now = { now })
        assertEquals(CloudCommandStatus.ACCEPTED, channel.verify(signed, secret).status)
        assertEquals(CloudCommandStatus.REPLAYED, channel.verify(signed, secret).status)
    }

    @Test fun `financial or external command requires approval`() {
        val unsigned = SignedCloudCommand(issuedAt = now, expiresAt = now + 30_000, nonce = "n-2", targetAgentId = "LUNA", instruction = "Transfer funds", signatureHex = "")
        val signed = unsigned.copy(signatureHex = SecureCommandChannel.hmacHex(secret, unsigned.canonicalPayload()))
        assertEquals(CloudCommandStatus.APPROVAL_REQUIRED, SecureCommandChannel(now = { now }).verify(signed, secret).status)
    }

    @Test fun `rejects bad signature and short secret`() {
        val command = SignedCloudCommand(issuedAt = now, expiresAt = now + 30_000, nonce = "n-3", targetAgentId = "SHOSHI", instruction = "Review", signatureHex = "00")
        assertEquals(CloudCommandStatus.REJECTED, SecureCommandChannel(now = { now }).verify(command, secret).status)
        assertEquals(CloudCommandStatus.REJECTED, SecureCommandChannel(now = { now }).verify(command, byteArrayOf(1)).status)
    }
}
