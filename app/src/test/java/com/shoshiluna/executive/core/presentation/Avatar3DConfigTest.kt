package com.shoshiluna.executive.core.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class Avatar3DConfigTest {
    @Test fun modelPathsAreStable() {
        assertEquals("models/shoshi.glb", ExecutiveAvatar.SHOSHI.modelAssetPath)
        assertEquals("models/luna.glb", ExecutiveAvatar.LUNA.modelAssetPath)
    }

    @Test fun readinessRequiresBothModels() {
        assertFalse(Avatar3DReadiness(true, false).fullyReady)
        assertFalse(Avatar3DReadiness(false, true).fullyReady)
        assertFalse(Avatar3DReadiness(false, false).fullyReady)
    }

    @Test fun requiredAnimationContractExists() {
        assertEquals(8, ExecutiveAnimation.entries.size)
    }
}
