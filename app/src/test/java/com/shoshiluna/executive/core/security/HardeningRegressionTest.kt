package com.shoshiluna.executive.core.security

import com.shoshiluna.executive.core.agent.ExecutiveAgent
import com.shoshiluna.executive.core.business.ArtifactFactory
import com.shoshiluna.executive.core.business.ArtifactType
import com.shoshiluna.executive.core.empire.EmpireMissionControl
import com.shoshiluna.executive.core.empire.MissionStatus
import com.shoshiluna.executive.core.engine.AnomalyType
import com.shoshiluna.executive.core.engine.BlackSwanEngine
import com.shoshiluna.executive.core.engine.BlackSwanEvent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HardeningRegressionTest {
    @Before
    fun setUp() {
        EmpireMissionControl.resetForTest()
        ArtifactFactory.resetForTest()
        BlackSwanEngine.clearEvents()
    }

    @After
    fun tearDown() {
        EmpireMissionControl.resetForTest()
    }

    @Test(expected = IllegalStateException::class)
    fun `emergency halt blocks new missions`() {
        EmpireMissionControl.emergencyHalt()
        EmpireMissionControl.createMission("must not run", ExecutiveAgent.SHOSHI.id)
    }

    @Test
    fun `emergency halt halts non-terminal missions`() {
        val mission = EmpireMissionControl.createMission("test mission", ExecutiveAgent.SHOSHI.id)
        EmpireMissionControl.transitionStatus(mission.id, MissionStatus.RUNNING, "test")
        EmpireMissionControl.emergencyHalt()
        assertEquals(MissionStatus.HALTED, EmpireMissionControl.state.value.missions.first { it.id == mission.id }.status)
    }

    @Test
    fun `artifact factory generates verifiable sha256`() {
        val artifact = ArtifactFactory.createArtifact(ArtifactType.REPORT, ExecutiveAgent.SHOSHI.id, "task-1", "verified content")
        assertTrue(artifact.hash.matches(Regex("[0-9a-f]{64}")))
        assertTrue(ArtifactFactory.verifyIntegrity(artifact))
    }

    @Test
    fun `severity threshold triggers hard halt`() {
        BlackSwanEngine.reportAnomaly(BlackSwanEvent(type = AnomalyType.DATA_DEGRADATION, severity = 0.80f, evidence = "test evidence"))
        assertTrue(EmpireMissionControl.state.value.emergencyHalt)
    }
}
