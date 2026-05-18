package com.example.chudadi.network.room

import com.example.chudadi.data.repository.ReconnectSession
import com.example.chudadi.network.bluetooth.platform.BluetoothPermissionChecker
import com.example.chudadi.network.bluetooth.transport.HostTransportConfig
import com.example.chudadi.network.bluetooth.transport.RoomTransport
import com.example.chudadi.network.bluetooth.transport.RoomTransportEvent
import com.example.chudadi.ui.room.BluetoothSearchState
import com.example.chudadi.ui.room.RoomMode
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class BluetoothRoomRepositoryTest {
    @Test
    fun connectToHost_whenPersistReconnectSessionFails_closesConnectionAndClearsClient() = runTest {
        val connection = FakeRoomClientConnection()
        val transport = FakeRoomTransport(connection = connection)
        val discoveryService = FakeDiscoveryService()
        val persistError = IOException("persist failed")
        val repository = createRepository(
            roomTransport = transport,
            discoveryService = discoveryService,
            permissionChecker = bluetoothPermissionChecker(
                supported = true,
                connectPermission = true,
            ),
            persistReconnectSessionAction = { throw persistError },
        )
        val device = BluetoothDiscoveredDevice(
            name = "Host",
            address = "00:11:22:33:44:55",
            isBonded = true,
        )

        val result = repository.connectToHost(
            device = device,
            playerName = "Player",
            avatarResId = null,
        )

        assertTrue(result.isFailure)
        assertEquals("persist failed", result.exceptionOrNull()?.message)
        assertTrue(connection.closeNowCalled)
        assertTrue(transport.clearClientConnectionCalled)
        assertEquals("Idle", repository.roomRoleForTest())
        val uiState = repository.roomUiState.value
        assertEquals(BluetoothSearchState.FAILED, uiState.searchState)
        assertEquals(RoomMode.LOCAL, uiState.roomMode)
        assertFalse(uiState.isHost)
        assertTrue(uiState.slots.none { it.isLocalPlayer })
        assertTrue(uiState.slots.none { it.participantId == "player-1" })
        assertEquals(uiState.connectionHint, uiState.joinErrorMessage)
    }

    @Test
    fun connectToHost_whenAttachClientReadLoopFails_closesConnectionAndClearsClient() = runTest {
        val connection = FakeRoomClientConnection()
        val attachError = IOException("attach failed")
        val transport = FakeRoomTransport(
            connection = connection,
            attachClientReadLoopAction = { throw attachError },
        )
        val discoveryService = FakeDiscoveryService()
        val repository = createRepository(
            roomTransport = transport,
            discoveryService = discoveryService,
            permissionChecker = bluetoothPermissionChecker(
                supported = true,
                connectPermission = true,
            ),
            persistReconnectSessionAction = {},
        )
        val device = BluetoothDiscoveredDevice(
            name = "Host",
            address = "00:11:22:33:44:55",
            isBonded = true,
        )

        val result = repository.connectToHost(
            device = device,
            playerName = "Player",
            avatarResId = null,
        )

        assertTrue(result.isFailure)
        assertEquals("attach failed", result.exceptionOrNull()?.message)
        assertTrue(connection.closeNowCalled)
        assertTrue(transport.clearClientConnectionCalled)
        assertEquals("Idle", repository.roomRoleForTest())
        val uiState = repository.roomUiState.value
        assertEquals(BluetoothSearchState.FAILED, uiState.searchState)
        assertEquals(RoomMode.LOCAL, uiState.roomMode)
        assertFalse(uiState.isHost)
        assertTrue(uiState.slots.none { it.isLocalPlayer })
        assertTrue(uiState.slots.none { it.participantId == "player-1" })
        assertEquals(uiState.connectionHint, uiState.joinErrorMessage)
    }

    @Test
    fun connectToHost_whenStartClientHeartbeatWatchdogFails_closesConnectionAndClearsClient() = runTest {
        val connection = FakeRoomClientConnection()
        val watchdogError = IOException("watchdog failed")
        val transport = FakeRoomTransport(
            connection = connection,
            startClientHeartbeatWatchdogAction = { throw watchdogError },
        )
        val discoveryService = FakeDiscoveryService()
        val repository = createRepository(
            roomTransport = transport,
            discoveryService = discoveryService,
            permissionChecker = bluetoothPermissionChecker(
                supported = true,
                connectPermission = true,
            ),
            persistReconnectSessionAction = {},
        )
        val device = BluetoothDiscoveredDevice(
            name = "Host",
            address = "00:11:22:33:44:55",
            isBonded = true,
        )

        val result = repository.connectToHost(
            device = device,
            playerName = "Player",
            avatarResId = null,
        )

        assertTrue(result.isFailure)
        assertEquals("watchdog failed", result.exceptionOrNull()?.message)
        assertTrue(connection.closeNowCalled)
        assertTrue(transport.clearClientConnectionCalled)
        assertEquals("Idle", repository.roomRoleForTest())
        val uiState = repository.roomUiState.value
        assertEquals(BluetoothSearchState.FAILED, uiState.searchState)
        assertEquals(RoomMode.LOCAL, uiState.roomMode)
        assertFalse(uiState.isHost)
        assertTrue(uiState.slots.none { it.isLocalPlayer })
        assertTrue(uiState.slots.none { it.participantId == "player-1" })
        assertEquals(uiState.connectionHint, uiState.joinErrorMessage)
    }

    @Test
    fun clear_closesTransportSynchronouslyWithoutWaitingForActorShutdown() = runTest {
        val transport = FakeRoomTransport()
        val discoveryService = FakeDiscoveryService()
        val repository = createRepository(
            roomTransport = transport,
            discoveryService = discoveryService,
        )

        repository.clear()

        assertTrue(transport.closeNowCalled)
        assertTrue(discoveryService.clearDiscoveredDevicesCalled)
    }

    @Test
    fun clear_whenCalledTwice_isIdempotent() = runTest {
        val transport = FakeRoomTransport()
        val discoveryService = FakeDiscoveryService()
        val repository = createRepository(
            roomTransport = transport,
            discoveryService = discoveryService,
        )

        repository.clear()
        repository.clear()

        assertEquals(2, transport.closeNowCallCount)
        assertEquals(2, discoveryService.clearDiscoveredDevicesCallCount)
    }

    private fun createRepository(
        roomTransport: RoomTransport,
        discoveryService: BluetoothDiscoveryServicePort,
        permissionChecker: BluetoothPermissionChecker? = null,
        persistReconnectSessionAction: (suspend (ReconnectSession) -> Unit)? = null,
    ): BluetoothRoomRepository {
        return BluetoothRoomRepository(
            roomTransport = roomTransport,
            discoveryService = discoveryService,
            scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher()),
            permissionChecker = permissionChecker,
            persistReconnectSessionAction = persistReconnectSessionAction,
        )
    }

    private fun bluetoothPermissionChecker(
        supported: Boolean,
        connectPermission: Boolean,
    ): BluetoothPermissionChecker {
        val permissionChecker = mock(BluetoothPermissionChecker::class.java)
        `when`(permissionChecker.isBluetoothSupported()).thenReturn(supported)
        `when`(permissionChecker.hasConnectPermission()).thenReturn(connectPermission)
        return permissionChecker
    }

    private fun BluetoothRoomRepository.roomRoleForTest(): String {
        val field = BluetoothRoomRepository::class.java.getDeclaredField("roomRole")
        field.isAccessible = true
        return field.get(this).toString()
    }

    private class FakeDiscoveryService : BluetoothDiscoveryServicePort {
        var clearDiscoveredDevicesCalled = false
        var clearDiscoveredDevicesCallCount = 0

        override val devices = MutableStateFlow<List<BluetoothDiscoveredDevice>>(emptyList())
        override val events = MutableSharedFlow<BluetoothDiscoveryEvent>()

        override fun loadBondedDevices(): List<BluetoothDiscoveredDevice> = emptyList()

        override fun getBondedDevices(): List<BluetoothDiscoveredDevice> = emptyList()

        override fun startDiscovery(): Boolean = true

        override fun stopDiscovery() = Unit

        override fun clearDiscoveredDevices() {
            clearDiscoveredDevicesCalled = true
            clearDiscoveredDevicesCallCount++
        }
    }

    private class FakeRoomClientConnection : RoomClientConnection {
        var closeNowCalled = false

        override suspend fun awaitJoinAccepted(
            playerName: String,
            avatarResId: Int?,
            resumeParticipantId: String?,
        ): RoomWireMessage.JoinRoomAccepted {
            return RoomWireMessage.JoinRoomAccepted(
                localParticipantId = "player-1",
                snapshot = RemoteRoomSnapshot(
                    roomName = "Host Room",
                    hostDeviceName = "Host",
                    currentRule = "SOUTHERN",
                    slots = emptyList(),
                ),
            )
        }

        override fun closeNow() {
            closeNowCalled = true
        }
    }

    private class FakeRoomTransport(
        private val connection: RoomClientConnection? = null,
        private val attachClientReadLoopAction: (RoomClientConnection) -> Unit = {},
        private val startClientHeartbeatWatchdogAction: () -> Unit = {},
    ) : RoomTransport {
        var closeNowCalled = false
        var closeNowCallCount = 0
        var clearClientConnectionCalled = false

        override val events: Flow<RoomTransportEvent> = MutableSharedFlow()

        override suspend fun startHost(config: HostTransportConfig): Result<Unit> = Result.success(Unit)

        override fun launchHostHeartbeat(
            heartbeatIntervalMs: Long,
            heartbeatTimeoutMs: Long,
        ) = Unit

        override suspend fun connectToHost(
            device: BluetoothDiscoveredDevice,
            roomUuid: UUID,
        ): Result<RoomClientConnection> = Result.success(requireNotNull(connection))

        override fun attachHostReadLoop(
            participantId: String,
            connection: RoomSocketConnection,
        ) = Unit

        override fun attachClientReadLoop(connection: RoomClientConnection) {
            attachClientReadLoopAction(connection)
        }

        override fun startClientHeartbeatWatchdog(
            heartbeatTimeoutMs: Long,
            clientHeartbeatCheckIntervalMs: Long,
        ) {
            startClientHeartbeatWatchdogAction()
        }

        override fun replaceHostConnection(
            participantId: String,
            connection: RoomSocketConnection,
        ) = Unit

        override fun sendToHost(message: RoomWireMessage) = Unit

        override fun sendToParticipant(
            participantId: String,
            message: RoomWireMessage,
        ) = Unit

        override fun broadcast(message: RoomWireMessage) = Unit

        override fun disconnectParticipant(participantId: String) = Unit

        override fun shutdown() = Unit

        override fun closeNow() {
            closeNowCalled = true
            closeNowCallCount++
        }

        override fun clearClientConnection() {
            clearClientConnectionCalled = true
        }
    }
}
