package com.shoshiluna.executive.core.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResilientAiClientKeyRotationTest {
    @Test
    fun authFailureMayTryNextConfiguredKey() {
        assertTrue(ResilientAiClient.shouldTryNextKey(AiCallResult.Failure(AiConnectionState.ERROR, "auth", 401)))
        assertTrue(ResilientAiClient.shouldTryNextKey(AiCallResult.Failure(AiConnectionState.ERROR, "billing", 402)))
        assertTrue(ResilientAiClient.shouldTryNextKey(AiCallResult.Failure(AiConnectionState.ERROR, "permission", 403)))
    }

    @Test
    fun rateLimitDoesNotCycleKeysToBypassQuota() {
        assertFalse(ResilientAiClient.shouldTryNextKey(AiCallResult.Failure(AiConnectionState.RATE_LIMITED, "rate", 429)))
        assertTrue(ResilientAiClient.shouldFailOverProvider(AiCallResult.Failure(AiConnectionState.RATE_LIMITED, "rate", 429)))
    }

    @Test
    fun transientProviderFailureMayFailOverProvider() {
        assertTrue(ResilientAiClient.shouldFailOverProvider(AiCallResult.Failure(AiConnectionState.OFFLINE, "timeout")))
        assertTrue(ResilientAiClient.shouldFailOverProvider(AiCallResult.Failure(AiConnectionState.ERROR, "server", 503)))
    }
}
