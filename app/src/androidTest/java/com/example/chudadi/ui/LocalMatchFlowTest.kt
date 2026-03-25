package com.example.chudadi.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.chudadi.controller.game.LocalMatchViewModel
import com.example.chudadi.navigation.ChuDaDiNavGraph
import org.junit.Rule
import org.junit.Test

class LocalMatchFlowTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun startLocalMatch_navigatesFromHomeToGame() {
        composeRule.setContent {
            ChuDaDiNavGraph(viewModel = LocalMatchViewModel())
        }

        composeRule.onNodeWithTag(ComposeTestTags.HOME_SCREEN).assertIsDisplayed()
        composeRule.onNodeWithTag(ComposeTestTags.START_MATCH_BUTTON).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(ComposeTestTags.GAME_SCREEN).assertIsDisplayed()
    }
}
