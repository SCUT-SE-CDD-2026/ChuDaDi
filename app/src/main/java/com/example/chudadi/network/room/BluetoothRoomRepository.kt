@file:Suppress(
    "TooManyFunctions",
    "LongMethod",
    "CyclomaticComplexMethod",
    "LargeClass",
    "MaxLineLength",
    "LoopWithTooManyJumpStatements",
)

package com.example.chudadi.network.room

import android.annotation.SuppressLint
import android.content.Context
import com.example.chudadi.controller.game.LocalGameAction
import com.example.chudadi.data.repository.ReconnectSession
import com.example.chudadi.data.repository.ReconnectSessionRepository
import com.example.chudadi.model.game.snapshot.MatchUiState
import com.example.chudadi.network.bluetooth.platform.BluetoothDiscoverabilityController
import com.example.chudadi.network.bluetooth.platform.BluetoothDiscoveryService
import com.example.chudadi.network.bluetooth.platform.BluetoothPermissionChecker
import com.example.chudadi.network.bluetooth.transport.ClassicBluetoothTransport
import com.example.chudadi.network.bluetooth.transport.HostTransportConfig
import com.example.chudadi.network.bluetooth.transport.RoomTransport
import com.example.chudadi.network.bluetooth.transport.RoomTransportEvent
import com.example.chudadi.network.game.GameWireMessage
import com.example.chudadi.network.room.presentation.BluetoothErrorMessageMapper
import com.example.chudadi.network.room.presentation.RoomUiStateMapper
import com.example.chudadi.ui.room.AiDifficulty
import com.example.chudadi.ui.room.BluetoothSearchState
import com.example.chudadi.ui.room.GameRuleDisplay
import com.example.chudadi.ui.room.MemberConnectionStatus
import com.example.chudadi.ui.room.RoomMode
import com.example.chudadi.ui.room.RoomUiState
import com.example.chudadi.ui.room.SwapRequest
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Orchestrates bluetooth room state, UI-facing room state, and room membership/game coordinators.
 *
 * The repository intentionally depends on RoomTransport and bluetooth platform services instead of
 * manipulating RFCOMM sockets directly. It still owns room membership, seating, reconnect, and match
 * orchestration until those older room responsibilities are split safely; keep any compatibility calls
 * to the transport boundary narrow and documented.
 */
class BluetoothRoomRepository(
    private val context: Context,
    private val reconnectSessionRepository: ReconnectSessionRepository,
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val permissionChecker = BluetoothPermissionChecker(appContext)
    private val bluetoothAdapter = permissionChecker.requireBluetoothAdapter()
    private val frameCodec = RoomFrameCodec()
    private val discoveryService = BluetoothDiscoveryService(
        BluetoothDiscoveryManager(appContext, bluetoothAdapter),
    )
    private val discoverabilityController = BluetoothDiscoverabilityController(permissionChecker)
    private val authorityStore = RoomAuthorityStore()
    private val roomUiStateMapper = RoomUiStateMapper()
    private val errorMessageMapper = BluetoothErrorMessageMapper()
    private val roomTransport: RoomTransport = ClassicBluetoothTransport(
        bluetoothAdapter = bluetoothAdapter,
        frameCodec = frameCodec,
        scope = scope,
    )
    private val matchCoordinator = NetworkMatchCoordinator(scope)
    private val membershipPort = object : RoomMembershipPort {
        override fun snapshotOfCurrentRoom(): RemoteRoomSnapshot = this@BluetoothRoomRepository.snapshotOfCurrentRoom()

        override fun publishConnectionHint(message: String) {
            this@BluetoothRoomRepository.publishUiState(connectionHint = message)
        }

        override fun publishRoomClosed(message: String) {
            this@BluetoothRoomRepository.publishRoomClosedState(message)
        }

        override fun resetRoomUiState() {
            this@BluetoothRoomRepository.resetRoomUiState()
        }

        override fun broadcastSnapshot() {
            this@BluetoothRoomRepository.broadcastSnapshot()
        }

        override fun updateAllMatchSnapshots(lastActionMessage: String?) {
            this@BluetoothRoomRepository.updateAllMatchSnapshots(lastActionMessage)
        }
    }
    private val seatPort = object : RoomSeatPort {
        override fun snapshotOfCurrentRoom(): RemoteRoomSnapshot = this@BluetoothRoomRepository.snapshotOfCurrentRoom()

        override fun publishConnectionHint(message: String) {
            this@BluetoothRoomRepository.publishUiState(connectionHint = message)
        }

        override fun showHostSwapPrompt(request: SwapRequest) {
            this@BluetoothRoomRepository.showHostSwapPrompt(request)
        }

        override fun clearPendingSwapRequest() {
            this@BluetoothRoomRepository.clearPendingSwapRequest()
        }

        override fun broadcastSnapshot() {
            this@BluetoothRoomRepository.broadcastSnapshot()
        }
    }
    private val membershipCoordinator = RoomMembershipCoordinator(
        scope = scope,
        authorityStore = authorityStore,
        roomTransport = roomTransport,
        matchCoordinator = matchCoordinator,
        reconnectSessionRepository = reconnectSessionRepository,
        port = membershipPort,
    )
    private val seatCoordinator = RoomSeatCoordinator(
        authorityStore = authorityStore,
        roomTransport = roomTransport,
        localParticipantIdProvider = ::localParticipantId,
        port = seatPort,
    )

    private val _roomUiState = MutableStateFlow(RoomUiState())
    val roomUiState: StateFlow<RoomUiState> = _roomUiState.asStateFlow()
    val matchUiState: StateFlow<MatchUiState> = matchCoordinator.matchUiState
    private var roomRole: RoomRole = RoomRole.Idle

    init {
        scope.launch {
            discoveryService.events.collect { event ->
                when (event) {
                    BluetoothDiscoveryEvent.DiscoveryFinished -> {
                        _roomUiState.update(roomUiStateMapper::withDiscoveryFinished)
                    }
                }
            }
        }
        scope.launch {
            roomTransport.events.collect { event ->
                when (event) {
                    is RoomTransportEvent.IncomingConnection -> {
                        membershipCoordinator.handleIncomingConnection(event.connection)
                    }

                    is RoomTransportEvent.MessageReceived -> handleTransportMessage(event)
                    is RoomTransportEvent.ConnectionLost -> handleTransportConnectionLost(event)
                    RoomTransportEvent.HostStarted,
                    is RoomTransportEvent.HostStartFailed,
                    is RoomTransportEvent.ClientConnected,
                    is RoomTransportEvent.TransportError,
                    RoomTransportEvent.Closed,
                    -> Unit
                }
            }
        }
    }

    val isBluetoothSupported: Boolean
        get() = permissionChecker.isBluetoothSupported()

    fun isBluetoothEnabled(): Boolean = permissionChecker.isBluetoothEnabled()

    fun hasBluetoothConnectPermission(): Boolean = permissionChecker.hasConnectPermission()

    fun hasBluetoothScanPermission(): Boolean = permissionChecker.hasScanPermission()

    fun isBluetoothDiscoverabilitySupported(): Boolean = discoverabilityController.isDiscoverabilitySupported()

    @SuppressLint("MissingPermission")
    fun loadBondedDevices() {
        val devices = discoveryService.loadBondedDevices()
        _roomUiState.update { roomUiStateMapper.withBondedDevices(it, devices) }
    }

    @SuppressLint("MissingPermission")
    fun startDiscovery() {
        if (!permissionChecker.isBluetoothSupported()) {
            _roomUiState.update {
                it.copy(
                    searchState = BluetoothSearchState.FAILED,
                    connectionHint = errorMessageMapper.hostUnsupportedBluetoothMessage,
                )
            }
            return
        }
        if (!permissionChecker.hasScanPermission()) {
            _roomUiState.update {
                it.copy(
                    searchState = BluetoothSearchState.FAILED,
                    connectionHint = errorMessageMapper.missingScanPermissionMessage,
                )
            }
            return
        }
        discoveryService.stopDiscovery()
        loadBondedDevices()
        _roomUiState.update {
            it.copy(searchState = BluetoothSearchState.SCANNING, connectionHint = "正在搜索蓝牙房间...")
        }
        discoveryService.startDiscovery()
    }

    @SuppressLint("MissingPermission")
    fun getBondedDevices(): List<BluetoothDiscoveredDevice> = discoveryService.getBondedDevices()

    fun createLocalRoom(
        playerName: String,
        avatarResId: Int?,
        hostDeviceName: String,
    ) {
        shutdownCurrentRole()
        matchCoordinator.reset()
        initializeHostRoom(
            playerName = playerName,
            avatarResId = avatarResId,
            hostDeviceName = hostDeviceName,
            bluetoothVisible = false,
        )
        publishUiState(
            connectionHint = "本地房间已创建，可直接开始单机游戏",
            roomMode = RoomMode.LOCAL,
        )
    }

    fun createHostRoom(
        playerName: String,
        avatarResId: Int?,
        hostDeviceName: String,
    ): Result<Unit> {
        if (!permissionChecker.isBluetoothSupported()) {
            return Result.failure(IllegalStateException(errorMessageMapper.hostUnsupportedBluetoothMessage))
        }
        if (!permissionChecker.hasConnectPermission()) {
            return Result.failure(IllegalStateException(errorMessageMapper.createRoomMissingConnectPermissionMessage))
        }
        shutdownCurrentRole()
        matchCoordinator.reset()
        initializeHostRoom(
            playerName = playerName,
            avatarResId = avatarResId,
            hostDeviceName = hostDeviceName,
            bluetoothVisible = true,
        )
        return roomTransport.startHost(hostTransportConfig())
            .onSuccess {
                roomTransport.launchHostHeartbeat(
                    heartbeatIntervalMs = HEARTBEAT_INTERVAL_MS,
                    heartbeatTimeoutMs = HEARTBEAT_TIMEOUT_MS,
                )
                publishUiState(
                    connectionHint = "已开启蓝牙房间，等待成员加入",
                    roomMode = RoomMode.BLUETOOTH_HOST,
                )
            }
            .onFailure { error ->
                val userMessage = errorMessageMapper.toUserFacingMessage(error, errorMessageMapper.listenStartFailedMessage)
                shutdownCurrentRole()
                authorityStore.reset()
                matchCoordinator.reset()
                _roomUiState.value = RoomUiState(
                    homeNoticeMessage = userMessage,
                    connectionHint = userMessage,
                    roomMode = RoomMode.LOCAL,
                )
            }
    }

    /**
     * Starts host-side bluetooth room listening for an already-created local host room.
     *
     * This is distinct from discoverability: listening accepts RFCOMM room connections, while
     * BluetoothDiscoverabilityController only asks Android to make this device visible to scanners.
     */
    fun startHostListening(hostDeviceName: String): Result<Unit> {
        val result = when {
            roomRole !is RoomRole.Host -> {
                Result.failure(IllegalStateException("当前不是房主，无法启动房主监听"))
            }

            !permissionChecker.isBluetoothSupported() -> {
                Result.failure(IllegalStateException(errorMessageMapper.hostUnsupportedBluetoothMessage))
            }

            !permissionChecker.hasConnectPermission() -> {
                Result.failure(IllegalStateException(errorMessageMapper.hostListeningMissingConnectPermissionMessage))
            }

            else -> startHostTransportListening(hostDeviceName)
        }
        return result
    }

    suspend fun connectToHost(
        device: BluetoothDiscoveredDevice,
        playerName: String,
        avatarResId: Int?,
        resumeParticipantId: String? = null,
    ): Result<Unit> {
        if (!permissionChecker.isBluetoothSupported()) {
            return Result.failure(IllegalStateException(errorMessageMapper.connectUnsupportedBluetoothMessage))
        }
        if (!permissionChecker.hasConnectPermission()) {
            return Result.failure(IllegalStateException(errorMessageMapper.missingConnectPermissionMessage))
        }
        shutdownCurrentRole()
        _roomUiState.update {
            it.copy(
                searchState = BluetoothSearchState.CONNECTING,
                selectedDeviceAddress = device.address,
                connectionHint = "正在连接 ${device.name}...",
            )
        }
        discoveryService.stopDiscovery()
        return roomTransport.connectToHost(device, ROOM_UUID)
            .mapCatching { connection ->
                val accepted = connection.awaitJoinAccepted(
                    playerName = playerName,
                    avatarResId = avatarResId,
                    resumeParticipantId = resumeParticipantId,
                )
                acceptClientJoin(
                    accepted = accepted,
                    selectedDeviceAddress = device.address,
                )
                persistReconnectSession(
                    participantId = accepted.localParticipantId,
                    hostAddress = device.address,
                    hostDeviceName = accepted.snapshot.hostDeviceName,
                    roomName = accepted.snapshot.roomName,
                )
                roomTransport.attachClientReadLoop(connection)
                roomTransport.startClientHeartbeatWatchdog(
                    heartbeatTimeoutMs = HEARTBEAT_TIMEOUT_MS,
                    clientHeartbeatCheckIntervalMs = CLIENT_HEARTBEAT_CHECK_INTERVAL_MS,
                )
            }
            .onFailure { error ->
                val userMessage =
                    errorMessageMapper.toUserFacingMessage(error, errorMessageMapper.connectFailedRetryMessage)
                _roomUiState.update {
                    it.copy(
                        searchState = BluetoothSearchState.FAILED,
                        selectedDeviceAddress = device.address,
                        connectionHint = userMessage,
                        joinErrorMessage = userMessage,
                        joinErrorTitle = "无法加入房间",
                    )
                }
            }
    }

    suspend fun tryReconnectLastSession(
        playerName: String,
        avatarResId: Int?,
        session: ReconnectSession,
    ): Result<Unit> {
        return connectToHost(
            device = BluetoothDiscoveredDevice(
                name = session.hostDeviceName,
                address = session.hostAddress,
                isBonded = true,
            ),
            playerName = playerName,
            avatarResId = avatarResId,
            resumeParticipantId = session.participantId,
        )
    }

    fun handleToggleReady() {
        when (val role = roomRole) {
            is RoomRole.Host -> seatCoordinator.toggleReadyForParticipant(role.localParticipantId)
            is RoomRole.Client -> {
                val currentSlot = _roomUiState.value.slots.firstOrNull { it.isLocalPlayer } ?: return
                val nextReady = currentSlot.connectionStatus != MemberConnectionStatus.READY
                roomTransport.sendToHost(RoomWireMessage.ReadyStateChangeMessage(nextReady))
            }

            RoomRole.Idle -> Unit
        }
    }

    fun startNetworkMatch() {
        if (roomRole !is RoomRole.Host || _roomUiState.value.roomMode != RoomMode.BLUETOOTH_HOST) return
        matchCoordinator.startNetworkMatch(
            authorityStore = authorityStore,
            localParticipantId = localParticipantId(),
            sendToParticipant = roomTransport::sendToParticipant,
        )
        matchCoordinator.startMatchLoop(
            updateAllMatchSnapshots = ::updateAllMatchSnapshots,
            seatDisplayName = ::seatDisplayName,
            isSeatDisconnected = ::isSeatDisconnected,
        )
    }

    fun onLocalGameAction(action: LocalGameAction) {
        when (roomRole) {
            is RoomRole.Host -> {
                if (action == LocalGameAction.ExitToHome) {
                    exitMatchToRoom()
                    return
                }
                matchCoordinator.onLocalGameActionAsHost(
                    action = action,
                    authorityStore = authorityStore,
                    localParticipantId = localParticipantId(),
                    updateAllMatchSnapshots = ::updateAllMatchSnapshots,
                )
            }

            is RoomRole.Client -> {
                if (action == LocalGameAction.ExitToHome) {
                    exitMatchToRoom()
                    return
                }
                matchCoordinator.onLocalGameActionAsClient(action, roomTransport::sendToHost)
            }

            RoomRole.Idle -> Unit
        }
    }

    fun handleToggleRule() {
        if (roomRole !is RoomRole.Host) return
        authorityStore.update {
            it.copy(
                currentRule = if (it.currentRule == GameRuleDisplay.SOUTHERN) {
                    GameRuleDisplay.NORTHERN
                } else {
                    GameRuleDisplay.SOUTHERN
                },
            )
        }
        publishUiState(connectionHint = "房主已切换为${authorityStore.state.currentRule.label}")
        broadcastSnapshot()
    }

    fun handleAddAiToSlot(slotIndex: Int, difficulty: AiDifficulty) {
        if (roomRole !is RoomRole.Host) return
        seatCoordinator.handleAddAiToSlot(slotIndex, difficulty)
        publishUiState(showAiDifficultyDialog = false, aiDialogTargetSlot = -1)
    }

    fun handleRemoveSlotOccupant(slotIndex: Int) {
        if (roomRole !is RoomRole.Host) return
        val participantId = authorityStore.occupantAt(slotIndex) ?: return
        if (participantId == HOST_PARTICIPANT_ID) return
        roomTransport.sendToParticipant(participantId, RoomWireMessage.RemovedFromRoom("你已被房主移出房间"))
        authorityStore.update {
            it.copy(
                participants = it.participants - participantId,
                slotAssignments = it.slotAssignments + (slotIndex to null),
            )
        }
        publishUiState(
            connectionHint = "已移除位置 ${slotIndex + 1} 的成员",
            showSlotActionMenu = false,
            slotActionMenuTarget = -1,
            pendingSwapRequest = null,
        )
        broadcastSnapshot()
        roomTransport.disconnectParticipant(participantId)
    }

    fun handleSwapRequest(targetSlotIndex: Int) {
        when (val role = roomRole) {
            is RoomRole.Host -> seatCoordinator.handleHostSwapRequest(role.localParticipantId, targetSlotIndex)
            is RoomRole.Client -> {
                roomTransport.sendToHost(RoomWireMessage.SwapSeatRequestMessage(targetSlotIndex))
            }

            RoomRole.Idle -> Unit
        }
    }

    fun handleSwapDecision(request: SwapRequest, accepted: Boolean) {
        when (roomRole) {
            is RoomRole.Host -> {
                if (accepted) {
                    seatCoordinator.applySeatSwap(request.requesterSlotIndex, request.targetSlotIndex)
                    publishUiState(pendingSwapRequest = null, showSlotActionMenu = false)
                } else {
                    seatCoordinator.handleRemoteSwapDecision(
                        RoomWireMessage.SwapSeatDecisionMessage(
                            requesterSlotIndex = request.requesterSlotIndex,
                            targetSlotIndex = request.targetSlotIndex,
                            accepted = false,
                        ),
                    )
                    publishUiState(pendingSwapRequest = null)
                }
            }

            is RoomRole.Client -> {
                _roomUiState.update { it.copy(pendingSwapRequest = null) }
                roomTransport.sendToHost(
                    RoomWireMessage.SwapSeatDecisionMessage(
                        requesterSlotIndex = request.requesterSlotIndex,
                        targetSlotIndex = request.targetSlotIndex,
                        accepted = accepted,
                    ),
                )
            }

            RoomRole.Idle -> Unit
        }
    }

    fun dismissMenus() {
        _roomUiState.update {
            it.copy(
                showAiDifficultyDialog = false,
                aiDialogTargetSlot = -1,
                showSlotActionMenu = false,
                slotActionMenuTarget = -1,
            )
        }
    }

    fun openAiDialog(slotIndex: Int) {
        if (roomRole !is RoomRole.Host) return
        _roomUiState.update { it.copy(showAiDifficultyDialog = true, aiDialogTargetSlot = slotIndex) }
    }

    fun openSlotActionMenu(slotIndex: Int) {
        _roomUiState.update { it.copy(showSlotActionMenu = true, slotActionMenuTarget = slotIndex) }
    }

    fun leaveRoom() {
        when (roomRole) {
            is RoomRole.Client -> roomTransport.sendToHost(RoomWireMessage.LeaveRoomMessage)
            is RoomRole.Host -> {
                roomTransport.broadcast(RoomWireMessage.RoomClosedByHost("房主已关闭房间"))
            }

            RoomRole.Idle -> Unit
        }
        matchCoordinator.reset()
        shutdownCurrentRole()
        authorityStore.reset()
        _roomUiState.value = RoomUiState(
            homeNoticeMessage = _roomUiState.value.homeNoticeMessage,
            roomMode = RoomMode.LOCAL,
        )
        scope.launch { reconnectSessionRepository.clearSession() }
    }

    fun exitMatchToRoom() {
        matchCoordinator.exitMatchToRoom()
    }

    fun consumeRoomExitNotice() {
        _roomUiState.update {
            it.copy(
                removedFromRoom = false,
                roomClosedByHost = false,
            )
        }
    }

    fun consumeHomeNotice() {
        _roomUiState.update { it.copy(homeNoticeMessage = null) }
    }

    fun consumeJoinError() {
        _roomUiState.update {
            it.copy(
                joinErrorMessage = null,
                joinErrorTitle = "无法加入房间",
            )
        }
    }

    fun showHomeNotice(message: String) {
        _roomUiState.update {
            it.copy(
                homeNoticeMessage = message,
                connectionHint = message,
            )
        }
    }

    fun showJoinError(
        message: String,
        title: String = "无法加入房间",
    ) {
        _roomUiState.update {
            it.copy(
                searchState = BluetoothSearchState.FAILED,
                connectionHint = message,
                joinErrorMessage = message,
                joinErrorTitle = title,
            )
        }
    }

    @SuppressLint("MissingPermission")
    fun loadBondedDevicesWithFeedback() {
        if (!permissionChecker.hasConnectPermission()) {
            showJoinError(
                message = errorMessageMapper.missingConnectPermissionMessage,
                title = "无法搜索房间",
            )
            return
        }
        loadBondedDevices()
        _roomUiState.update {
            it.copy(
                joinErrorMessage = null,
                joinErrorTitle = "无法加入房间",
            )
        }
    }

    @SuppressLint("MissingPermission")
    fun startDiscoveryWithFeedback() {
        if (!permissionChecker.isBluetoothSupported()) {
            showJoinError(
                message = errorMessageMapper.hostUnsupportedBluetoothMessage,
                title = "无法搜索房间",
            )
            return
        }
        if (!permissionChecker.hasScanPermission()) {
            showJoinError(
                message = errorMessageMapper.missingScanPermissionMessage,
                title = "无法搜索房间",
            )
            return
        }
        loadBondedDevicesWithFeedback()
        val started = discoveryService.startDiscovery()
        if (!started) {
            showJoinError(
                message = errorMessageMapper.discoveryStartFailedMessage,
                title = "扫描启动失败",
            )
            return
        }
        _roomUiState.update {
            it.copy(
                searchState = BluetoothSearchState.SCANNING,
                connectionHint = "正在搜索蓝牙房间...",
                joinErrorMessage = null,
                joinErrorTitle = "无法加入房间",
            )
        }
    }

    fun resetRoomScores() {
        if (roomRole !is RoomRole.Host) return
        authorityStore.update { state ->
            state.copy(
                participants = state.participants.mapValues { (_, participant) ->
                    participant.copy(cumulativeScore = 0)
                },
            )
        }
        publishUiState(connectionHint = "已重置房间分数")
        broadcastSnapshot()
    }

    fun clear() {
        shutdownCurrentRole()
        discoveryService.clearDiscoveredDevices()
        scope.cancel()
    }

    private fun handleHostSocketMessage(participantId: String, message: RoomWireMessage) {
        when (message) {
            is RoomWireMessage.ReadyStateChangeMessage -> seatCoordinator.setRemoteReady(participantId, message.ready)
            is RoomWireMessage.SwapSeatRequestMessage -> seatCoordinator.handleHostSwapRequest(participantId, message.targetSlotIndex)
            is RoomWireMessage.SwapSeatDecisionMessage -> {
                seatCoordinator.handleRemoteSwapDecision(message)
                publishUiState(pendingSwapRequest = null)
            }
            is RoomWireMessage.GameEnvelope -> {
                matchCoordinator.handleHostGameEnvelope(
                    participantId = participantId,
                    message = message.message,
                    authorityStore = authorityStore,
                    sendToParticipant = roomTransport::sendToParticipant,
                    updateAllMatchSnapshots = ::updateAllMatchSnapshots,
                )
            }

            RoomWireMessage.LeaveRoomMessage -> membershipCoordinator.handleRemoteLeave(participantId)
            else -> Unit
        }
    }

    private fun handleTransportMessage(event: RoomTransportEvent.MessageReceived) {
        val participantId = event.fromParticipantId
        if (participantId == null) {
            handleClientSocketMessage(event.payload)
        } else {
            handleHostSocketMessage(participantId, event.payload)
        }
    }

    private fun handleTransportConnectionLost(event: RoomTransportEvent.ConnectionLost) {
        val participantId = event.participantId
        if (participantId == null) {
            membershipCoordinator.handleHostConnectionLost(event.cause?.message ?: "与房主的连接已断开")
        } else {
            membershipCoordinator.markParticipantDisconnected(participantId)
        }
    }

    private fun handleClientSocketMessage(message: RoomWireMessage) {
        when (message) {
            is RoomWireMessage.RoomSnapshotMessage -> handleClientRoomSnapshot(message)

            is RoomWireMessage.SwapSeatPromptMessage -> {
                _roomUiState.update {
                    it.copy(
                        pendingSwapRequest = SwapRequest(
                            requesterSlotIndex = message.request.requesterSlotIndex,
                            targetSlotIndex = message.request.targetSlotIndex,
                            requesterName = message.request.requesterName,
                        ),
                        connectionHint = "收到换位请求",
                    )
                }
            }

            is RoomWireMessage.JoinRoomRejected -> handleClientJoinRejected(message)

            is RoomWireMessage.RemovedFromRoom -> handleClientRoomTermination(
                reason = message.reason,
                removedFromRoom = true,
            )

            is RoomWireMessage.RoomClosedByHost -> handleClientRoomTermination(
                reason = message.reason,
                removedFromRoom = false,
            )

            is RoomWireMessage.GameEnvelope -> {
                matchCoordinator.handleClientGameEnvelope(
                    message.message,
                    handleHostConnectionLost = { reason ->
                        shutdownCurrentRole()
                        membershipCoordinator.handleHostConnectionLost(reason)
                    },
                )
            }

            else -> Unit
        }
    }

    private fun initializeHostRoom(
        playerName: String,
        avatarResId: Int?,
        hostDeviceName: String,
        bluetoothVisible: Boolean,
    ) {
        roomRole = RoomRole.Host(
            localParticipantId = HOST_PARTICIPANT_ID,
            hostDeviceName = hostDeviceName,
        )
        authorityStore.createHostRoom(
            playerName = playerName,
            avatarResId = avatarResId,
            hostDeviceName = hostDeviceName,
            bluetoothVisible = bluetoothVisible,
        )
    }

    private fun acceptClientJoin(
        accepted: RoomWireMessage.JoinRoomAccepted,
        selectedDeviceAddress: String,
    ) {
        roomRole = RoomRole.Client(
            localParticipantId = accepted.localParticipantId,
            hostDeviceName = accepted.snapshot.hostDeviceName,
        )
        authorityStore.applyRemoteSnapshot(accepted.snapshot)
        publishUiState(
            connectionHint = accepted.snapshot.connectionHint,
            localParticipantId = accepted.localParticipantId,
            isHost = false,
            roomMode = RoomMode.BLUETOOTH_CLIENT,
            searchState = BluetoothSearchState.IDLE,
            selectedDeviceAddress = selectedDeviceAddress,
        )
    }

    private fun handleClientRoomSnapshot(message: RoomWireMessage.RoomSnapshotMessage) {
        val localParticipantId = (roomRole as? RoomRole.Client)?.localParticipantId ?: return
        authorityStore.applyRemoteSnapshot(message.snapshot)
        publishUiState(
            connectionHint = message.snapshot.connectionHint,
            localParticipantId = localParticipantId,
            isHost = false,
        )
    }

    private fun handleClientJoinRejected(message: RoomWireMessage.JoinRoomRejected) {
        _roomUiState.update {
            it.copy(
                connectionHint = message.reason,
                joinErrorMessage = message.reason,
                joinErrorTitle = "无法加入房间",
                searchState = BluetoothSearchState.FAILED,
            )
        }
    }

    private fun handleClientRoomTermination(
        reason: String,
        removedFromRoom: Boolean,
    ) {
        _roomUiState.update {
            it.copy(
                connectionHint = reason,
                removedFromRoom = removedFromRoom,
                roomClosedByHost = !removedFromRoom,
                homeNoticeMessage = reason,
            )
        }
        shutdownCurrentRole()
        if (removedFromRoom) {
            membershipCoordinator.handleRemovedFromRoom(reason)
        } else {
            membershipCoordinator.handleRoomClosedByHost(reason)
        }
    }

    private fun updateAllMatchSnapshots(lastActionMessage: String?) {
        matchCoordinator.updateAllMatchSnapshots(
            authorityStore = authorityStore,
            localParticipantId = localParticipantId(),
            sendToParticipant = roomTransport::sendToParticipant,
            onMatchFinished = { roundScores ->
                authorityStore.applyRoundScores(roundScores)
                authorityStore.resetReadyStatesAfterMatchFinished()
                publishUiState()
                broadcastSnapshot()
            },
            lastActionMessage = lastActionMessage,
        )
    }

    private fun publishRoomClosedState(message: String) {
        _roomUiState.update {
            it.copy(
                connectionHint = message,
                roomClosedByHost = true,
                homeNoticeMessage = message,
            )
        }
    }

    private fun resetRoomUiState() {
        _roomUiState.update(roomUiStateMapper::resetRoomUiState)
    }

    /**
     * Broadcasts the current room snapshot to all connected participants.
     *
     * In this repository, "broadcast" only means multi-recipient message sending. It must not be
     * used as a synonym for starting host listening or making the local device discoverable.
     */
    private fun broadcastSnapshot() {
        if (roomRole !is RoomRole.Host) return
        roomTransport.broadcast(RoomWireMessage.RoomSnapshotMessage(snapshotOfCurrentRoom()))
    }

    private fun snapshotOfCurrentRoom(): RemoteRoomSnapshot {
        return authorityStore.snapshotOfCurrentRoom(
            connectionHint = _roomUiState.value.connectionHint,
            localParticipantId = localParticipantId(),
        )
    }

    /**
     * Compatibility helper for enabling transport listening after a local room already exists.
     *
     * Long term, host creation and host listening can be unified at the caller boundary. For now this
     * keeps the existing local-room-to-bluetooth-room flow behavior unchanged.
     */
    private fun startHostTransportListening(hostDeviceName: String): Result<Unit> {
        authorityStore.update { state ->
            state.copy(
                hostDeviceName = hostDeviceName,
                bluetoothVisible = true,
            )
        }
        return roomTransport.startHost(hostTransportConfig())
            .onSuccess {
                roomTransport.launchHostHeartbeat(
                    heartbeatIntervalMs = HEARTBEAT_INTERVAL_MS,
                    heartbeatTimeoutMs = HEARTBEAT_TIMEOUT_MS,
                )
                publishUiState(
                    connectionHint = "已开启蓝牙房间，等待成员加入",
                    roomMode = RoomMode.BLUETOOTH_HOST,
                )
            }
            .onFailure { error ->
                val userMessage = errorMessageMapper.toUserFacingMessage(error, errorMessageMapper.listenStartFailedMessage)
                authorityStore.update { state -> state.copy(bluetoothVisible = false) }
                publishUiState(
                    connectionHint = userMessage,
                    roomMode = RoomMode.LOCAL,
                )
            }
    }

    private fun hostTransportConfig(): HostTransportConfig {
        return HostTransportConfig(
            serviceName = SERVICE_NAME,
            serviceUuid = ROOM_UUID,
        )
    }

    @Suppress("LongParameterList")
    private fun publishUiState(
        connectionHint: String? = null,
        pendingSwapRequest: SwapRequest? = _roomUiState.value.pendingSwapRequest,
        showAiDifficultyDialog: Boolean = _roomUiState.value.showAiDifficultyDialog,
        aiDialogTargetSlot: Int = _roomUiState.value.aiDialogTargetSlot,
        showSlotActionMenu: Boolean = _roomUiState.value.showSlotActionMenu,
        slotActionMenuTarget: Int = _roomUiState.value.slotActionMenuTarget,
        localParticipantId: String = localParticipantId(),
        isHost: Boolean = roomRole is RoomRole.Host,
        roomMode: RoomMode = _roomUiState.value.roomMode,
        searchState: BluetoothSearchState = _roomUiState.value.searchState,
        selectedDeviceAddress: String? = _roomUiState.value.selectedDeviceAddress,
    ) {
        _roomUiState.value = roomUiStateMapper.toUiState(
            authorityStore = authorityStore,
            previousState = _roomUiState.value,
            discoveredDevices = discoveryService.devices.value,
            connectionHint = connectionHint,
            pendingSwapRequest = pendingSwapRequest,
            showAiDifficultyDialog = showAiDifficultyDialog,
            aiDialogTargetSlot = aiDialogTargetSlot,
            showSlotActionMenu = showSlotActionMenu,
            slotActionMenuTarget = slotActionMenuTarget,
            localParticipantId = localParticipantId,
            isHost = isHost,
            roomMode = roomMode,
            searchState = searchState,
            selectedDeviceAddress = selectedDeviceAddress,
        )
    }

    private suspend fun persistReconnectSession(
        participantId: String,
        hostAddress: String,
        hostDeviceName: String,
        roomName: String,
    ) {
        reconnectSessionRepository.updateSession(
            ReconnectSession(
                hostAddress = hostAddress,
                hostDeviceName = hostDeviceName,
                participantId = participantId,
                roomName = roomName,
                savedAtMillis = System.currentTimeMillis(),
            ),
        )
    }

    private fun shutdownCurrentRole() {
        roomTransport.shutdown()
        roomRole = RoomRole.Idle
    }

    private fun isSeatDisconnected(seatId: Int): Boolean {
        val participantId = authorityStore.occupantAt(seatId) ?: return false
        return authorityStore.state.participants[participantId]?.connectionStatus == MemberConnectionStatus.DISCONNECTED
    }

    private fun seatDisplayName(seatId: Int): String {
        val participantId = authorityStore.occupantAt(seatId) ?: return "玩家${seatId + 1}"
        return authorityStore.state.participants[participantId]?.displayName ?: "玩家${seatId + 1}"
    }

    private fun localParticipantId(): String {
        return when (val role = roomRole) {
            is RoomRole.Host -> role.localParticipantId
            is RoomRole.Client -> role.localParticipantId
            RoomRole.Idle -> HOST_PARTICIPANT_ID
        }
    }

    private fun showHostSwapPrompt(request: SwapRequest) {
        publishUiState(
            pendingSwapRequest = request,
            connectionHint = "${request.requesterName} 请求与房主换位",
        )
    }

    private fun clearPendingSwapRequest() {
        _roomUiState.update { it.copy(pendingSwapRequest = null) }
    }

    private sealed interface RoomRole {
        data object Idle : RoomRole

        data class Host(
            val localParticipantId: String,
            val hostDeviceName: String,
        ) : RoomRole

        data class Client(
            val localParticipantId: String,
            val hostDeviceName: String,
        ) : RoomRole
    }

    private companion object {
        const val SERVICE_NAME = "ChuDaDiRoom"
        val ROOM_UUID: UUID = UUID.fromString("a9b56c03-6cae-417b-a522-3b299d790e14")
        const val HEARTBEAT_INTERVAL_MS = 5_000L
        const val HEARTBEAT_TIMEOUT_MS = 15_000L
        const val CLIENT_HEARTBEAT_CHECK_INTERVAL_MS = 3_000L
    }
}
