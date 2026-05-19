package com.example.chudadi.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReconnectSessionRepositoryTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val repository = ReconnectSessionRepository(context)

    @After
    fun tearDown() = runBlocking {
        repository.clearSession()
    }

    @Test
    fun updateSession_persistsAndCanBeReadBackImmediately() = runBlocking {
        repository.clearSession()
        val session = ReconnectSession(
            hostAddress = "00:11:22:33:44:55",
            hostDeviceName = "HostDevice",
            participantId = "member-1",
            roomName = "测试房间",
            savedAtMillis = 1_234_567_890L,
        )

        repository.updateSession(session)

        assertEquals(session, repository.session.first())
    }

    @Test
    fun clearSession_removesPersistedSession() = runBlocking {
        repository.updateSession(
            ReconnectSession(
                hostAddress = "AA:BB:CC:DD:EE:FF",
                hostDeviceName = "HostDevice",
                participantId = "member-2",
                roomName = "测试房间",
                savedAtMillis = 9_876_543_210L,
            ),
        )

        repository.clearSession()

        assertNull(repository.session.first())
    }
}
