package com.shoshiluna.executive

import android.Manifest
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.shoshiluna.executive.core.empire.EmpireMissionControl
import com.shoshiluna.executive.core.empire.MissionStatus
import com.shoshiluna.executive.core.security.ApprovalRiskLevel
import com.shoshiluna.executive.core.security.HumanApprovalGateway
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Rule
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationNavigationRuntimeTest {
    @get:Rule val composeRule = createEmptyComposeRule()

    @Before fun resetState() {
        EmpireMissionControl.resetForTest()
        HumanApprovalGateway.resetForTest()
    }

    @After fun cleanup() {
        HumanApprovalGateway.resetForTest()
        EmpireMissionControl.resetForTest()
    }

    @Test fun notificationIntentOpensMissionControl() {
        grantNotificationPermission()
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_TAB, 2)
            .putExtra(MainActivity.EXTRA_MISSION_ID, "runtime-mission")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        ActivityScenario.launch<MainActivity>(intent).use {
            composeRule.onNodeWithText("Mission Control").assertIsDisplayed()
        }
    }

    @Test fun approvingDeliverableFromMissionScreenCompletesMission() {
        grantNotificationPermission()
        val mission = EmpireMissionControl.createMission("בדיקת תוצר מלאה", "SHOSHI")
        EmpireMissionControl.transitionStatus(mission.id, MissionStatus.RUNNING, "runtime execution")
        EmpireMissionControl.transitionStatus(mission.id, MissionStatus.REVIEW, "runtime owner review")
        HumanApprovalGateway.requestApproval(
            "אישור התוצר למשימה: ${mission.title}",
            "SHOSHI",
            ApprovalRiskLevel.MEDIUM,
            "runtime approval",
            mission.id
        )
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_TAB, 2)
            .putExtra(MainActivity.EXTRA_MISSION_ID, mission.id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        ActivityScenario.launch<MainActivity>(intent).use {
            composeRule.onNodeWithText("אישור התוצר").performClick()
            composeRule.onNodeWithText("SHOSHI • COMPLETED • Evidence required").assertIsDisplayed()
        }
    }

    private fun grantNotificationPermission() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            instrumentation.uiAutomation.executeShellCommand(
                "pm grant ${instrumentation.targetContext.packageName} ${Manifest.permission.POST_NOTIFICATIONS}"
            ).close()
        }
    }
}
