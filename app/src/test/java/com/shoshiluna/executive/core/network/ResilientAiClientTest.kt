package com.shoshiluna.executive.core.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResilientAiClientTest {
    @Test fun `temporary failures permit bounded failover`() {
        assertTrue(ResilientAiClient.shouldFailOver(AiCallResult.Failure(AiConnectionState.OFFLINE, "offline")))
        assertTrue(ResilientAiClient.shouldFailOver(AiCallResult.Failure(AiConnectionState.RATE_LIMITED, "quota", 429)))
        assertTrue(ResilientAiClient.shouldFailOver(AiCallResult.Failure(AiConnectionState.ERROR, "server", 503)))
    }

    @Test fun `authentication and billing failures never hide behind failover`() {
        assertFalse(ResilientAiClient.shouldFailOver(AiCallResult.Failure(AiConnectionState.ERROR, "bad key", 401)))
        assertFalse(ResilientAiClient.shouldFailOver(AiCallResult.Failure(AiConnectionState.ERROR, "billing", 402)))
        assertFalse(ResilientAiClient.shouldFailOver(AiCallResult.Failure(AiConnectionState.ERROR, "denied", 403)))
    }
}
