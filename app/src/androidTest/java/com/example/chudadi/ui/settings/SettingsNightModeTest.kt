package com.example.chudadi.ui.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.chudadi.data.repository.PlayerPreferencesRepository
import com.example.chudadi.ui.ComposeTestTags
import com.example.chudadi.ui.theme.ChuDaDiTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsNightModeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val repository = PlayerPreferencesRepository(context)

    @After
    fun tearDown() = runBlocking {
        repository.updateNightMode(false)
    }

    @Test
    fun nightModeSwitchUpdatesPersistedPreference() {
        runBlocking {
            repository.updateNightMode(false)
            assertFalse(repository.nightMode.first())
        }

        composeRule.setContent {
            ChuDaDiTheme(nightMode = false) {
                SettingsScreen(
                    viewModel = SettingsViewModel(repository),
                    onNavigateBack = {},
                )
            }
        }

        composeRule.onNodeWithTag(ComposeTestTags.NIGHT_MODE_SWITCH)
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        composeRule.waitUntil {
            runBlocking { repository.nightMode.first() }
        }
        assertTrue(runBlocking { repository.nightMode.first() })
    }
}
