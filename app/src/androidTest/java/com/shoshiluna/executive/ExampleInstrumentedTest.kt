package com.shoshiluna.executive

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun applicationIdMatchesProductionPackage() {
        val appContext = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        assertEquals("com.shoshiluna.executive", appContext.packageName)
    }
}
