package com.shoshiluna.executive.core.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResilientAiClientTest {
    @Test fun `temporary failures permit provider failover`() {
        assertTrue(ResilientAiClient.shouldFailOver(AiCallResult.Failure(AiConnectionState.OFFLINE, "offline")))
        assertTrue(ResilientAiClient.shouldFailOver(AiCallResult.Failure(AiConnectionState.RATE_LIMITED, "quota", 429)))
        assertTrue(ResilientAiClient.shouldFailOver(AiCallResult.Failure(AiConnectionState.ERROR, "server", 503)))
    }

    @Test fun `authentication billing and permission failures rotate key then permit provider failover`() {
        val auth = AiCallResult.Failure(AiConnectionState.ERROR, "bad key", 401)
        val billing = AiCallResult.Failure(AiConnectionState.ERROR, "billing", 402)
        val denied = AiCallResult.Failure(AiConnectionState.ERROR, "denied", 403)
        assertTrue(ResilientAiClient.shouldTryNextKey(auth))
        assertTrue(ResilientAiClient.shouldTryNextKey(billing))
        assertTrue(ResilientAiClient.shouldTryNextKey(denied))
        assertTrue(ResilientAiClient.shouldFailOver(auth))
        assertTrue(ResilientAiClient.shouldFailOver(billing))
        assertTrue(ResilientAiClient.shouldFailOver(denied))
    }

    @Test fun `rate limit never rotates keys to bypass quota`() {
        val rateLimited = AiCallResult.Failure(AiConnectionState.RATE_LIMITED, "quota", 429)
        assertFalse(ResilientAiClient.shouldTryNextKey(rateLimited))
        assertTrue(ResilientAiClient.shouldFailOver(rateLimited))
    }

    @Test fun `dead model route falls back without rotating credentials`() {
        val notFound = AiCallResult.Failure(AiConnectionState.ERROR, "model route missing", 404)
        val unprocessable = AiCallResult.Failure(AiConnectionState.ERROR, "model unsupported", 422)

        assertFalse(ResilientAiClient.shouldTryNextKey(notFound))
        assertFalse(ResilientAiClient.shouldTryNextKey(unprocessable))
        assertTrue(ResilientAiClient.shouldFailOver(notFound))
        assertTrue(ResilientAiClient.shouldFailOver(unprocessable))
    }

    @Test fun `request timeout and provider conflict fall back without rotating keys`() {
        val timeout = AiCallResult.Failure(AiConnectionState.ERROR, "timeout", 408)
        val conflict = AiCallResult.Failure(AiConnectionState.ERROR, "conflict", 409)

        assertFalse(ResilientAiClient.shouldTryNextKey(timeout))
        assertFalse(ResilientAiClient.shouldTryNextKey(conflict))
        assertTrue(ResilientAiClient.shouldFailOver(timeout))
        assertTrue(ResilientAiClient.shouldFailOver(conflict))
    }
}
