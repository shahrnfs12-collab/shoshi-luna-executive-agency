package com.shoshiluna.executive

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ApplicationLaunchRecoveryRuntimeTest {
    @Test fun mainActivityLaunchesWithCorruptGrowthMigrationData() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.getSharedPreferences("agency_ai_growth_v1", 0).edit().putString("needs", "{corrupt-json").commit()
        val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            scenario.onActivity { assertNotNull(it.window.decorView) }
        }
    }
}
