@file:Suppress(
    "TooManyFunctions",
    "LongMethod",
    "CyclomaticComplexMethod",
    "LargeClass",
    "MaxLineLength",
    "LoopWithTooManyJumpStatements",
    "LongParameterList",
)

package com.example.chudadi.network.room

import android.content.Context
import com.example.chudadi.BuildConfig
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
import com.example.chudadi.ui.room.AiSelectionStep
import com.example.chudadi.ui.room.AIType
import com.example.chudadi.ui.room.RoomAiDifficulty
import com.example.chudadi.ui.room.BluetoothSearchState
import com.example.chudadi.ui.room.GameRuleDisplay
import com.example.chudadi.ui.room.MemberConnectionStatus
import com.example.chudadi.ui.room.RoomMode
import com.example.chudadi.ui.room.RoomUiState
import com.example.chudadi.ui.room.SlotOccupantType
import com.example.chudadi.ui.room.SlotState
import com.example.chudadi.ui.room.SwapRequest
import java.util.UUID
import kotlinx.coroutines.CancellationException
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
class BluetoothRoomRepository private constructor(
    dependencies: BluetoothRoomRepositoryDependencies,
) {
    constructor(
        context: Context,
        reconnectSessionRepository: ReconnectSessionRepository,
    ) : this(createRuntimeDependencies(context.applicationContext, reconnectSessionRepository))

    internal constructor(
        roomTransport: RoomTransport,
        discoveryService: BluetoothDiscoveryServicePort,
        scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        permissionChecker: BluetoothPermissionChecker? = null,
        persistReconnectSessionAction: (suspend (ReconnectSession) -> Unit)? = null,
    ) : this(
        BluetoothRoomRepositoryDependencies(
            reconnectSessionRepository = null,
            scope = scope,
            permissionChecker = permissionChecker,
            discoveryService = discoveryService,
            discoverabilityController = null,
            roomTransport = roomTransport,
            persistReconnectSessionAction = persistReconnectSessionAction,
        ),
    )

    private val reconnectSessionRepository = dependencies.reconnectSessionRepository
    private val scope = dependencies.scope
    private val permissionChecker = dependencies.permissionChecker
    private val discoveryService = dependencies.discoveryService
    private val discoverabilityController = dependencies.discoverabilityController
    private val persistReconnectSessionAction = dependencies.persistReconnectSessionAction
    private val authorityStore = RoomAuthorityStore()
    private val roomUiStateMapper = RoomUiStateMapper()
    private val errorMessageMapper = BluetoothErrorMessageMapper()
    private val roomTransport = dependencies.roomTransport
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
    private val membershipCoordinator by lazy {
        RoomMembershipCoordinator(
            scope = scope,
            authorityStore = authorityStore,
            roomTransport = roomTransport,
            matchCoordinator = matchCoordinator,
            reconnectSessionRepository = requireReconnectSessionRepository(),
            port = membershipPort,
        )
    }
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
                    is RoomTransportEvent.SendFailed -> handleTransportSendFailed(event)
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
        get() = requirePermissionChecker().isBluetoothSupported()

    fun isBluetoothEnabled(): Boolean = requirePermissionChecker().isBluetoothEnabled()

    fun hasBluetoothConnectPermission(): Boolean = requirePermissionChecker().hasConnectPermission()

    fun hasBluetoothScanPermission(): Boolean = requirePermissionChecker().hasScanPermission()

    fun isBluetoothDiscoverabilitySupported(): Boolean = requireDiscoverabilityController().isDiscoverabilitySupported()

    fun loadBondedDevices() {
        val devices = discoveryService.loadBondedDevices()
        _roomUiState.update { roomUiStateMapper.withBondedDevices(it, devices) }
    }

    fun startDiscovery() {
        if (!requirePermissionChecker().isBluetoothSupported()) {
            _roomUiState.update {
                it.copy(
                    searchState = BluetoothSearchState.FAILED,
                    connectionHint = errorMessageMapper.hostUnsupportedBluetoothMessage,
                )
            }
            return
        }
        if (!requirePermissionChecker().hasScanPermission()) {
            discoveryService.stopDiscovery()
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
            .onFailure { error -> publishDiscoveryStartFailure(error) }
    }

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

    suspend fun createHostRoom(
        playerName: String,
        avatarResId: Int?,
        hostDeviceName: String,
    ): Result<Unit> {
        if (!requirePermissionChecker().isBluetoothSupported()) {
            return Result.failure(IllegalStateException(errorMessageMapper.hostUnsupportedBluetoothMessage))
        }
        if (!requirePermissionChecker().hasConnectPermission()) {
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
    suspend fun startHostListening(hostDeviceName: String): Result<Unit> {
        val result = when {
            roomRole !is RoomRole.Host -> {
                Result.failure(IllegalStateException("当前不是房主，无法启动房主监听"))
            }

            !requirePermissionChecker().isBluetoothSupported() -> {
                Result.failure(IllegalStateException(errorMessageMapper.hostUnsupportedBluetoothMessage))
            }

            !requirePermissionChecker().hasConnectPermission() -> {
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
        shouldAcceptResult: () -> Boolean = { true },
        onClientJoinAccepted: () -> Unit = {},
    ): Result<Unit> {
        if (!requirePermissionChecker().isBluetoothSupported()) {
            return Result.failure(IllegalStateException(errorMessageMapper.connectUnsupportedBluetoothMessage))
        }
        if (!requirePermissionChecker().hasConnectPermission()) {
            return Result.failure(IllegalStateException(errorMessageMapper.missingConnectPermissionMessage))
        }
        ensureClientJoinStillCurrent(shouldAcceptResult)
        shutdownCurrentRole()
        ensureClientJoinStillCurrent(shouldAcceptResult)
        _roomUiState.update {
            it.copy(
                searchState = BluetoothSearchState.CONNECTING,
                selectedDeviceAddress = device.address,
                connectionHint = "正在连接 ${device.name}...",
            )
        }
        discoveryService.stopDiscovery()
        ensureClientJoinStillCurrent(shouldAcceptResult)
        val joinConnector = BluetoothClientJoinConnector(
            roomTransport = roomTransport,
            roomUuid = ROOM_UUID,
            connectTimeoutMs = CONNECT_TIMEOUT_MS,
            connectTimeoutMessage = CONNECT_TIMEOUT_MESSAGE,
            joinTimeoutMs = JOIN_TIMEOUT_MS,
            joinTimeoutMessage = JOIN_TIMEOUT_MESSAGE,
        )
        val joinResult = joinConnector.connectAndAwaitAccepted(
            device = device,
            playerName = playerName,
            avatarResId = avatarResId,
            resumeParticipantId = resumeParticipantId,
        )
        ensureClientJoinStillCurrent(shouldAcceptResult)
        val joinError = joinResult.exceptionOrNull()
        return if (joinError != null) {
            publishJoinFailure(device, joinError)
            Result.failure(joinError)
        } else {
            val session = requireNotNull(joinResult.getOrNull())
            ensureClientJoinStillCurrent(shouldAcceptResult) {
                cleanupFailedClientJoin(session.connection)
            }
            val setupResult = completeClientJoinAfterConnect(
                session = session,
                device = device,
                shouldAcceptResult = shouldAcceptResult,
                onClientJoinAccepted = onClientJoinAccepted,
            )
            ensureClientJoinStillCurrent(shouldAcceptResult)
            setupResult.exceptionOrNull()?.let { error -> publishJoinFailure(device, error) }
            setupResult
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun completeClientJoinAfterConnect(
        session: ClientJoinSession,
        device: BluetoothDiscoveredDevice,
        shouldAcceptResult: () -> Boolean,
        onClientJoinAccepted: () -> Unit,
    ): Result<Unit> {
        return try {
            val accepted = session.accepted
            ensureClientJoinStillCurrent(shouldAcceptResult)
            onClientJoinAccepted()
            acceptClientJoin(
                accepted = accepted,
                selectedDeviceAddress = device.address,
            )
            ensureClientJoinStillCurrent(shouldAcceptResult)
            persistReconnectSession(
                participantId = accepted.localParticipantId,
                hostAddress = device.address,
                hostDeviceName = accepted.snapshot.hostDeviceName,
                roomName = accepted.snapshot.roomName,
            )
            roomTransport.attachClientReadLoop(session.connection)
            ensureClientJoinStillCurrent(shouldAcceptResult)
            roomTransport.startClientHeartbeatWatchdog(
                heartbeatTimeoutMs = HEARTBEAT_TIMEOUT_MS,
                clientHeartbeatCheckIntervalMs = CLIENT_HEARTBEAT_CHECK_INTERVAL_MS,
            )
            Result.success(Unit)
        } catch (error: CancellationException) {
            cleanupFailedClientJoin(session.connection)
            throw error
        } catch (error: Throwable) {
            cleanupFailedClientJoin(session.connection)
            Result.failure(error)
        }
    }

    private fun cleanupFailedClientJoin(connection: RoomClientConnection) {
        roomTransport.cleanupFailedClientJoin(connection)
        resetClientJoinStateAfterSetupFailure()
    }

    fun cancelPendingClientConnection() {
        roomTransport.clearClientConnection()
        _roomUiState.update {
            if (it.searchState == BluetoothSearchState.CONNECTING) {
                it.copy(
                    searchState = BluetoothSearchState.IDLE,
                    selectedDeviceAddress = null,
                    connectionHint = "",
                    joinErrorMessage = null,
                    joinErrorTitle = "无法加入房间",
                )
            } else {
                it
            }
        }
    }

    private fun ensureClientJoinStillCurrent(
        shouldAcceptResult: () -> Boolean,
        cleanup: () -> Unit = {},
    ) {
        if (shouldAcceptResult()) return
        cleanup()
        throw CancellationException("Client join was cancelled")
    }

    private fun resetClientJoinStateAfterSetupFailure() {
        roomRole = RoomRole.Idle
        authorityStore.reset()
        _roomUiState.update { state ->
            state.copy(
                isHost = false,
                roomMode = RoomMode.LOCAL,
                roomName = "",
                hostDeviceName = "",
                slots = List(RoomAuthorityStore.SLOT_COUNT) { index -> SlotState(slotIndex = index) },
                bluetoothVisible = false,
                selectedDeviceAddress = null,
                canStartGame = false,
                canStartLocalGame = false,
                canStartNetworkGame = false,
                canEnableBroadcast = false,
                canManageAiSeats = false,
                pendingSwapRequest = null,
            )
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
                    .onFailure { error -> publishSendFailure("准备状态", error) }
            }

            RoomRole.Idle -> Unit
        }
    }

    fun startNetworkMatch(aiMoveDelayMillis: Long = 0L) {
        if (roomRole !is RoomRole.Host || _roomUiState.value.roomMode != RoomMode.BLUETOOTH_HOST) return
        val startResult = matchCoordinator.startNetworkMatch(
            authorityStore = authorityStore,
            localParticipantId = localParticipantId(),
            sendToParticipant = roomTransport::sendToParticipant,
            onMatchStartedSendFailed = ::handleMatchStartedSendFailure,
            aiMoveDelayMillis = aiMoveDelayMillis,
        )
        if (startResult.isFailure) {
            matchCoordinator.reset()
            publishSendFailure("开始游戏", requireNotNull(startResult.exceptionOrNull()))
            return
        }
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
                    .onFailure { error -> publishSendFailure("游戏消息", error) }
            }

            RoomRole.Idle -> Unit
        }
    }

    private fun handleMatchStartedSendFailure(participantId: String, error: Throwable) {
        authorityStore.updateParticipant(participantId) {
            it.copy(connectionStatus = MemberConnectionStatus.DISCONNECTED)
        }
        matchCoordinator.onParticipantDisconnected(participantId, authorityStore)
        publishSendFailure("MatchStarted", error)
        broadcastSnapshot()
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

    fun handleAddAiToSlot(slotIndex: Int, difficulty: RoomAiDifficulty) {
        if (roomRole !is RoomRole.Host) return
        seatCoordinator.handleAddAiToSlot(slotIndex, difficulty)
        publishUiState(showRoomAiDifficultyDialog = false, aiDialogTargetSlot = -1)
    }

    fun handleRemoveSlotOccupant(slotIndex: Int) {
        val participantId = authorityStore.occupantAt(slotIndex)
        if (roomRole !is RoomRole.Host || participantId == null) return
        if (participantId == HOST_PARTICIPANT_ID && !BuildConfig.DEBUG) return
        val participant = authorityStore.state.participants[participantId]
        val notifyFailure = participant?.let {
            tryNotifyRemovedFromRoomIfNeeded(participantId = participantId, participant = it)
        }
        clearSlotOccupantAuthoritatively(
            slotIndex = slotIndex,
            participantId = participantId,
            notifyFailure = notifyFailure,
        )
    }

    private fun tryNotifyRemovedFromRoomIfNeeded(
        participantId: String,
        participant: ParticipantRecord,
    ): Throwable? {
        if (participant.occupantType != SlotOccupantType.HUMAN_MEMBER ||
            participant.connectionStatus == MemberConnectionStatus.DISCONNECTED
        ) {
            return null
        }
        return roomTransport.sendToParticipant(
            participantId,
            RoomWireMessage.RemovedFromRoom("你已被房主移出房间"),
        ).exceptionOrNull()
    }

    private fun clearSlotOccupantAuthoritatively(
        slotIndex: Int,
        participantId: String,
        notifyFailure: Throwable?,
    ) {
        authorityStore.update {
            it.copy(
                participants = it.participants - participantId,
                slotAssignments = it.slotAssignments + (slotIndex to null),
            )
        }
        val notificationSuffix = notifyFailure?.message?.let { "，移除通知发送失败：$it" }.orEmpty()
        publishUiState(
            connectionHint = "已移除位置 ${slotIndex + 1} 的成员$notificationSuffix",
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
                    .onFailure { error -> publishSendFailure("换位请求", error) }
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
                val sendResult = roomTransport.sendToHost(
                    RoomWireMessage.SwapSeatDecisionMessage(
                        requesterSlotIndex = request.requesterSlotIndex,
                        targetSlotIndex = request.targetSlotIndex,
                        accepted = accepted,
                    ),
                )
                if (sendResult.isSuccess) {
                    _roomUiState.update { it.copy(pendingSwapRequest = null) }
                } else {
                    publishSendFailure(if (accepted) "换位确认" else "换位拒绝", requireNotNull(sendResult.exceptionOrNull()))
                }
            }

            RoomRole.Idle -> Unit
        }
    }

    fun dismissMenus() {
        _roomUiState.update {
            it.copy(
                showRoomAiDifficultyDialog = false,
                aiDialogTargetSlot = -1,
                showSlotActionMenu = false,
                slotActionMenuTarget = -1,
            )
        }
    }

    fun openAiDialog(slotIndex: Int) {
        if (roomRole !is RoomRole.Host) return
        _roomUiState.update { it.copy(showRoomAiDifficultyDialog = true, aiDialogTargetSlot = slotIndex) }
    }

    fun openSlotActionMenu(slotIndex: Int) {
        _roomUiState.update { it.copy(showSlotActionMenu = true, slotActionMenuTarget = slotIndex) }
    }

    fun leaveRoom() {
        when (roomRole) {
            is RoomRole.Client -> {
                roomTransport.sendToHost(RoomWireMessage.LeaveRoomMessage)
                    .onFailure { error -> publishSendFailure("离房消息", error) }
            }
            is RoomRole.Host -> {
                roomTransport.broadcast(RoomWireMessage.RoomClosedByHost("房主已关闭房间"))
                    .onFailure { error -> publishSendFailure("关闭房间消息", error) }
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
        scope.launch { requireReconnectSessionRepository().clearSession() }
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

    private fun publishDiscoveryStartFailure(error: Throwable) {
        val userMessage = errorMessageMapper.toUserFacingMessage(error, errorMessageMapper.discoveryStartFailedMessage)
        showJoinError(
            message = userMessage,
            title = "扫描启动失败",
        )
    }

    fun loadBondedDevicesWithFeedback() {
        if (!requirePermissionChecker().hasConnectPermission()) {
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

    fun startDiscoveryWithFeedback() {
        if (!requirePermissionChecker().isBluetoothSupported()) {
            showJoinError(
                message = errorMessageMapper.hostUnsupportedBluetoothMessage,
                title = "无法搜索房间",
            )
            return
        }
        if (!requirePermissionChecker().hasScanPermission()) {
            discoveryService.stopDiscovery()
            showJoinError(
                message = errorMessageMapper.missingScanPermissionMessage,
                title = "无法搜索房间",
            )
            return
        }
        loadBondedDevicesWithFeedback()
        val startResult = discoveryService.startDiscovery()
        if (startResult.isFailure) {
            publishDiscoveryStartFailure(requireNotNull(startResult.exceptionOrNull()))
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

    fun stopDiscovery() {
        discoveryService.stopDiscovery()
        _roomUiState.update {
            if (it.searchState == BluetoothSearchState.SCANNING) {
                it.copy(searchState = BluetoothSearchState.IDLE)
            } else {
                it
            }
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
        roomTransport.closeNow()
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

    private fun handleTransportSendFailed(event: RoomTransportEvent.SendFailed) {
        publishSendFailure(
            operation = event.messageType,
            error = event.cause,
        )
    }

    private fun publishSendFailure(operation: String, error: Throwable) {
        val detail = error.message?.takeIf { it.isNotBlank() } ?: "蓝牙写入失败"
        publishUiState(connectionHint = "$operation 发送失败：$detail")
    }

    private fun publishJoinFailure(device: BluetoothDiscoveredDevice, error: Throwable) {
        val userMessage = errorMessageMapper.toUserFacingMessage(error, errorMessageMapper.connectFailedRetryMessage)
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
            .onFailure { error -> publishSendFailure("房间状态同步", error) }
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
    private suspend fun startHostTransportListening(hostDeviceName: String): Result<Unit> {
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
                roomTransport.shutdown()
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
        showRoomAiDifficultyDialog: Boolean = _roomUiState.value.showRoomAiDifficultyDialog,
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
            showRoomAiDifficultyDialog = showRoomAiDifficultyDialog,
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
        val session = ReconnectSession(
            hostAddress = hostAddress,
            hostDeviceName = hostDeviceName,
            participantId = participantId,
            roomName = roomName,
            savedAtMillis = System.currentTimeMillis(),
        )
        val persist = persistReconnectSessionAction ?: requireReconnectSessionRepository()::updateSession
        persist(session)
    }

    private fun requirePermissionChecker(): BluetoothPermissionChecker {
        return requireNotNull(permissionChecker) { "Bluetooth permission checker is not available" }
    }

    private fun requireDiscoverabilityController(): BluetoothDiscoverabilityController {
        return requireNotNull(discoverabilityController) { "Bluetooth discoverability controller is not available" }
    }

    private fun requireReconnectSessionRepository(): ReconnectSessionRepository {
        return requireNotNull(reconnectSessionRepository) { "Reconnect session repository is not available" }
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
        const val CONNECT_TIMEOUT_MS = 15_000L
        const val CONNECT_TIMEOUT_MESSAGE = "连接房主超时，请确认双方蓝牙已开启并靠近后重试"
        const val JOIN_TIMEOUT_MS = 8_000L
        const val JOIN_TIMEOUT_MESSAGE = "入房响应超时，请让房主重新开启房间后重试"
    }
}

internal interface BluetoothDiscoveryServicePort {
    val devices: StateFlow<List<BluetoothDiscoveredDevice>>
    val events: kotlinx.coroutines.flow.SharedFlow<BluetoothDiscoveryEvent>

    fun loadBondedDevices(): List<BluetoothDiscoveredDevice>

    fun getBondedDevices(): List<BluetoothDiscoveredDevice>

    fun startDiscovery(): Result<Unit>

    fun stopDiscovery()

    fun clearDiscoveredDevices()
}

private class AndroidBluetoothDiscoveryServicePort(
    private val discoveryService: BluetoothDiscoveryService,
) : BluetoothDiscoveryServicePort {
    override val devices: StateFlow<List<BluetoothDiscoveredDevice>> = discoveryService.devices
    override val events: kotlinx.coroutines.flow.SharedFlow<BluetoothDiscoveryEvent> = discoveryService.events

    override fun loadBondedDevices(): List<BluetoothDiscoveredDevice> = discoveryService.loadBondedDevices()

    override fun getBondedDevices(): List<BluetoothDiscoveredDevice> = discoveryService.getBondedDevices()

    override fun startDiscovery(): Result<Unit> = discoveryService.startDiscovery()

    override fun stopDiscovery() {
        discoveryService.stopDiscovery()
    }

    override fun clearDiscoveredDevices() {
        discoveryService.clearDiscoveredDevices()
    }
}

private data class BluetoothRoomRepositoryDependencies(
    val reconnectSessionRepository: ReconnectSessionRepository?,
    val scope: CoroutineScope,
    val permissionChecker: BluetoothPermissionChecker?,
    val discoveryService: BluetoothDiscoveryServicePort,
    val discoverabilityController: BluetoothDiscoverabilityController?,
    val roomTransport: RoomTransport,
    val persistReconnectSessionAction: (suspend (ReconnectSession) -> Unit)? = null,
)

private fun createRuntimeDependencies(
    appContext: Context,
    reconnectSessionRepository: ReconnectSessionRepository,
): BluetoothRoomRepositoryDependencies {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val permissionChecker = BluetoothPermissionChecker(appContext)
    val bluetoothAdapter = permissionChecker.requireBluetoothAdapter()
    val frameCodec = RoomFrameCodec()
    return BluetoothRoomRepositoryDependencies(
        reconnectSessionRepository = reconnectSessionRepository,
        scope = scope,
        permissionChecker = permissionChecker,
        discoveryService = AndroidBluetoothDiscoveryServicePort(
            BluetoothDiscoveryService(
                BluetoothDiscoveryManager(appContext, bluetoothAdapter),
            ),
        ),
        discoverabilityController = BluetoothDiscoverabilityController(permissionChecker),
        roomTransport = ClassicBluetoothTransport(
            bluetoothAdapter = bluetoothAdapter,
            frameCodec = frameCodec,
            scope = scope,
        ),
        persistReconnectSessionAction = reconnectSessionRepository::updateSession,
    )
}
