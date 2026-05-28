package com.example.chudadi.ui.room

import com.example.chudadi.data.repository.PlayerPreferencesRepository
import com.example.chudadi.data.repository.ReconnectSession
import com.example.chudadi.data.repository.ReconnectSessionRepository
import com.example.chudadi.network.bluetooth.platform.BluetoothPermissionChecker
import com.example.chudadi.network.bluetooth.transport.BroadcastResult
import com.example.chudadi.network.bluetooth.transport.HostTransportConfig
import com.example.chudadi.network.bluetooth.transport.RoomTransport
import com.example.chudadi.network.bluetooth.transport.RoomTransportEvent
import com.example.chudadi.network.room.BluetoothDiscoveredDevice
import com.example.chudadi.network.room.BluetoothDiscoveryEvent
import com.example.chudadi.network.room.BluetoothDiscoveryServicePort
import com.example.chudadi.network.room.BluetoothRoomRepository
import com.example.chudadi.network.room.RemoteRoomSnapshot
import com.example.chudadi.network.room.RemoteSlotSnapshot
import com.example.chudadi.network.room.RoomClientConnection
import com.example.chudadi.network.room.RoomSocketConnection
import com.example.chudadi.network.room.RoomWireMessage
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

@OptIn(ExperimentalCoroutinesApi::class)
class RoomViewModelConnectionTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun connectToBluetoothDevice_whenConnectionInProgress_ignoresSecondClick() = runTest {
        val fixture = createFixture()
        val viewModel = fixture.viewModel
        collectUiState(viewModel)

        viewModel.dispatch(RoomAction.ConnectToBluetoothDevice(DEVICE_A.address))
        viewModel.dispatch(RoomAction.ConnectToBluetoothDevice(DEVICE_B.address))
        runCurrent()

        assertEquals(1, fixture.transport.connectToHostCallCount)
        assertEquals(DEVICE_A.address, fixture.transport.connectedAddresses.single())
        viewModel.dispatch(RoomAction.CancelPendingConnection)
    }

    @Test
    fun startBluetoothDiscovery_whenConnectionInProgress_cancelsAndClearsClientConnection() = runTest {
        val fixture = createFixture()
        val viewModel = fixture.viewModel
        collectUiState(viewModel)

        viewModel.dispatch(RoomAction.ConnectToBluetoothDevice(DEVICE_A.address))
        runCurrent()
        viewModel.dispatch(RoomAction.StartBluetoothDiscovery)
        runCurrent()

        assertEquals(1, fixture.transport.clearClientConnectionCallCount)
        assertEquals(BluetoothSearchState.SCANNING, viewModel.uiState.value.searchState)
        assertNull(viewModel.uiState.value.selectedDeviceAddress)
    }

    @Test
    fun stopBluetoothDiscovery_stopsDiscoveryAndClearsScanningState() = runTest {
        val fixture = createFixture()
        val viewModel = fixture.viewModel
        collectUiState(viewModel)

        viewModel.dispatch(RoomAction.StartBluetoothDiscovery)
        runCurrent()
        viewModel.dispatch(RoomAction.StopBluetoothDiscovery)
        runCurrent()

        assertEquals(1, fixture.discoveryService.stopDiscoveryCallCount)
        assertEquals(BluetoothSearchState.IDLE, viewModel.uiState.value.searchState)
    }

    @Test
    fun cancelPendingConnection_whenConnectionInProgress_cancelsAndClearsClientConnection() = runTest {
        val fixture = createFixture()
        val viewModel = fixture.viewModel
        collectUiState(viewModel)

        viewModel.dispatch(RoomAction.ConnectToBluetoothDevice(DEVICE_A.address))
        runCurrent()
        viewModel.dispatch(RoomAction.CancelPendingConnection)
        runCurrent()

        assertEquals(1, fixture.transport.clearClientConnectionCallCount)
        assertEquals(BluetoothSearchState.IDLE, viewModel.uiState.value.searchState)
        assertNull(viewModel.uiState.value.selectedDeviceAddress)
    }

    @Test
    fun cancelPendingConnection_doesNotPublishConnectionFailure() = runTest {
        val fixture = createFixture()
        val viewModel = fixture.viewModel
        collectUiState(viewModel)

        viewModel.dispatch(RoomAction.ConnectToBluetoothDevice(DEVICE_A.address))
        runCurrent()
        viewModel.dispatch(RoomAction.CancelPendingConnection)
        runCurrent()

        assertNull(viewModel.uiState.value.joinErrorMessage)
        assertTrue(viewModel.uiState.value.connectionHint.isBlank())
    }

    @Test
    fun cancelPendingConnection_whenCalledAgainAfterCancel_doesNotClearClientConnectionAgain() = runTest {
        val fixture = createFixture()
        val viewModel = fixture.viewModel
        collectUiState(viewModel)

        viewModel.dispatch(RoomAction.ConnectToBluetoothDevice(DEVICE_A.address))
        runCurrent()
        viewModel.dispatch(RoomAction.CancelPendingConnection)
        viewModel.dispatch(RoomAction.CancelPendingConnection)
        runCurrent()

        assertEquals(1, fixture.transport.clearClientConnectionCallCount)
    }

    @Test
    fun disposeAfterSuccessfulJoinDoesNotClearClientConnection() = runTest {
        val persistStarted = CompletableDeferred<Unit>()
        val persistCanFinish = CompletableDeferred<Unit>()
        val connection = FakeRoomClientConnection()
        val connectResult = CompletableDeferred<Result<RoomClientConnection>>(Result.success(connection))
        val fixture = createFixture(
            connectResult = connectResult,
            persistReconnectSessionAction = {
                persistStarted.complete(Unit)
                persistCanFinish.await()
            },
        )
        val viewModel = fixture.viewModel
        collectUiState(viewModel)

        viewModel.dispatch(RoomAction.ConnectToBluetoothDevice(DEVICE_A.address))
        runCurrent()
        persistStarted.await()

        assertEquals(RoomMode.BLUETOOTH_CLIENT, viewModel.uiState.value.roomMode)
        assertTrue(viewModel.uiState.value.slots.any { it.isLocalPlayer })

        viewModel.dispatch(RoomAction.CancelPendingConnectionIfNotJoined)
        runCurrent()

        assertEquals(0, fixture.transport.clearClientConnectionCallCount)
        assertEquals(RoomMode.BLUETOOTH_CLIENT, viewModel.uiState.value.roomMode)
        assertTrue(viewModel.uiState.value.slots.any { it.isLocalPlayer })

        persistCanFinish.complete(Unit)
        runCurrent()
    }

    @Test
    fun oldConnectionFailure_afterRefreshDoesNotOverwriteCurrentSearchState() = runTest {
        val connectResult = CompletableDeferred<Result<RoomClientConnection>>()
        val fixture = createFixture(
            connectResult = connectResult,
            ignoreConnectCancellation = true,
        )
        val viewModel = fixture.viewModel
        collectUiState(viewModel)

        viewModel.dispatch(RoomAction.ConnectToBluetoothDevice(DEVICE_A.address))
        runCurrent()
        viewModel.dispatch(RoomAction.StartBluetoothDiscovery)
        connectResult.complete(Result.failure(IOException("late failure")))
        runCurrent()

        assertEquals(BluetoothSearchState.SCANNING, viewModel.uiState.value.searchState)
        assertNull(viewModel.uiState.value.joinErrorMessage)
    }

    @Test
    fun tryReconnectLastSession_whenReconnectFails_clearsSessionAndReturnsFalse() = runTest {
        val reconnectSessionRepository = reconnectSessionRepository(RECONNECT_SESSION)
        val fixture = createFixture(
            connectResult = CompletableDeferred(Result.failure(IOException("reconnect rejected"))),
            reconnectSessionRepository = reconnectSessionRepository,
        )

        val result = fixture.viewModel.tryReconnectLastSession()

        assertEquals(false, result)
        verify(reconnectSessionRepository).clearSession()
    }

    @Test
    fun tryReconnectLastSession_whenReconnectSucceeds_keepsSessionAndReturnsTrue() = runTest {
        val reconnectSessionRepository = reconnectSessionRepository(RECONNECT_SESSION)
        val fixture = createFixture(
            connectResult = CompletableDeferred(Result.success(FakeRoomClientConnection())),
            reconnectSessionRepository = reconnectSessionRepository,
        )

        val result = fixture.viewModel.tryReconnectLastSession()

        assertEquals(true, result)
        verify(reconnectSessionRepository, never()).clearSession()
    }

    private fun TestScope.collectUiState(viewModel: RoomViewModel) {
        backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()
    }

    private fun createFixture(
        connectResult: CompletableDeferred<Result<RoomClientConnection>> = CompletableDeferred(),
        ignoreConnectCancellation: Boolean = false,
        persistReconnectSessionAction: suspend (ReconnectSession) -> Unit = {},
        reconnectSessionRepository: ReconnectSessionRepository = reconnectSessionRepository(),
    ): Fixture {
        val transport = FakeRoomTransport(
            connectResult = connectResult,
            ignoreConnectCancellation = ignoreConnectCancellation,
        )
        val discoveryService = FakeDiscoveryService(listOf(DEVICE_A, DEVICE_B))
        val repository = BluetoothRoomRepository(
            roomTransport = transport,
            discoveryService = discoveryService,
            scope = CoroutineScope(SupervisorJob() + mainDispatcherRule.dispatcher),
            permissionChecker = bluetoothPermissionChecker(),
            persistReconnectSessionAction = persistReconnectSessionAction,
            appContext = org.mockito.Mockito.mock(android.content.Context::class.java),
        )
        repository.loadBondedDevicesWithFeedback()
        val viewModel = RoomViewModel(
            playerPrefsRepository = playerPrefsRepository(),
            bluetoothRoomRepository = repository,
            reconnectSessionRepository = reconnectSessionRepository,
        )
        return Fixture(
            viewModel = viewModel,
            transport = transport,
            repository = repository,
            discoveryService = discoveryService,
        )
    }

    private fun playerPrefsRepository(): PlayerPreferencesRepository {
        val repository = mock(PlayerPreferencesRepository::class.java)
        `when`(repository.playerName).thenReturn(MutableStateFlow("Player"))
        `when`(repository.avatarResId).thenReturn(MutableStateFlow(0))
        return repository
    }

    private fun reconnectSessionRepository(session: ReconnectSession? = null): ReconnectSessionRepository {
        val repository = mock(ReconnectSessionRepository::class.java)
        `when`(repository.session).thenReturn(MutableStateFlow(session))
        return repository
    }

    private fun bluetoothPermissionChecker(): BluetoothPermissionChecker {
        val permissionChecker = mock(BluetoothPermissionChecker::class.java)
        `when`(permissionChecker.isBluetoothSupported()).thenReturn(true)
        `when`(permissionChecker.hasConnectPermission()).thenReturn(true)
        `when`(permissionChecker.hasScanPermission()).thenReturn(true)
        return permissionChecker
    }

    private data class Fixture(
        val viewModel: RoomViewModel,
        val transport: FakeRoomTransport,
        val repository: BluetoothRoomRepository,
        val discoveryService: FakeDiscoveryService,
    )

    private class FakeDiscoveryService(
        private val bondedDevices: List<BluetoothDiscoveredDevice>,
    ) : BluetoothDiscoveryServicePort {
        var stopDiscoveryCallCount = 0

        override val devices = MutableStateFlow(bondedDevices)
        override val events = MutableSharedFlow<BluetoothDiscoveryEvent>()

        override fun loadBondedDevices(): List<BluetoothDiscoveredDevice> = bondedDevices

        override fun getBondedDevices(): List<BluetoothDiscoveredDevice> = bondedDevices

        override fun startDiscovery(): Result<Unit> = Result.success(Unit)

        override fun stopDiscovery() {
            stopDiscoveryCallCount++
        }

        override fun clearDiscoveredDevices() {
            devices.value = emptyList()
        }
    }

    private class FakeRoomTransport(
        private val connectResult: CompletableDeferred<Result<RoomClientConnection>>,
        private val ignoreConnectCancellation: Boolean,
    ) : RoomTransport {
        var connectToHostCallCount = 0
        val connectedAddresses = mutableListOf<String>()
        var clearClientConnectionCallCount = 0

        override val events: Flow<RoomTransportEvent> = MutableSharedFlow()

        override suspend fun startHost(config: HostTransportConfig): Result<Unit> = Result.success(Unit)

        override fun launchHostHeartbeat(
            heartbeatIntervalMs: Long,
            heartbeatTimeoutMs: Long,
        ) = Unit

        override suspend fun connectToHost(
            device: BluetoothDiscoveredDevice,
            roomUuid: UUID,
        ): Result<RoomClientConnection> {
            connectToHostCallCount++
            connectedAddresses += device.address
            return if (ignoreConnectCancellation) {
                withContext(NonCancellable) { connectResult.await() }
            } else {
                connectResult.await()
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
            clearClientConnectionCallCount++
        }
    }

    private class FakeRoomClientConnection : RoomClientConnection {
        override suspend fun awaitJoinAccepted(
            playerName: String,
            avatarResId: Int?,
            resumeParticipantId: String?,
        ): RoomWireMessage.JoinRoomAccepted {
            return RoomWireMessage.JoinRoomAccepted(
                localParticipantId = LOCAL_PARTICIPANT_ID,
                snapshot = RemoteRoomSnapshot(
                    roomName = "Host Room",
                    hostDeviceName = "Host",
                    currentRule = "SOUTHERN",
                    bluetoothVisible = true,
                    slots = listOf(
                        RemoteSlotSnapshot(
                            slotIndex = 0,
                            seatId = 0,
                            participantId = LOCAL_PARTICIPANT_ID,
                            occupantType = "HUMAN_MEMBER",
                            displayName = "Player",
                            connectionStatus = "CONNECTED",
                        ),
                    ),
                    connectionHint = "已加入房间",
                ),
            )
        }

        override fun closeNow() = Unit
    }

    class MainDispatcherRule(
        val dispatcher: TestDispatcher = StandardTestDispatcher(),
    ) : TestWatcher() {
        override fun starting(description: org.junit.runner.Description) {
            Dispatchers.setMain(dispatcher)
        }

        override fun finished(description: org.junit.runner.Description) {
            Dispatchers.resetMain()
        }
    }

    private companion object {
        val DEVICE_A = BluetoothDiscoveredDevice(
            name = "Host A",
            address = "00:11:22:33:44:55",
            isBonded = true,
        )
        val DEVICE_B = BluetoothDiscoveredDevice(
            name = "Host B",
            address = "66:77:88:99:AA:BB",
            isBonded = true,
        )
        const val LOCAL_PARTICIPANT_ID = "player-1"
        val RECONNECT_SESSION = ReconnectSession(
            hostAddress = DEVICE_A.address,
            hostDeviceName = DEVICE_A.name,
            participantId = LOCAL_PARTICIPANT_ID,
            roomName = "Host Room",
            savedAtMillis = 1L,
        )
    }
}
