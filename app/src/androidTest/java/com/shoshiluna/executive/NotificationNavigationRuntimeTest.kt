package com.shoshiluna.executive

import android.Manifest
import android.content.Intent
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.Rule
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationNavigationRuntimeTest {
    @get:Rule val composeRule = createEmptyComposeRule()

    @Test fun notificationIntentOpensMissionControl() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val descriptor: ParcelFileDescriptor = instrumentation.uiAutomation.executeShellCommand(
                "pm grant ${instrumentation.targetContext.packageName} ${Manifest.permission.POST_NOTIFICATIONS}"
            )
            descriptor.close()
        }
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_TAB, 2)
            .putExtra(MainActivity.EXTRA_MISSION_ID, "runtime-mission")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        ActivityScenario.launch<MainActivity>(intent).use {
            composeRule.onNodeWithText("Mission Control").assertIsDisplayed()
        }
    }
}
