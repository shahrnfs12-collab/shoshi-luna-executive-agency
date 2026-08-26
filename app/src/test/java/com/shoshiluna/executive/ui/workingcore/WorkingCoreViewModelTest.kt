package com.shoshiluna.executive.ui.workingcore

import com.shoshiluna.executive.core.agent.ExecutiveAgent
import com.shoshiluna.executive.core.agent.WorkingCoreAiGateway
import com.shoshiluna.executive.core.network.AiCallResult
import com.shoshiluna.executive.core.network.AiConnectionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WorkingCoreViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun selectedAgentIsPassedToRealGatewayBoundary() = runTest(dispatcher) {
        var observed: ExecutiveAgent? = null
        val fake = object : WorkingCoreAiGateway {
            override suspend fun send(agent: ExecutiveAgent, prompt: String): AiCallResult {
                observed = agent
                return AiCallResult.Success("provider-result")
            }
        }
        val vm = WorkingCoreViewModel(fake)
        vm.selectAgent(ExecutiveAgent.LUNA)
        vm.send("arbitrary prompt")
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(ExecutiveAgent.LUNA, observed)
        assertEquals(AiConnectionState.CONNECTED, vm.uiState.value.connection)
        assertEquals("provider-result", vm.uiState.value.messages.last().text)
    }

    @Test
    fun providerFailureIsNotConvertedIntoFakeAssistantMessage() = runTest(dispatcher) {
        val fake = object : WorkingCoreAiGateway {
            override suspend fun send(agent: ExecutiveAgent, prompt: String): AiCallResult =
                AiCallResult.Failure(AiConnectionState.API_KEY_MISSING, "missing")
        }
        val vm = WorkingCoreViewModel(fake)
        vm.send("anything")
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(AiConnectionState.API_KEY_MISSING, vm.uiState.value.connection)
        assertFalse(vm.uiState.value.messages.any { !it.fromUser })
        assertEquals("missing", vm.uiState.value.error)
    }

    @Test
    fun duplicateSendWhileRequestActiveIsRejected() = runTest(dispatcher) {
        var calls = 0
        val fake = object : WorkingCoreAiGateway {
            override suspend fun send(agent: ExecutiveAgent, prompt: String): AiCallResult {
                calls++
                return AiCallResult.Success("ok")
            }
        }
        val vm = WorkingCoreViewModel(fake)
        vm.send("one")
        vm.send("two")
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, calls)
        assertTrue(vm.uiState.value.messages.count { it.fromUser } == 1)
    }

    @Test
    fun vagueWorkCommandAsksForRevenueTargetWithoutCallingProvider() = runTest(dispatcher) {
        var calls = 0
        val fake = object : WorkingCoreAiGateway {
            override suspend fun send(agent: ExecutiveAgent, prompt: String): AiCallResult {
                calls++
                return AiCallResult.Success("should-not-run")
            }
        }
        val vm = WorkingCoreViewModel(fake)
        vm.send("לכי לעבוד")
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(0, calls)
        assertTrue(vm.uiState.value.messages.last().text.contains("כמה כסף"))
        assertFalse(vm.uiState.value.isSending)
    }
}
