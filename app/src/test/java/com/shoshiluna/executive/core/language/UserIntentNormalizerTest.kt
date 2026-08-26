package com.shoshiluna.executive.core.language

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UserIntentNormalizerTest {
    @Test
    fun `normalizes known Hebrew and English routing typos`() {
        val result = UserIntentNormalizer.process("תיצרי סירטון ותעלי לגיטהב עם comit")
        assertTrue(result.normalizedForRouting.contains("תייצרי"))
        assertTrue(result.normalizedForRouting.contains("סרטון"))
        assertTrue(result.normalizedForRouting.contains("גיטהאב"))
        assertTrue(result.normalizedForRouting.contains("commit"))
    }

    @Test
    fun `fuzzy matches unseen routing typo`() {
        val result = UserIntentNormalizer.process("תבני סרטן חדש ופתחי brnch")
        assertTrue(result.normalizedForRouting.contains("סרטון"))
        assertTrue(result.normalizedForRouting.contains("branch"))
    }

    @Test
    fun `does not rewrite urls numbers or arbitrary identifiers`() {
        val original = "פתחי https://example.com/a1 ובדקי ABCD-7788"
        val result = UserIntentNormalizer.process(original)
        assertEquals(original, result.original)
        assertTrue(result.normalizedForRouting.contains("https://example.com/a1"))
        assertTrue(result.normalizedForRouting.contains("abcd-7788"))
    }

    @Test
    fun `preserves original message in model prompt`() {
        val original = "אני רוצה שתיבני לי סירטון"
        val result = UserIntentNormalizer.process(original)
        assertTrue(result.modelPrompt.contains(original))
    }
}
