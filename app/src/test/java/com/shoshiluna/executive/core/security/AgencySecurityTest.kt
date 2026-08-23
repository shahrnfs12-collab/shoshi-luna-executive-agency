package com.shoshiluna.executive.core.security

import com.shoshiluna.executive.core.agent.*
import com.shoshiluna.executive.core.empire.EmpireMissionControl
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AgencySecurityTest {

    @Before
    fun setup() {
        EmpireMissionControl.resetForTest()
        PermissionKernel.resetForTest()
        MultiAgentCoordinator.resetForTest()
    }

    @Test
    fun `test SHOSHI cannot recruit LUNA`() {
        val request = PermissionRequest(
            agentId = ExecutiveAgent.SHOSHI.id,
            capability = AgentCapability.RECRUIT_AGENT,
            targetResourceId = ExecutiveAgent.LUNA.id
        )
        val decision = PermissionKernel.authorize(request)
        assertFalse("SHOSHI should not be allowed to recruit LUNA", decision.allowed)
        assertEquals("Executive Peer Protection Violation", decision.reason)
    }

    @Test
    fun `test LUNA cannot recruit SHOSHI`() {
        val request = PermissionRequest(
            agentId = ExecutiveAgent.LUNA.id,
            capability = AgentCapability.RECRUIT_AGENT,
            targetResourceId = ExecutiveAgent.SHOSHI.id
        )
        val decision = PermissionKernel.authorize(request)
        assertFalse("LUNA should not be allowed to recruit SHOSHI", decision.allowed)
    }

    @Test
    fun `test Sub-agent cannot escalate permissions`() {
        val shoshi = AgentIdentity(
            id = ExecutiveAgent.SHOSHI.id,
            name = "SHOSHI",
            role = AgentRole.EXECUTIVE_OVERSIGHT,
            allowedCapabilities = setOf(AgentCapability.READ_SHARED_MEMORY)
        )
        val requestedChildCaps = setOf(AgentCapability.READ_SHARED_MEMORY, AgentCapability.WRITE_PRIVATE_MEMORY)
        
        val isValid = PermissionKernel.validateChildPermissions(shoshi, requestedChildCaps)
        assertFalse("Child should not have more permissions than parent", isValid)
    }

    @Test
    fun `test Emergency Halt blocks execution`() {
        EmpireMissionControl.emergencyHalt()
        val request = PermissionRequest(
            agentId = ExecutiveAgent.SHOSHI.id,
            capability = AgentCapability.READ_SHARED_MEMORY
        )
        val decision = PermissionKernel.authorize(request)
        assertFalse("Action should be blocked during halt", decision.allowed)
        assertEquals("Emergency Halt Active", decision.reason)
    }

    @Test
    fun `test Fail-closed on unknown agent`() {
        val request = PermissionRequest(
            agentId = "UNKNOWN-AGENT",
            capability = AgentCapability.READ_SHARED_MEMORY
        )
        val decision = PermissionKernel.authorize(request)
        assertFalse("Unknown agent should be denied", decision.allowed)
    }
}
