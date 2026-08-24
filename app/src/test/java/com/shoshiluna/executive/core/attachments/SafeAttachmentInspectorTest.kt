package com.shoshiluna.executive.core.attachments

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeAttachmentInspectorTest {
    @Test fun acceptsNormalNestedZipPath() {
        assertTrue(SafeAttachmentInspector.validateZipPath("game/assets/hero.png"))
    }

    @Test fun rejectsZipSlipAndAbsolutePaths() {
        assertFalse(SafeAttachmentInspector.validateZipPath("../../secret.txt"))
        assertFalse(SafeAttachmentInspector.validateZipPath("/system/file"))
        assertFalse(SafeAttachmentInspector.validateZipPath("C:\\Windows\\file"))
    }

    @Test fun rejectsNullByteAndBackslashTraversal() {
        assertFalse(SafeAttachmentInspector.validateZipPath("safe/../bad"))
        assertFalse(SafeAttachmentInspector.validateZipPath("safe\u0000.png"))
    }
}
