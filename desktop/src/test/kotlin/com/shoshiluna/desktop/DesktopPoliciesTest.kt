package com.shoshiluna.desktop

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopPoliciesTest {
    @Test fun `mission plan is proactive and evidence gated`() {
        val plan = DesktopPolicies.plan("Create a legal revenue path")
        assertTrue(plan.steps.any { it.contains("Research") })
        assertTrue(plan.steps.any { it.contains("free", ignoreCase = true) })
        assertTrue(plan.steps.any { it.contains("Verify") })
    }

    @Test fun `adequate free capability blocks paid proposal`() {
        val decision = DesktopPolicies.evaluateCapabilitySpend(true, true, 1000.0, 10.0)
        assertFalse(decision.allowedToPropose)
    }

    @Test fun `negative ROI blocks paid proposal`() {
        val decision = DesktopPolicies.evaluateCapabilitySpend(true, false, 50.0, 100.0)
        assertFalse(decision.allowedToPropose)
    }

    @Test fun `positive ROI only permits proposal not autonomous spend`() {
        val decision = DesktopPolicies.evaluateCapabilitySpend(true, false, 1000.0, 100.0)
        assertTrue(decision.allowedToPropose)
        assertTrue(decision.reason.contains("approval", ignoreCase = true))
    }
}
