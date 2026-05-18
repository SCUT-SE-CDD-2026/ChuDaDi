package com.example.chudadi.network.room

import android.bluetooth.BluetoothSocket
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class RoomSocketManagerTest {
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun startServer_whenActorDoesNotRespond_returnsTimeoutFailure() = runTest {
        val managerScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val manager = RoomSocketManager(
            bluetoothAdapter = null,
            frameCodec = RoomFrameCodec(),
            scope = managerScope,
        )
        managerScope.cancel()

        val deferred = async {
            manager.startServer(
                serviceName = "ChuDaDiRoom",
                roomUuid = UUID.fromString("a9b56c03-6cae-417b-a522-3b299d790e14"),
            )
        }
        runCurrent()
        assertFalse(deferred.isCompleted)

        advanceTimeBy(HOST_START_TIMEOUT_MS)
        runCurrent()

        val result = deferred.await()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IOException)
        assertEquals("开启蓝牙房间超时", result.exceptionOrNull()?.message)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun serverAccepted_whenConnectionConstructionFails_closesSocketAndKeepsActorAlive() = runTest {
        val badSocket = mock(BluetoothSocket::class.java)
        val normalSocket = mock(BluetoothSocket::class.java)
        `when`(normalSocket.inputStream).thenReturn(ByteArrayInputStream(ByteArray(0)))
        `when`(normalSocket.outputStream).thenReturn(ByteArrayOutputStream())
        val connectionFactory = FailingOnceConnectionFactory()
        val manager = RoomSocketManager(
            bluetoothAdapter = null,
            frameCodec = RoomFrameCodec(),
            scope = backgroundScope,
            connectionFactory = connectionFactory,
        )
        val events = mutableListOf<RoomSocketEvent>()
        backgroundScope.launch {
            manager.events.collect { event -> events += event }
        }
        runCurrent()

        manager.acceptSocketForTest(badSocket)
        runCurrent()

        verify(badSocket).close()
        assertTrue(events.none { it is RoomSocketEvent.IncomingConnection })

        manager.acceptSocketForTest(normalSocket)
        runCurrent()

        assertEquals(1, events.count { it is RoomSocketEvent.IncomingConnection })
    }

    private class FailingOnceConnectionFactory : RoomSocketConnectionFactory {
        private var shouldFail = true

        override fun create(
            socket: BluetoothSocket,
            frameCodec: RoomFrameCodec,
        ): RoomSocketConnection {
            if (shouldFail) {
                shouldFail = false
                throw IOException("bad socket")
            }
            return RoomSocketConnection(socket = socket, frameCodec = frameCodec)
        }
    }

    private companion object {
        const val HOST_START_TIMEOUT_MS = 8_000L
    }
}
