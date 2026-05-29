package com.example.chudadi.network.room

import com.example.chudadi.network.bluetooth.transport.BroadcastResult
import com.example.chudadi.network.bluetooth.transport.HostTransportConfig
import com.example.chudadi.network.bluetooth.transport.RoomTransport
import com.example.chudadi.network.bluetooth.transport.RoomTransportEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class BluetoothClientJoinConnectorTest {
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun connectToHost_whenConnectTimeout_clearsClientConnectionAndReturnsConnectTimeout() = runTest {
        val transport = FakeRoomTransport(connectBehavior = ConnectBehavior.HANG)
        val connector = BluetoothClientJoinConnector(
            roomTransport = transport,
            roomUuid = UUID.fromString("a9b56c03-6cae-417b-a522-3b299d790e14"),
            connectTimeoutMs = CONNECT_TIMEOUT_MS,
            connectTimeoutMessage = CONNECT_TIMEOUT_MESSAGE,
            joinTimeoutMs = JOIN_TIMEOUT_MS,
            joinTimeoutMessage = JOIN_TIMEOUT_MESSAGE,
        )

        val deferred = async {
            connector.connectAndAwaitAccepted(
                device = BluetoothDiscoveredDevice(
                    name = "Host",
                    address = "00:11:22:33:44:55",
                    isBonded = true,
                ),
                playerName = "Player",
                avatarResId = null,
                resumeParticipantId = null,
            )
        }
        runCurrent()
        advanceTimeBy(CONNECT_TIMEOUT_MS)
        runCurrent()

        val result = deferred.await()

        assertTrue(result.isFailure)
        assertEquals(CONNECT_TIMEOUT_MESSAGE, result.exceptionOrNull()?.message)
        assertNotEquals(JOIN_TIMEOUT_MESSAGE, result.exceptionOrNull()?.message)
        assertTrue(transport.clearClientConnectionCalled)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun connectToHost_whenJoinAcceptedTimeout_closesConnectionAndReturnsJoinTimeout() = runTest {
        val connection = FakeRoomClientConnection(joinBehavior = JoinBehavior.Hang)
        val transport = FakeRoomTransport(connection)
        val connector = BluetoothClientJoinConnector(
            roomTransport = transport,
            roomUuid = UUID.fromString("a9b56c03-6cae-417b-a522-3b299d790e14"),
            connectTimeoutMs = CONNECT_TIMEOUT_MS,
            connectTimeoutMessage = CONNECT_TIMEOUT_MESSAGE,
            joinTimeoutMs = JOIN_TIMEOUT_MS,
            joinTimeoutMessage = JOIN_TIMEOUT_MESSAGE,
        )

        val deferred = async {
            connector.connectAndAwaitAccepted(
                device = BluetoothDiscoveredDevice(
                    name = "Host",
                    address = "00:11:22:33:44:55",
                    isBonded = true,
                ),
                playerName = "Player",
                avatarResId = null,
                resumeParticipantId = null,
            )
        }
        runCurrent()
        advanceTimeBy(JOIN_TIMEOUT_MS)
        runCurrent()

        val result = deferred.await()

        assertTrue(result.isFailure)
        assertEquals(JOIN_TIMEOUT_MESSAGE, result.exceptionOrNull()?.message)
        assertNotEquals(CONNECT_TIMEOUT_MESSAGE, result.exceptionOrNull()?.message)
        assertTrue(connection.closeNowCalled)
        assertTrue(transport.clearClientConnectionCalled)
    }

    @Test
    fun connectToHost_whenJoinRejected_clearsClientConnection() = runTest {
        val connection = FakeRoomClientConnection(joinBehavior = JoinBehavior.Reject("房间已满"))
        val transport = FakeRoomTransport(connection)
        val connector = BluetoothClientJoinConnector(
            roomTransport = transport,
            roomUuid = UUID.fromString("a9b56c03-6cae-417b-a522-3b299d790e14"),
            connectTimeoutMs = CONNECT_TIMEOUT_MS,
            connectTimeoutMessage = CONNECT_TIMEOUT_MESSAGE,
            joinTimeoutMs = JOIN_TIMEOUT_MS,
            joinTimeoutMessage = JOIN_TIMEOUT_MESSAGE,
        )

        val result = connector.connectAndAwaitAccepted(
            device = BluetoothDiscoveredDevice(
                name = "Host",
                address = "00:11:22:33:44:55",
                isBonded = true,
            ),
            playerName = "Player",
            avatarResId = null,
            resumeParticipantId = null,
        )

        assertTrue(result.isFailure)
        assertEquals("房间已满", result.exceptionOrNull()?.message)
        assertNotEquals(CONNECT_TIMEOUT_MESSAGE, result.exceptionOrNull()?.message)
        assertNotEquals(JOIN_TIMEOUT_MESSAGE, result.exceptionOrNull()?.message)
        assertTrue(connection.closeNowCalled)
        assertTrue(transport.clearClientConnectionCalled)
    }

    @Test
    fun connectToHost_whenJoinRejectedWithBlankReason_returnsDefaultRejectedMessage() = runTest {
        val connection = FakeRoomClientConnection(joinBehavior = JoinBehavior.Reject(""))
        val transport = FakeRoomTransport(connection)
        val connector = BluetoothClientJoinConnector(
            roomTransport = transport,
            roomUuid = UUID.fromString("a9b56c03-6cae-417b-a522-3b299d790e14"),
            connectTimeoutMs = CONNECT_TIMEOUT_MS,
            connectTimeoutMessage = CONNECT_TIMEOUT_MESSAGE,
            joinTimeoutMs = JOIN_TIMEOUT_MS,
            joinTimeoutMessage = JOIN_TIMEOUT_MESSAGE,
        )

        val result = connector.connectAndAwaitAccepted(
            device = BluetoothDiscoveredDevice(
                name = "Host",
                address = "00:11:22:33:44:55",
                isBonded = true,
            ),
            playerName = "Player",
            avatarResId = null,
            resumeParticipantId = null,
        )

        assertTrue(result.isFailure)
        assertEquals(JOIN_REJECTED_DEFAULT_MESSAGE, result.exceptionOrNull()?.message)
        assertNotEquals(CONNECT_TIMEOUT_MESSAGE, result.exceptionOrNull()?.message)
        assertNotEquals(JOIN_TIMEOUT_MESSAGE, result.exceptionOrNull()?.message)
        assertTrue(connection.closeNowCalled)
        assertTrue(transport.clearClientConnectionCalled)
    }

    private class FakeRoomClientConnection(
        private val joinBehavior: JoinBehavior,
    ) : RoomClientConnection {
        var closeNowCalled = false

        override suspend fun awaitJoinAccepted(
            playerName: String,
            avatarResId: Int?,
            resumeParticipantId: String?,
        ): RoomWireMessage.JoinRoomAccepted {
            return when (joinBehavior) {
                JoinBehavior.Accept -> RoomWireMessage.JoinRoomAccepted(
                    localParticipantId = "player-1",
                    snapshot = acceptedSnapshot(),
                )
                JoinBehavior.Hang -> awaitCancellation()
                is JoinBehavior.Reject -> throw UserVisibleRoomException(
                    joinBehavior.reason.ifBlank { JOIN_REJECTED_DEFAULT_MESSAGE },
                )
            }
        }

        override fun closeNow() {
            closeNowCalled = true
        }
    }

    private class FakeRoomTransport(
        private val connection: RoomClientConnection? = null,
        private val connectBehavior: ConnectBehavior = ConnectBehavior.SUCCEED,
    ) : RoomTransport {
        var clearClientConnectionCalled = false

        override val events: Flow<RoomTransportEvent> = emptyFlow()

        override suspend fun startHost(config: HostTransportConfig): Result<Unit> = Result.success(Unit)

        override fun launchHostHeartbeat(
            heartbeatIntervalMs: Long,
            heartbeatTimeoutMs: Long,
        ) = Unit

        override suspend fun connectToHost(
            device: BluetoothDiscoveredDevice,
            roomUuid: UUID,
        ): Result<RoomClientConnection> {
            return when (connectBehavior) {
                ConnectBehavior.SUCCEED -> Result.success(requireNotNull(connection))
                ConnectBehavior.HANG -> awaitCancellation()
            }
        }

        override fun attachHostReadLoop(
            participantId: String,
            connection: RoomSocketConnection,
        ) = Unit

        override fun attachClientReadLoop(connection: RoomClientConnection) = Unit

        override fun startClientHeartbeatWatchdog(
            heartbeatTimeoutMs: Long,
            clientHeartbeatCheckIntervalMs: Long,
        ) = Unit

        override fun replaceHostConnection(
            participantId: String,
            connection: RoomSocketConnection,
        ) = Unit

        override fun sendToHost(message: RoomWireMessage): Result<Unit> = Result.success(Unit)

        override fun sendToParticipant(
            participantId: String,
            message: RoomWireMessage,
        ): Result<Unit> = Result.success(Unit)

        override fun broadcast(message: RoomWireMessage): Result<BroadcastResult> = Result.success(BroadcastResult())

        override fun disconnectParticipant(participantId: String) = Unit

        override fun shutdown() = Unit

        override fun closeNow() = Unit

        override fun clearClientConnection() {
            clearClientConnectionCalled = true
        }
    }

    private enum class ConnectBehavior {
        SUCCEED,
        HANG,
    }

    private sealed interface JoinBehavior {
        data object Accept : JoinBehavior

        data object Hang : JoinBehavior

        data class Reject(val reason: String) : JoinBehavior
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 15_000L
        const val CONNECT_TIMEOUT_MESSAGE = "连接房主超时，请确认双方蓝牙已开启并靠近后重试"
        const val JOIN_TIMEOUT_MS = 8_000L
        const val JOIN_TIMEOUT_MESSAGE = "入房响应超时，请让房主重新开启房间后重试"
        const val JOIN_REJECTED_DEFAULT_MESSAGE = "房主拒绝了入房请求"

        fun acceptedSnapshot(): RemoteRoomSnapshot {
            return RemoteRoomSnapshot(
                roomName = "Room",
                hostDeviceName = "Host",
                currentRule = "SOUTHERN",
                slots = emptyList(),
            )
        }
    }
}
