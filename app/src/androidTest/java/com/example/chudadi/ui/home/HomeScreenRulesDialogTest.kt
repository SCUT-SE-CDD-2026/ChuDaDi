package com.example.chudadi.ui.home

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.example.chudadi.ui.ComposeTestTags
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class HomeScreenRulesDialogTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rulesButtonOpensScrollableRulesDialog() {
        var callbackCount = 0

        composeRule.setContent {
            HomeScreen(onRules = { callbackCount += 1 })
        }

        composeRule.onNodeWithTag(ComposeTestTags.RULES_BUTTON).performClick()

        composeRule.runOnIdle {
            assertEquals(1, callbackCount)
        }
        composeRule.onNodeWithTag(ComposeTestTags.RULES_DIALOG).assertIsDisplayed()
        composeRule.onNodeWithTag(ComposeTestTags.RULES_DIALOG_CONTENT).assert(hasScrollAction())
        composeRule.onNodeWithText("一、游戏基本信息").assertIsDisplayed()
        composeRule.onNodeWithTag(ComposeTestTags.RULES_DIALOG_CONTENT)
            .performScrollToNode(hasText("北方计分", substring = true))
        composeRule.onNodeWithText("北方计分", substring = true).assertIsDisplayed()

        composeRule.onNodeWithText("关闭").performClick()

        composeRule.waitUntil {
            composeRule.onAllNodesWithTag(ComposeTestTags.RULES_DIALOG).fetchSemanticsNodes().isEmpty()
        }
    }
}
