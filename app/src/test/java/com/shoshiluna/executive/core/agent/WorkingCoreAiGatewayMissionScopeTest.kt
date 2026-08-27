package com.shoshiluna.executive.core.agent

import com.shoshiluna.executive.core.network.AiCallResult
import com.shoshiluna.executive.core.network.AiConnectionState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkingCoreAiGatewayMissionScopeTest {
    private class RecordingProvider : AiProvider {
        override val name: String = "recording"
        override val model: String = "recording"
        var lastMissionId: String? = null

        override suspend fun chat(
            prompt: String,
            context: List<SovereignMemory>,
            missionId: String?,
            agent: ExecutiveAgent
        ): String = "unused"

        override suspend fun chatResult(
            prompt: String,
            context: List<SovereignMemory>,
            missionId: String?,
            agent: ExecutiveAgent
        ): AiCallResult {
            lastMissionId = missionId
            return AiCallResult.Failure(AiConnectionState.API_KEY_MISSING, "diagnostic")
        }
    }

    @Test
    fun `ordinary direct chat stays outside mission scope`() = runBlocking {
        val provider = RecordingProvider()
        val gateway = DefaultWorkingCoreAiGateway(provider)

        gateway.send(ExecutiveAgent.SHOSHI, "שלום, מה הסטטוס?")

        assertNull(provider.lastMissionId)
    }

    @Test
    fun `legacy structured autonomous prompt is conservatively mission scoped`() = runBlocking {
        val provider = RecordingProvider()
        val gateway = DefaultWorkingCoreAiGateway(provider)

        gateway.send(
            ExecutiveAgent.SHOSHI,
            "Mission: test\nProduce the strongest safe execution plan.\nCAPABILITY_GAP: NONE"
        )

        assertEquals("legacy-mission-scope", provider.lastMissionId)
    }

    @Test
    fun `explicit mission path preserves actual mission id`() = runBlocking {
        val provider = RecordingProvider()
        val gateway = DefaultWorkingCoreAiGateway(provider)

        gateway.sendMission(ExecutiveAgent.LUNA, "Mission: test\nResearch options", "mission-123")

        assertEquals("mission-123", provider.lastMissionId)
    }
}
