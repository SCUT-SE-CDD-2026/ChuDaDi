package com.example.chudadi.network.room

import com.example.chudadi.data.repository.ReconnectSession
import com.example.chudadi.model.game.entity.MatchPhase
import com.example.chudadi.network.bluetooth.platform.BluetoothPermissionChecker
import com.example.chudadi.network.bluetooth.transport.BroadcastResult
import com.example.chudadi.network.bluetooth.transport.HostTransportConfig
import com.example.chudadi.network.bluetooth.transport.RoomTransport
import com.example.chudadi.network.bluetooth.transport.RoomTransportEvent
import com.example.chudadi.ui.room.BluetoothSearchState
import com.example.chudadi.ui.room.MemberConnectionStatus
import com.example.chudadi.ui.room.RoomAiDifficulty
import com.example.chudadi.ui.room.RoomMode
import com.example.chudadi.ui.room.SlotOccupantType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.io.IOException
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
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

    @Test
    fun startDiscoveryFailureClearsUiScanningState() = runTest {
        val scanError = SecurityException("scan denied")
        val discoveryService = FakeDiscoveryService(startDiscoveryResult = Result.failure(scanError))
        val repository = createRepository(
            roomTransport = FakeRoomTransport(),
            discoveryService = discoveryService,
            permissionChecker = bluetoothPermissionChecker(
                supported = true,
                connectPermission = true,
                scanPermission = true,
            ),
        )

        repository.startDiscoveryWithFeedback()

        val uiState = repository.roomUiState.value
        assertEquals(BluetoothSearchState.FAILED, uiState.searchState)
        assertEquals(uiState.connectionHint, uiState.joinErrorMessage)
        assertTrue(uiState.connectionHint.isNotBlank())
    }

    @Test
    fun startDiscoveryWithFeedbackWithoutPermissionStopsPreviousDiscoveryAndPublishesFailure() = runTest {
        val discoveryService = FakeDiscoveryService()
        val permissionChecker = bluetoothPermissionChecker(
            supported = true,
            connectPermission = true,
            scanPermission = true,
        )
        val repository = createRepository(
            roomTransport = FakeRoomTransport(),
            discoveryService = discoveryService,
            permissionChecker = permissionChecker,
        )

        repository.startDiscoveryWithFeedback()
        `when`(permissionChecker.hasScanPermission()).thenReturn(false)
        repository.startDiscoveryWithFeedback()

        val uiState = repository.roomUiState.value
        assertEquals(BluetoothSearchState.FAILED, uiState.searchState)
        assertEquals(uiState.connectionHint, uiState.joinErrorMessage)
        assertTrue(uiState.connectionHint.isNotBlank())
        assertEquals(1, discoveryService.stopDiscoveryCallCount)
        assertEquals(1, discoveryService.startDiscoveryCallCount)
    }

    @Test
    fun repositoryDiscoveryFinishedClearsUiScanningState() = runTest {
        val discoveryService = FakeDiscoveryService()
        val repository = createRepository(
            roomTransport = FakeRoomTransport(),
            discoveryService = discoveryService,
            permissionChecker = bluetoothPermissionChecker(
                supported = true,
                connectPermission = true,
                scanPermission = true,
            ),
            scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)),
        )
        runCurrent()

        repository.startDiscoveryWithFeedback()
        assertEquals(BluetoothSearchState.SCANNING, repository.roomUiState.value.searchState)
        discoveryService.events.tryEmit(BluetoothDiscoveryEvent.DiscoveryFinished)
        runCurrent()

        val uiState = repository.roomUiState.value
        assertEquals(BluetoothSearchState.IDLE, uiState.searchState)
        assertEquals(null, uiState.joinErrorMessage)
        assertTrue(uiState.connectionHint.isNotBlank())
    }

    @Test
    fun startHostListening_whenStartHostFails_shutsDownAndPublishesFailure() = runTest {
        val listenError = SecurityException("missing bluetooth permission")
        val transport = FakeRoomTransport(startHostResult = Result.failure(listenError))
        val repository = createRepository(
            roomTransport = transport,
            discoveryService = FakeDiscoveryService(),
            permissionChecker = bluetoothPermissionChecker(
                supported = true,
                connectPermission = true,
            ),
        )
        repository.createLocalRoom(
            playerName = "Host",
            avatarResId = null,
            hostDeviceName = "HostDevice",
        )
        val shutdownCountBeforeStart = transport.shutdownCallCount

        val result = repository.startHostListening(hostDeviceName = "HostDevice")

        assertTrue(result.isFailure)
        assertSame(listenError, result.exceptionOrNull())
        assertEquals(shutdownCountBeforeStart + 1, transport.shutdownCallCount)
        val uiState = repository.roomUiState.value
        assertEquals(RoomMode.LOCAL, uiState.roomMode)
        assertFalse(uiState.bluetoothVisible)
        assertTrue(uiState.connectionHint.isNotBlank())
    }

    @Test
    fun handleToggleReady_whenSendToHostFails_doesNotMarkLocalReady() = runTest {
        val sendError = IOException("ready write failed")
        val connection = FakeRoomClientConnection(acceptedSnapshot = connectedClientSnapshot())
        val transport = FakeRoomTransport(
            connection = connection,
            sendToHostResult = Result.failure(sendError),
        )
        val repository = createRepository(
            roomTransport = transport,
            discoveryService = FakeDiscoveryService(),
            permissionChecker = bluetoothPermissionChecker(
                supported = true,
                connectPermission = true,
            ),
            persistReconnectSessionAction = {},
        )

        val joinResult = repository.connectToHost(
            device = BluetoothDiscoveredDevice(
                name = "Host",
                address = "00:11:22:33:44:55",
                isBonded = true,
            ),
            playerName = "Player",
            avatarResId = null,
        )
        repository.handleToggleReady()

        assertTrue(joinResult.isSuccess)
        val localSlot = repository.roomUiState.value.slots.single { it.isLocalPlayer }
        assertEquals(MemberConnectionStatus.CONNECTED, localSlot.connectionStatus)
        assertTrue(repository.roomUiState.value.connectionHint.contains("ready write failed"))
    }

    @Test
    fun startNetworkMatch_whenRoomHasAi_sendsMatchStartedOnlyToOnlineHumanMembers() = runTest {
        val transport = FakeRoomTransport()
        val repository = createRepository(
            roomTransport = transport,
            discoveryService = FakeDiscoveryService(),
            permissionChecker = bluetoothPermissionChecker(
                supported = true,
                connectPermission = true,
            ),
        )
        repository.createHostRoom(
            playerName = "Host",
            avatarResId = null,
            hostDeviceName = "HostDevice",
        )
        val authorityStore = repository.authorityStoreForTest()
        authorityStore.setReadyParticipants(
            memberIds = listOf("member-1"),
            aiIds = listOf("ai-1", "ai-2"),
        )

        repository.startNetworkMatch()

        assertEquals(listOf("member-1"), transport.sentToParticipantIds)
        assertTrue(transport.sentParticipantMessages.single().isMatchStartedEnvelope())
        assertTrue(repository.matchUiState.value.phase != MatchPhase.NOT_STARTED)
    }

    @Test
    fun startNetworkMatch_whenMatchStartedSendFails_keepsHostMatchAndContinuesOtherSends() = runTest {
        val sendError = IOException("match started write failed")
        val transport = FakeRoomTransport(
            sendToParticipantResults = mapOf("member-1" to Result.failure(sendError)),
        )
        val repository = createRepository(
            roomTransport = transport,
            discoveryService = FakeDiscoveryService(),
            permissionChecker = bluetoothPermissionChecker(
                supported = true,
                connectPermission = true,
            ),
        )
        repository.createHostRoom(
            playerName = "Host",
            avatarResId = null,
            hostDeviceName = "HostDevice",
        )
        val authorityStore = repository.authorityStoreForTest()
        authorityStore.setReadyParticipants(
            memberIds = listOf("member-1", "member-2", "member-3"),
            aiIds = emptyList(),
        )

        repository.startNetworkMatch()

        assertEquals(listOf("member-1", "member-2", "member-3"), transport.sentToParticipantIds)
        assertTrue(transport.sentParticipantMessages.all { it.isMatchStartedEnvelope() })
        assertTrue(repository.matchUiState.value.phase != MatchPhase.NOT_STARTED)
        assertEquals(
            MemberConnectionStatus.DISCONNECTED,
            authorityStore.state.participants["member-1"]?.connectionStatus,
        )
        assertEquals(
            MemberConnectionStatus.READY,
            authorityStore.state.participants["member-2"]?.connectionStatus,
        )
        assertTrue(repository.roomUiState.value.connectionHint.contains("match started write failed"))
    }

    @Test
    fun startNetworkMatch_whenMemberIsDisconnected_skipsMatchStartedNotificationTarget() = runTest {
        val transport = FakeRoomTransport(
            sendToParticipantResults = mapOf("member-2" to Result.failure(IOException("unreachable"))),
        )
        val repository = createRepository(
            roomTransport = transport,
            discoveryService = FakeDiscoveryService(),
            permissionChecker = bluetoothPermissionChecker(
                supported = true,
                connectPermission = true,
            ),
        )
        repository.createHostRoom(
            playerName = "Host",
            avatarResId = null,
            hostDeviceName = "HostDevice",
        )
        val authorityStore = repository.authorityStoreForTest()
        authorityStore.setReadyParticipants(
            memberIds = listOf("member-1", "member-2"),
            aiIds = listOf("ai-1"),
            disconnectedMemberIds = setOf("member-2"),
        )

        repository.startNetworkMatch()

        assertEquals(listOf("member-1"), transport.sentToParticipantIds)
        assertTrue(repository.matchUiState.value.phase != MatchPhase.NOT_STARTED)
        assertEquals(
            MemberConnectionStatus.DISCONNECTED,
            authorityStore.state.participants["member-2"]?.connectionStatus,
        )
    }

    @Test
    fun handleRemoveSlotOccupant_whenOccupantIsAi_clearsSeatWithoutSendingRemovedFromRoom() = runTest {
        val transport = FakeRoomTransport()
        val repository = createRepository(
            roomTransport = transport,
            discoveryService = FakeDiscoveryService(),
        )
        repository.createLocalRoom(
            playerName = "Host",
            avatarResId = null,
            hostDeviceName = "HostDevice",
        )
        repository.handleAddAiToSlot(slotIndex = 1, difficulty = RoomAiDifficulty.RULE_NORMAL)
        val authorityStore = repository.authorityStoreForTest()
        val aiParticipantId = requireNotNull(authorityStore.state.slotAssignments[1])
        val broadcastCountBeforeRemove = transport.broadcastCallCount

        repository.handleRemoveSlotOccupant(slotIndex = 1)

        assertFalse(authorityStore.state.participants.containsKey(aiParticipantId))
        assertEquals(null, authorityStore.state.slotAssignments[1])
        assertEquals(0, transport.sendToParticipantCallCount)
        assertEquals(1, transport.disconnectParticipantCallCount)
        assertEquals(aiParticipantId, transport.disconnectedParticipantIds.single())
        assertEquals(broadcastCountBeforeRemove + 1, transport.broadcastCallCount)
        assertEquals(null, repository.roomUiState.value.slots[1].participantId)
    }

    @Test
    fun handleRemoveSlotOccupant_whenMemberIsDisconnected_clearsSeatWithoutRequiringNotification() = runTest {
        val transport = FakeRoomTransport(
            sendToParticipantResult = Result.failure(IOException("kick write failed")),
        )
        val repository = createRepository(
            roomTransport = transport,
            discoveryService = FakeDiscoveryService(),
        )
        repository.createLocalRoom(
            playerName = "Host",
            avatarResId = null,
            hostDeviceName = "HostDevice",
        )
        val authorityStore = repository.authorityStoreForTest()
        authorityStore.setState(
            authorityStore.state.copy(
                participants = authorityStore.state.participants + (
                    "member-1" to ParticipantRecord(
                        participantId = "member-1",
                        occupantType = SlotOccupantType.HUMAN_MEMBER,
                        displayName = "Member",
                        avatarResId = null,
                        connectionStatus = MemberConnectionStatus.DISCONNECTED,
                    )
                ),
                slotAssignments = authorityStore.state.slotAssignments + (1 to "member-1"),
            ),
        )
        val broadcastCountBeforeRemove = transport.broadcastCallCount

        repository.handleRemoveSlotOccupant(slotIndex = 1)

        assertFalse(authorityStore.state.participants.containsKey("member-1"))
        assertEquals(null, authorityStore.state.slotAssignments[1])
        assertEquals(0, transport.sendToParticipantCallCount)
        assertEquals(1, transport.disconnectParticipantCallCount)
        assertEquals("member-1", transport.disconnectedParticipantIds.single())
        assertEquals(broadcastCountBeforeRemove + 1, transport.broadcastCallCount)
        assertEquals(null, repository.roomUiState.value.slots[1].participantId)
    }

    @Test
    fun handleRemoveSlotOccupant_whenOnlineKickSendFails_stillClearsParticipantFromRoom() = runTest {
        val transport = FakeRoomTransport(
            sendToParticipantResult = Result.failure(IOException("kick write failed")),
        )
        val repository = createRepository(
            roomTransport = transport,
            discoveryService = FakeDiscoveryService(),
        )
        repository.createLocalRoom(
            playerName = "Host",
            avatarResId = null,
            hostDeviceName = "HostDevice",
        )
        val authorityStore = repository.authorityStoreForTest()
        authorityStore.setState(
            authorityStore.state.copy(
                participants = authorityStore.state.participants + (
                    "member-1" to ParticipantRecord(
                        participantId = "member-1",
                        occupantType = SlotOccupantType.HUMAN_MEMBER,
                        displayName = "Member",
                        avatarResId = null,
                        connectionStatus = MemberConnectionStatus.CONNECTED,
                    )
                ),
                slotAssignments = authorityStore.state.slotAssignments + (1 to "member-1"),
            ),
        )

        repository.handleRemoveSlotOccupant(slotIndex = 1)

        assertFalse(authorityStore.state.participants.containsKey("member-1"))
        assertEquals(null, authorityStore.state.slotAssignments[1])
        assertEquals(1, transport.sendToParticipantCallCount)
        assertEquals("member-1", transport.sentToParticipantIds.single())
        assertTrue(transport.sentParticipantMessages.single() is RoomWireMessage.RemovedFromRoom)
        assertEquals(1, transport.disconnectParticipantCallCount)
        assertEquals("member-1", transport.disconnectedParticipantIds.single())
        assertEquals(1, transport.broadcastCallCount)
        assertEquals(null, repository.roomUiState.value.slots[1].participantId)
        assertTrue(repository.roomUiState.value.connectionHint.contains("kick write failed"))
    }

    private fun RoomAuthorityStore.setReadyParticipants(
        memberIds: List<String>,
        aiIds: List<String>,
        disconnectedMemberIds: Set<String> = emptySet(),
    ) {
        val participantRecords = mutableMapOf<String, ParticipantRecord>()
        val assignments = state.slotAssignments.toMutableMap()
        var slotIndex = 1
        memberIds.forEach { participantId ->
            participantRecords[participantId] = ParticipantRecord(
                participantId = participantId,
                occupantType = SlotOccupantType.HUMAN_MEMBER,
                displayName = participantId,
                avatarResId = null,
                connectionStatus = if (participantId in disconnectedMemberIds) {
                    MemberConnectionStatus.DISCONNECTED
                } else {
                    MemberConnectionStatus.READY
                },
            )
            assignments[slotIndex] = participantId
            slotIndex++
        }
        aiIds.forEach { participantId ->
            participantRecords[participantId] = ParticipantRecord(
                participantId = participantId,
                occupantType = SlotOccupantType.AI,
                displayName = participantId,
                avatarResId = null,
                connectionStatus = MemberConnectionStatus.READY,
                aiDifficulty = RoomAiDifficulty.RULE_NORMAL,
            )
            assignments[slotIndex] = participantId
            slotIndex++
        }
        setState(
            state.copy(
                participants = state.participants + participantRecords,
                slotAssignments = assignments,
            ),
        )
    }

    private fun RoomWireMessage.isMatchStartedEnvelope(): Boolean {
        return this is RoomWireMessage.GameEnvelope &&
            message is com.example.chudadi.network.game.GameWireMessage.MatchStarted
    }

    private fun createRepository(
        roomTransport: RoomTransport,
        discoveryService: BluetoothDiscoveryServicePort,
        permissionChecker: BluetoothPermissionChecker? = null,
        persistReconnectSessionAction: (suspend (ReconnectSession) -> Unit)? = null,
        scope: CoroutineScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher()),
    ): BluetoothRoomRepository {
        return BluetoothRoomRepository(
            roomTransport = roomTransport,
            discoveryService = discoveryService,
            scope = scope,
            permissionChecker = permissionChecker,
            persistReconnectSessionAction = persistReconnectSessionAction,
            appContext = org.mockito.Mockito.mock(android.content.Context::class.java),
        )
    }

    private fun bluetoothPermissionChecker(
        supported: Boolean,
        connectPermission: Boolean,
        scanPermission: Boolean = true,
    ): BluetoothPermissionChecker {
        val permissionChecker = mock(BluetoothPermissionChecker::class.java)
        `when`(permissionChecker.isBluetoothSupported()).thenReturn(supported)
        `when`(permissionChecker.hasConnectPermission()).thenReturn(connectPermission)
        `when`(permissionChecker.hasScanPermission()).thenReturn(scanPermission)
        return permissionChecker
    }

    private fun BluetoothRoomRepository.roomRoleForTest(): String {
        val field = BluetoothRoomRepository::class.java.getDeclaredField("roomRole")
        field.isAccessible = true
        return requireNotNull(field.get(this)).toString()
    }

    private fun BluetoothRoomRepository.authorityStoreForTest(): RoomAuthorityStore {
        val field = BluetoothRoomRepository::class.java.getDeclaredField("authorityStore")
        field.isAccessible = true
        return field.get(this) as RoomAuthorityStore
    }

    private class FakeDiscoveryService(
        private val startDiscoveryResult: Result<Unit> = Result.success(Unit),
    ) : BluetoothDiscoveryServicePort {
        var clearDiscoveredDevicesCalled = false
        var clearDiscoveredDevicesCallCount = 0
        var startDiscoveryCallCount = 0
        var stopDiscoveryCallCount = 0

        override val devices = MutableStateFlow<List<BluetoothDiscoveredDevice>>(emptyList())
        override val events = MutableSharedFlow<BluetoothDiscoveryEvent>(extraBufferCapacity = 1)

        override fun loadBondedDevices(): List<BluetoothDiscoveredDevice> = emptyList()

        override fun getBondedDevices(): List<BluetoothDiscoveredDevice> = emptyList()

        override fun startDiscovery(): Result<Unit> {
            startDiscoveryCallCount++
            return startDiscoveryResult
        }

        override fun stopDiscovery() {
            stopDiscoveryCallCount++
        }

        override fun clearDiscoveredDevices() {
            clearDiscoveredDevicesCalled = true
            clearDiscoveredDevicesCallCount++
        }
    }

    private class FakeRoomClientConnection(
        private val acceptedSnapshot: RemoteRoomSnapshot = RemoteRoomSnapshot(
            roomName = "Host Room",
            hostDeviceName = "Host",
            currentRule = "SOUTHERN",
            slots = emptyList(),
        ),
    ) : RoomClientConnection {
        var closeNowCalled = false

        override suspend fun awaitJoinAccepted(
            playerName: String,
            avatarResId: Int?,
            resumeParticipantId: String?,
        ): RoomWireMessage.JoinRoomAccepted {
            return RoomWireMessage.JoinRoomAccepted(
                localParticipantId = "player-1",
                snapshot = acceptedSnapshot,
            )
        }

        override fun closeNow() {
            closeNowCalled = true
        }
    }

    @Suppress("LongParameterList")
    private class FakeRoomTransport(
        private val connection: RoomClientConnection? = null,
        private val attachClientReadLoopAction: (RoomClientConnection) -> Unit = {},
        private val startClientHeartbeatWatchdogAction: () -> Unit = {},
        private val sendToHostResult: Result<Unit> = Result.success(Unit),
        private val sendToParticipantResult: Result<Unit> = Result.success(Unit),
        private val sendToParticipantResults: Map<String, Result<Unit>> = emptyMap(),
        private val startHostResult: Result<Unit> = Result.success(Unit),
    ) : RoomTransport {
        var closeNowCalled = false
        var closeNowCallCount = 0
        var clearClientConnectionCalled = false
        var disconnectParticipantCallCount = 0
        var sendToParticipantCallCount = 0
        var broadcastCallCount = 0
        var shutdownCallCount = 0
        val sentToParticipantIds = mutableListOf<String>()
        val sentParticipantMessages = mutableListOf<RoomWireMessage>()
        val disconnectedParticipantIds = mutableListOf<String>()

        override val events: Flow<RoomTransportEvent> = MutableSharedFlow()

        override suspend fun startHost(config: HostTransportConfig): Result<Unit> = startHostResult

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

        override fun sendToHost(message: RoomWireMessage): Result<Unit> = sendToHostResult

        override fun sendToParticipant(
            participantId: String,
            message: RoomWireMessage,
        ): Result<Unit> {
            sendToParticipantCallCount++
            sentToParticipantIds += participantId
            sentParticipantMessages += message
            return sendToParticipantResults[participantId] ?: sendToParticipantResult
        }

        override fun broadcast(message: RoomWireMessage): Result<BroadcastResult> {
            broadcastCallCount++
            return Result.success(BroadcastResult())
        }

        override fun disconnectParticipant(participantId: String) {
            disconnectParticipantCallCount++
            disconnectedParticipantIds += participantId
        }

        override fun shutdown() {
            shutdownCallCount++
        }

        override fun closeNow() {
            closeNowCalled = true
            closeNowCallCount++
        }

        override fun clearClientConnection() {
            clearClientConnectionCalled = true
        }
    }

    private fun connectedClientSnapshot(): RemoteRoomSnapshot {
        return RemoteRoomSnapshot(
            roomName = "Host Room",
            hostDeviceName = "Host",
            currentRule = "SOUTHERN",
            slots = listOf(
                RemoteSlotSnapshot(
                    slotIndex = 0,
                    seatId = 0,
                    participantId = "player-1",
                    occupantType = SlotOccupantType.HUMAN_MEMBER.name,
                    displayName = "Player",
                    connectionStatus = MemberConnectionStatus.CONNECTED.name,
                ),
            ),
        )
    }
}
