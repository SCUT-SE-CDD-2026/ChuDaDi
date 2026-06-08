package com.example.chudadi.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlayerPreferencesRepositoryTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val repository = PlayerPreferencesRepository(context)

    @After
    fun tearDown() = runBlocking {
        repository.updateNightMode(false)
    }

    @Test
    fun updateNightMode_whenEnabled_persistsTrue() = runBlocking {
        repository.updateNightMode(false)

        repository.updateNightMode(true)

        assertTrue(repository.nightMode.first())
    }

    @Test
    fun updateNightMode_whenDisabled_persistsFalse() = runBlocking {
        repository.updateNightMode(true)

        repository.updateNightMode(false)

        assertFalse(repository.nightMode.first())
    }
}
