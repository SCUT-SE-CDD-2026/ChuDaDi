package com.example.chudadi.network.room

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import com.example.chudadi.network.bluetooth.transport.BluetoothConnection
import com.example.chudadi.network.bluetooth.transport.BroadcastSendException
import com.example.chudadi.network.bluetooth.transport.ClassicBluetoothTransport
import com.example.chudadi.network.bluetooth.transport.ClientConnectionHolder
import com.example.chudadi.network.bluetooth.transport.HeartbeatMonitor
import com.example.chudadi.network.bluetooth.transport.HostConnectionRegistry
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.doAnswer
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
    fun startServerTimeout_doesNotCreateStaleServerSocketWhenActorRunsLater() = runTest {
        val managerScheduler = TestCoroutineScheduler()
        val managerScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(managerScheduler))
        val serverSocketFactory = CountingServerSocketFactory { blockingServerSocket() }
        val manager = RoomSocketManager(
            bluetoothAdapter = mock(BluetoothAdapter::class.java),
            frameCodec = RoomFrameCodec(),
            scope = managerScope,
            serverSocketFactory = serverSocketFactory,
        )

        val deferred = async {
            manager.startServer(
                serviceName = "ChuDaDiRoom",
                roomUuid = ROOM_UUID,
            )
        }
        runCurrent()

        advanceTimeBy(HOST_START_TIMEOUT_MS)
        runCurrent()
        val result = deferred.await()
        managerScheduler.advanceUntilIdle()
        managerScope.cancel()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IOException)
        assertEquals(0, serverSocketFactory.createCount)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun startServerSecurityExceptionCompletesFailure() = runTest {
        val error = SecurityException("missing bluetooth permission")
        val serverSocketFactory = CountingServerSocketFactory { throw error }
        val manager = RoomSocketManager(
            bluetoothAdapter = mock(BluetoothAdapter::class.java),
            frameCodec = RoomFrameCodec(),
            scope = backgroundScope,
            serverSocketFactory = serverSocketFactory,
        )

        val deferred = async {
            manager.startServer(
                serviceName = "ChuDaDiRoom",
                roomUuid = ROOM_UUID,
            )
        }
        runCurrent()
        val result = deferred.await()

        assertTrue(result.isFailure)
        assertSame(error, result.exceptionOrNull())
        assertEquals(1, serverSocketFactory.createCount)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun startServerIllegalStateExceptionCompletesFailure() = runTest {
        val error = IllegalStateException("adapter disabled")
        val serverSocketFactory = CountingServerSocketFactory { throw error }
        val manager = RoomSocketManager(
            bluetoothAdapter = mock(BluetoothAdapter::class.java),
            frameCodec = RoomFrameCodec(),
            scope = backgroundScope,
            serverSocketFactory = serverSocketFactory,
        )

        val deferred = async {
            manager.startServer(
                serviceName = "ChuDaDiRoom",
                roomUuid = ROOM_UUID,
            )
        }
        runCurrent()
        val result = deferred.await()

        assertTrue(result.isFailure)
        assertSame(error, result.exceptionOrNull())
        assertEquals(1, serverSocketFactory.createCount)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun startServerIOExceptionStillCompletesFailureAndCleansUp() = runTest {
        val error = IOException("listen failed")
        val serverSocketFactory = CountingServerSocketFactory { throw error }
        val manager = RoomSocketManager(
            bluetoothAdapter = mock(BluetoothAdapter::class.java),
            frameCodec = RoomFrameCodec(),
            scope = backgroundScope,
            serverSocketFactory = serverSocketFactory,
        )

        val deferred = async {
            manager.startServer(
                serviceName = "ChuDaDiRoom",
                roomUuid = ROOM_UUID,
            )
        }
        runCurrent()
        val result = deferred.await()

        assertTrue(result.isFailure)
        assertSame(error, result.exceptionOrNull())
        assertEquals(1, serverSocketFactory.createCount)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun startServerReturnsFailureWhenAcceptImmediatelyThrowsSecurityException() = runTest {
        val error = SecurityException("accept permission denied")
        val serverSocket = throwingAcceptServerSocket(error)
        val serverSocketFactory = CountingServerSocketFactory { serverSocket }
        val manager = RoomSocketManager(
            bluetoothAdapter = mock(BluetoothAdapter::class.java),
            frameCodec = RoomFrameCodec(),
            scope = backgroundScope,
            serverSocketFactory = serverSocketFactory,
        )

        val deferred = async { manager.startServer(serviceName = "ChuDaDiRoom", roomUuid = ROOM_UUID) }
        runCurrent()
        waitForAcceptLoopStartup()
        val result = deferred.await()

        assertTrue(result.isFailure)
        assertSame(error, result.exceptionOrNull())
        assertEquals(1, serverSocketFactory.createCount)
        verify(serverSocket).close()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun startServerReturnsFailureWhenAcceptImmediatelyThrowsIllegalStateException() = runTest {
        val error = IllegalStateException("accept unavailable")
        val serverSocket = throwingAcceptServerSocket(error)
        val serverSocketFactory = CountingServerSocketFactory { serverSocket }
        val manager = RoomSocketManager(
            bluetoothAdapter = mock(BluetoothAdapter::class.java),
            frameCodec = RoomFrameCodec(),
            scope = backgroundScope,
            serverSocketFactory = serverSocketFactory,
        )

        val deferred = async { manager.startServer(serviceName = "ChuDaDiRoom", roomUuid = ROOM_UUID) }
        runCurrent()
        waitForAcceptLoopStartup()
        val result = deferred.await()

        assertTrue(result.isFailure)
        assertSame(error, result.exceptionOrNull())
        assertEquals(1, serverSocketFactory.createCount)
        verify(serverSocket).close()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun startServerReturnsFailureWhenAcceptImmediatelyThrowsIOException() = runTest {
        val error = IOException("accept failed")
        val serverSocket = throwingAcceptServerSocket(error)
        val serverSocketFactory = CountingServerSocketFactory { serverSocket }
        val manager = RoomSocketManager(
            bluetoothAdapter = mock(BluetoothAdapter::class.java),
            frameCodec = RoomFrameCodec(),
            scope = backgroundScope,
            serverSocketFactory = serverSocketFactory,
        )

        val deferred = async { manager.startServer(serviceName = "ChuDaDiRoom", roomUuid = ROOM_UUID) }
        runCurrent()
        waitForAcceptLoopStartup()
        val result = deferred.await()

        assertTrue(result.isFailure)
        assertSame(error, result.exceptionOrNull())
        assertEquals(1, serverSocketFactory.createCount)
        verify(serverSocket).close()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun repeatedStartServerDoesNotCreateMultipleSockets() = runTest {
        val serverSocketFactory = CountingServerSocketFactory { blockingServerSocket() }
        val manager = RoomSocketManager(
            bluetoothAdapter = mock(BluetoothAdapter::class.java),
            frameCodec = RoomFrameCodec(),
            scope = backgroundScope,
            serverSocketFactory = serverSocketFactory,
        )

        val first = async { manager.startServer(serviceName = "ChuDaDiRoom", roomUuid = ROOM_UUID) }
        runCurrent()
        val second = async { manager.startServer(serviceName = "ChuDaDiRoom", roomUuid = ROOM_UUID) }
        runCurrent()
        waitForAcceptLoopStartup()

        assertTrue(first.await().isSuccess)
        assertTrue(second.await().isSuccess)
        assertEquals(1, serverSocketFactory.createCount)

        manager.closeNow()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun startServerFailureAllowsRetry() = runTest {
        var shouldFail = true
        val serverSocketFactory = CountingServerSocketFactory {
            if (shouldFail) {
                shouldFail = false
                throw SecurityException("first listen failed")
            }
            blockingServerSocket()
        }
        val manager = RoomSocketManager(
            bluetoothAdapter = mock(BluetoothAdapter::class.java),
            frameCodec = RoomFrameCodec(),
            scope = backgroundScope,
            serverSocketFactory = serverSocketFactory,
        )

        val failed = async { manager.startServer(serviceName = "ChuDaDiRoom", roomUuid = ROOM_UUID) }
        runCurrent()
        val succeeded = async { manager.startServer(serviceName = "ChuDaDiRoom", roomUuid = ROOM_UUID) }
        runCurrent()
        waitForAcceptLoopStartup()

        assertTrue(failed.await().isFailure)
        assertTrue(succeeded.await().isSuccess)
        assertEquals(2, serverSocketFactory.createCount)

        manager.closeNow()
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

    @Test
    fun sendSafely_whenWriteThrows_returnsFailureAndClosesSocket() {
        val socket = mock(BluetoothSocket::class.java)
        val output = ThrowingOutputStream(IOException("write failed"))
        `when`(socket.inputStream).thenReturn(ByteArrayInputStream(ByteArray(0)))
        `when`(socket.outputStream).thenReturn(output)
        val connection = RoomSocketConnection(socket = socket, frameCodec = RoomFrameCodec())

        val result = connection.sendSafely(
            message = RoomWireMessage.HeartbeatPing,
            targetId = "host",
        )

        assertTrue(result.isFailure)
        assertEquals("write failed", result.exceptionOrNull()?.message)
        assertTrue(output.closeCalled)
        verify(socket).close()
    }

    @Test
    fun sendToHost_whenWriteThrows_returnsFailure() = runTest {
        val clientConnectionHolder = ClientConnectionHolder()
        val transport = createTransport(
            scope = backgroundScope,
            clientConnectionHolder = clientConnectionHolder,
        )
        val connection = failingBluetoothConnection("write host failed")
        clientConnectionHolder.set(connection)

        val result = transport.sendToHost(RoomWireMessage.ReadyStateChangeMessage(true))

        assertTrue(result.isFailure)
        assertEquals("write host failed", result.exceptionOrNull()?.message)
        assertEquals(null, clientConnectionHolder.current())
    }

    @Test
    fun sendToParticipant_whenWriteThrows_returnsFailure() = runTest {
        val hostConnectionRegistry = HostConnectionRegistry()
        val transport = createTransport(
            scope = backgroundScope,
            hostConnectionRegistry = hostConnectionRegistry,
        )
        hostConnectionRegistry.add("participant-1", failingBluetoothConnection("write participant failed"))

        val result = transport.sendToParticipant(
            participantId = "participant-1",
            message = RoomWireMessage.SwapSeatRequestMessage(targetSlotIndex = 1),
        )

        assertTrue(result.isFailure)
        assertEquals("write participant failed", result.exceptionOrNull()?.message)
    }

    @Test
    fun broadcast_whenOneTargetWriteThrows_reportsFailedTarget() = runTest {
        val hostConnectionRegistry = HostConnectionRegistry()
        val transport = createTransport(
            scope = backgroundScope,
            hostConnectionRegistry = hostConnectionRegistry,
        )
        hostConnectionRegistry.add("participant-ok", bluetoothConnection(ByteArrayOutputStream()))
        hostConnectionRegistry.add("participant-failed", failingBluetoothConnection("broadcast failed"))

        val result = transport.broadcast(RoomWireMessage.RoomSnapshotMessage(emptySnapshot()))

        assertTrue(result.isFailure)
        val error = result.exceptionOrNull() as BroadcastSendException
        assertEquals(listOf("participant-failed"), error.failedTargets)
    }

    private fun createTransport(
        scope: CoroutineScope,
        hostConnectionRegistry: HostConnectionRegistry = HostConnectionRegistry(),
        clientConnectionHolder: ClientConnectionHolder = ClientConnectionHolder(),
    ): ClassicBluetoothTransport {
        val heartbeatMonitor = HeartbeatMonitor(scope)
        val socketManager = RoomSocketManager(
            bluetoothAdapter = null,
            frameCodec = RoomFrameCodec(),
            scope = scope,
            hostConnectionRegistry = hostConnectionRegistry,
            clientConnectionHolder = clientConnectionHolder,
            heartbeatMonitor = heartbeatMonitor,
        )
        return ClassicBluetoothTransport(
            socketManager = socketManager,
            hostConnectionRegistry = hostConnectionRegistry,
            clientConnectionHolder = clientConnectionHolder,
            heartbeatMonitor = heartbeatMonitor,
            scope = scope,
        )
    }

    private fun failingBluetoothConnection(message: String): BluetoothConnection {
        return bluetoothConnection(ThrowingOutputStream(IOException(message)))
    }

    private fun bluetoothConnection(output: OutputStream): BluetoothConnection {
        val socket = mock(BluetoothSocket::class.java)
        `when`(socket.inputStream).thenReturn(ByteArrayInputStream(ByteArray(0)))
        `when`(socket.outputStream).thenReturn(output)
        return BluetoothConnection(RoomSocketConnection(socket = socket, frameCodec = RoomFrameCodec()))
    }

    private fun emptySnapshot(): RemoteRoomSnapshot {
        return RemoteRoomSnapshot(
            roomName = "Room",
            hostDeviceName = "Host",
            currentRule = "SOUTHERN",
            slots = emptyList(),
        )
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

    private class ThrowingOutputStream(
        private val error: IOException,
    ) : OutputStream() {
        var closeCalled = false

        override fun write(b: Int) {
            throw error
        }

        override fun close() {
            closeCalled = true
        }
    }

    private class CountingServerSocketFactory(
        private val createAction: () -> BluetoothServerSocket,
    ) : RoomServerSocketFactory {
        var createCount = 0

        override fun create(
            adapter: BluetoothAdapter,
            serviceName: String,
            roomUuid: UUID,
        ): BluetoothServerSocket {
            createCount++
            return createAction()
        }
    }

    private fun blockingServerSocket(): BluetoothServerSocket {
        val serverSocket = mock(BluetoothServerSocket::class.java)
        val closed = AtomicBoolean(false)
        `when`(serverSocket.accept()).thenAnswer {
            while (!closed.get()) {
                Thread.sleep(SOCKET_CLOSE_POLL_MS)
            }
            throw IOException("server socket closed")
        }
        doAnswer {
            closed.set(true)
            null
        }.`when`(serverSocket).close()
        return serverSocket
    }

    private fun throwingAcceptServerSocket(error: Throwable): BluetoothServerSocket {
        val serverSocket = mock(BluetoothServerSocket::class.java)
        `when`(serverSocket.accept()).thenAnswer { throw error }
        return serverSocket
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun TestScope.waitForAcceptLoopStartup() {
        Thread.sleep(ACCEPT_LOOP_TEST_WAIT_MS)
        testScheduler.runCurrent()
    }

    private companion object {
        const val HOST_START_TIMEOUT_MS = 8_000L
        const val ACCEPT_LOOP_START_GRACE_MS = 50L
        const val ACCEPT_LOOP_TEST_WAIT_MS = 150L
        const val SOCKET_CLOSE_POLL_MS = 10L
        val ROOM_UUID: UUID = UUID.fromString("a9b56c03-6cae-417b-a522-3b299d790e14")
    }
}
