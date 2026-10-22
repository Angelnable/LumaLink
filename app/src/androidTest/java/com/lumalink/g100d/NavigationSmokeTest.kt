package com.lumalink.g100d

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationSmokeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun bottomNavigationShowsCoreDestinations() {
        composeRule.onNodeWithText("连接").assertExists()
        composeRule.onNodeWithText("传输").assertExists()
        composeRule.onNodeWithText("相册").assertExists()
    }
}
