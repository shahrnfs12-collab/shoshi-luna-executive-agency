package com.shoshiluna.executive.core.presentation

import org.junit.Assert.*
import org.junit.Test

class CharacterFoundryTest {
    @Test fun cannotApproveIdentityBeforeStructuralGate() {
        val foundry = CharacterFoundry()
        val report = CharacterFoundryReport(CharacterFoundryRequest(avatar = ExecutiveAvatar.SHOSHI), FoundryStage.GENERATED)
        assertThrows(IllegalArgumentException::class.java) { foundry.approveIdentity(report, true, "looks correct") }
    }

    @Test fun readyRequiresStructureIdentityAndDeviceEvidence() {
        val structural = GlbValidationResult(isValid = true, skinCount = 1, animationNames = Glb3DValidator.REQUIRED_ANIMATIONS)
        val report = CharacterFoundryReport(
            request = CharacterFoundryRequest(avatar = ExecutiveAvatar.LUNA),
            stage = FoundryStage.READY,
            structuralResult = structural,
            identityApproved = true,
            deviceRuntimeVerified = true
        )
        assertTrue(report.ready)
        assertFalse(report.copy(deviceRuntimeVerified = false).ready)
    }
}
