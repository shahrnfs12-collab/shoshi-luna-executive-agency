package com.shoshiluna.executive

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PrimaryNavigationRuntimeTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun everyPrimaryDestinationOpensAndRendersItsHeader() {
        compose.onNodeWithText("מרכז").performClick()
        compose.onNodeWithText("מרכז השליטה").assertIsDisplayed()

        compose.onNodeWithText("צ׳אט").performClick()
        compose.onNodeWithText("SHOSHI • LUNA — AI Chat").assertIsDisplayed()

        compose.onNodeWithText("משימות").performClick()
        compose.onNodeWithText("מרכז המשימות").assertIsDisplayed()

        compose.onNodeWithText("מועצה").performClick()
        compose.onNodeWithText("מועצת שושי ולונה").assertIsDisplayed()

        compose.onNodeWithText("עסקים").performClick()
        compose.onNodeWithText("המנוע העסקי").assertIsDisplayed()

        compose.onNodeWithText("עוד").performClick()
        compose.onNodeWithText("מערכות הסוכנות").assertIsDisplayed()
    }
}
