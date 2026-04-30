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
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import com.example.chudadi.controller.game.LocalGameAction
import com.example.chudadi.data.repository.ReconnectSession
import com.example.chudadi.data.repository.ReconnectSessionRepository
import com.example.chudadi.model.game.snapshot.MatchUiState
import com.example.chudadi.network.bluetooth.BluetoothPermissionUtils
import com.example.chudadi.network.game.GameWireMessage
import com.example.chudadi.ui.room.AiDifficulty
import com.example.chudadi.ui.room.BluetoothSearchState
import com.example.chudadi.ui.room.DiscoveredDeviceUiState
import com.example.chudadi.ui.room.GameRuleDisplay
import com.example.chudadi.ui.room.MemberConnectionStatus
import com.example.chudadi.ui.room.RoomMode
import com.example.chudadi.ui.room.RoomUiState
import com.example.chudadi.ui.room.SlotState
import com.example.chudadi.ui.room.SwapRequest
import java.io.IOException
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

class BluetoothRoomRepository(
    private val context: Context,
    private val reconnectSessionRepository: ReconnectSessionRepository,
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val bluetoothAdapter: BluetoothAdapter? =
        appContext.getSystemService(BluetoothManager::class.java)?.adapter
    private val frameCodec = RoomFrameCodec()
    private val discoveryManager = BluetoothDiscoveryManager(appContext, bluetoothAdapter)
    private val authorityStore = RoomAuthorityStore()
    private val socketManager = RoomSocketManager(bluetoothAdapter, frameCodec, scope)
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
        socketManager = socketManager,
        matchCoordinator = matchCoordinator,
        reconnectSessionRepository = reconnectSessionRepository,
        port = membershipPort,
    )
    private val seatCoordinator = RoomSeatCoordinator(
        authorityStore = authorityStore,
        socketManager = socketManager,
        localParticipantIdProvider = ::localParticipantId,
        port = seatPort,
    )

    private val _roomUiState = MutableStateFlow(RoomUiState())
    val roomUiState: StateFlow<RoomUiState> = _roomUiState.asStateFlow()
    val matchUiState: StateFlow<MatchUiState> = matchCoordinator.matchUiState
    private var roomRole: RoomRole = RoomRole.Idle

    init {
        scope.launch {
            discoveryManager.events.collect { event ->
                when (event) {
                    BluetoothDiscoveryEvent.DiscoveryFinished -> {
                        _roomUiState.update {
                            it.copy(
                                searchState = BluetoothSearchState.IDLE,
                                connectionHint = if (it.discoveredDevices.isEmpty()) {
                                    "未发现可连接房间，请确认房主已创建房间"
                                } else {
                                    "扫描完成，请选择要加入的房间"
                                },
                            )
                        }
                    }
                }
            }
        }
        scope.launch {
            socketManager.events.collect { event ->
                when (event) {
                    is RoomSocketEvent.IncomingConnection -> membershipCoordinator.handleIncomingConnection(event.connection)
                    is RoomSocketEvent.HostMessage -> handleHostSocketMessage(event.participantId, event.message)
                    is RoomSocketEvent.ClientMessage -> handleClientSocketMessage(event.message)
                    is RoomSocketEvent.ParticipantDisconnected -> membershipCoordinator.markParticipantDisconnected(event.participantId)
                    is RoomSocketEvent.HostConnectionLost -> membershipCoordinator.handleHostConnectionLost(event.reason)
                }
            }
        }
    }

    val isBluetoothSupported: Boolean
        get() = discoveryManager.isBluetoothSupported

    fun isBluetoothEnabled(): Boolean = discoveryManager.isBluetoothEnabled()

    fun hasBluetoothConnectPermission(): Boolean = discoveryManager.hasBluetoothConnectPermission()

    fun hasBluetoothScanPermission(): Boolean = discoveryManager.hasBluetoothScanPermission()

    @SuppressLint("MissingPermission")
    fun loadBondedDevices() {
        val devices = discoveryManager.loadBondedDevices().map { it.toUiState() }
        _roomUiState.update {
            it.copy(
                discoveredDevices = devices,
                connectionHint = if (devices.isEmpty()) {
                    "暂无已配对设备，请先让房主创建房间"
                } else {
                    "已加载已配对设备，请选择要加入的房间"
                },
            )
        }
    }

    @SuppressLint("MissingPermission")
    fun startDiscovery() {
        val adapter = bluetoothAdapter ?: run {
            _roomUiState.update {
                it.copy(searchState = BluetoothSearchState.FAILED, connectionHint = "当前设备不支持蓝牙")
            }
            return
        }
        if (!BluetoothPermissionUtils.hasScanPermission(appContext)) {
            _roomUiState.update {
                it.copy(searchState = BluetoothSearchState.FAILED, connectionHint = "缺少蓝牙扫描权限")
            }
            return
        }
        if (adapter.isDiscovering) {
            adapter.cancelDiscovery()
        }
        loadBondedDevices()
        _roomUiState.update {
            it.copy(searchState = BluetoothSearchState.SCANNING, connectionHint = "正在搜索蓝牙房间...")
        }
        discoveryManager.startDiscovery()
    }

    @SuppressLint("MissingPermission")
    fun getBondedDevices(): List<BluetoothDiscoveredDevice> = discoveryManager.getBondedDevices()

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
        bluetoothAdapter ?: return Result.failure(IllegalStateException("当前设备不支持蓝牙"))
        if (!BluetoothPermissionUtils.hasConnectPermission(appContext)) {
            return Result.failure(IllegalStateException("缺少蓝牙连接权限，无法创建房间"))
        }
        shutdownCurrentRole()
        matchCoordinator.reset()
        initializeHostRoom(
            playerName = playerName,
            avatarResId = avatarResId,
            hostDeviceName = hostDeviceName,
            bluetoothVisible = true,
        )
        return socketManager.startServerSynchronously(SERVICE_NAME, ROOM_UUID)
            .onSuccess {
                socketManager.launchHeartbeatLoop(
                    heartbeatIntervalMs = HEARTBEAT_INTERVAL_MS,
                    heartbeatTimeoutMs = HEARTBEAT_TIMEOUT_MS,
                )
                publishUiState(
                    connectionHint = "已开启蓝牙房间，等待成员加入",
                    roomMode = RoomMode.BLUETOOTH_HOST,
                )
            }
            .onFailure { error ->
                val userMessage = error.toUserFacingBluetoothMessage("蓝牙房间监听启动失败")
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

    fun enableBluetoothBroadcast(hostDeviceName: String): Result<Unit> {
        val result = when {
            roomRole !is RoomRole.Host -> {
                Result.failure(IllegalStateException("当前不是房主，无法开启广播"))
            }

            bluetoothAdapter == null -> {
                Result.failure(IllegalStateException("当前设备不支持蓝牙"))
            }

            !BluetoothPermissionUtils.hasConnectPermission(appContext) -> {
                Result.failure(IllegalStateException("缺少蓝牙连接权限，无法开启广播"))
            }

            else -> {
                authorityStore.update { state ->
                    state.copy(
                        hostDeviceName = hostDeviceName,
                        bluetoothVisible = true,
                    )
                }
                socketManager.startServerSynchronously(SERVICE_NAME, ROOM_UUID)
                    .onSuccess {
                        socketManager.launchHeartbeatLoop(
                            heartbeatIntervalMs = HEARTBEAT_INTERVAL_MS,
                            heartbeatTimeoutMs = HEARTBEAT_TIMEOUT_MS,
                        )
                        publishUiState(
                            connectionHint = "已开启蓝牙房间，等待成员加入",
                            roomMode = RoomMode.BLUETOOTH_HOST,
                        )
                    }
                    .onFailure { error ->
                        val userMessage = error.toUserFacingBluetoothMessage("蓝牙房间监听启动失败")
                        authorityStore.update { state -> state.copy(bluetoothVisible = false) }
                        publishUiState(
                            connectionHint = userMessage,
                            roomMode = RoomMode.LOCAL,
                        )
                    }
            }
        }
        return result
    }

    suspend fun connectToHost(
        device: BluetoothDiscoveredDevice,
        playerName: String,
        avatarResId: Int?,
        resumeParticipantId: String? = null,
    ): Result<Unit> {
        bluetoothAdapter ?: return Result.failure(IllegalStateException("设备不支持蓝牙"))
        if (!BluetoothPermissionUtils.hasConnectPermission(appContext)) {
            return Result.failure(IllegalStateException("缺少蓝牙连接权限"))
        }
        shutdownCurrentRole()
        _roomUiState.update {
            it.copy(
                searchState = BluetoothSearchState.CONNECTING,
                selectedDeviceAddress = device.address,
                connectionHint = "正在连接 ${device.name}...",
            )
        }
        discoveryManager.stopDiscoveryIfNeeded()
        return socketManager.connectToHost(device, ROOM_UUID)
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
                socketManager.attachClientReadLoop(connection)
                socketManager.startClientHeartbeatWatchdog(
                    heartbeatTimeoutMs = HEARTBEAT_TIMEOUT_MS,
                    clientHeartbeatCheckIntervalMs = CLIENT_HEARTBEAT_CHECK_INTERVAL_MS,
                )
            }
            .onFailure { error ->
                val userMessage = error.toUserFacingBluetoothMessage("连接失败，请重试")
                _roomUiState.update {
                    it.copy(
                        searchState = BluetoothSearchState.FAILED,
                        selectedDeviceAddress = device.address,
                        connectionHint = userMessage,
                        joinErrorMessage = userMessage,
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
                socketManager.sendToHost(RoomWireMessage.ReadyStateChangeMessage(nextReady))
            }

            RoomRole.Idle -> Unit
        }
    }

    fun startNetworkMatch() {
        if (roomRole !is RoomRole.Host || _roomUiState.value.roomMode != RoomMode.BLUETOOTH_HOST) return
        matchCoordinator.startNetworkMatch(
            authorityStore = authorityStore,
            localParticipantId = localParticipantId(),
            sendToParticipant = socketManager::sendToParticipant,
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
                matchCoordinator.onLocalGameActionAsClient(action, socketManager::sendToHost)
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
        socketManager.sendToParticipant(participantId, RoomWireMessage.RemovedFromRoom("你已被房主移出房间"))
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
        socketManager.disconnectParticipant(participantId)
    }

    fun handleSwapRequest(targetSlotIndex: Int) {
        when (val role = roomRole) {
            is RoomRole.Host -> seatCoordinator.handleHostSwapRequest(role.localParticipantId, targetSlotIndex)
            is RoomRole.Client -> {
                socketManager.sendToHost(RoomWireMessage.SwapSeatRequestMessage(targetSlotIndex))
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
                socketManager.sendToHost(
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
            is RoomRole.Client -> socketManager.sendToHost(RoomWireMessage.LeaveRoomMessage)
            is RoomRole.Host -> {
                socketManager.broadcast(RoomWireMessage.RoomClosedByHost("房主已关闭房间"))
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
        _roomUiState.update { it.copy(joinErrorMessage = null) }
    }

    fun showHomeNotice(message: String) {
        _roomUiState.update {
            it.copy(
                homeNoticeMessage = message,
                connectionHint = message,
            )
        }
    }

    fun showJoinError(message: String) {
        _roomUiState.update {
            it.copy(
                searchState = BluetoothSearchState.FAILED,
                connectionHint = message,
                joinErrorMessage = message,
            )
        }
    }

    @SuppressLint("MissingPermission")
    fun loadBondedDevicesWithFeedback() {
        if (!BluetoothPermissionUtils.hasConnectPermission(appContext)) {
            showJoinError("缺少蓝牙连接权限")
            return
        }
        loadBondedDevices()
        _roomUiState.update { it.copy(joinErrorMessage = null) }
    }

    @SuppressLint("MissingPermission")
    fun startDiscoveryWithFeedback() {
        bluetoothAdapter ?: run {
            showJoinError("当前设备不支持蓝牙")
            return
        }
        if (!BluetoothPermissionUtils.hasScanPermission(appContext)) {
            showJoinError("缺少蓝牙扫描权限")
            return
        }
        loadBondedDevicesWithFeedback()
        val started = discoveryManager.startDiscovery()
        if (!started) {
            showJoinError("蓝牙扫描启动失败，请确认蓝牙已开启且当前未被系统占用")
            return
        }
        _roomUiState.update {
            it.copy(
                searchState = BluetoothSearchState.SCANNING,
                connectionHint = "正在搜索蓝牙房间...",
                joinErrorMessage = null,
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
        discoveryManager.clear()
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
                    sendToParticipant = socketManager::sendToParticipant,
                    updateAllMatchSnapshots = ::updateAllMatchSnapshots,
                )
            }

            RoomWireMessage.LeaveRoomMessage -> membershipCoordinator.handleRemoteLeave(participantId)
            else -> Unit
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
            sendToParticipant = socketManager::sendToParticipant,
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
        _roomUiState.update {
            it.copy(
                slots = List(RoomAuthorityStore.SLOT_COUNT) { index -> SlotState(slotIndex = index) },
                bluetoothVisible = false,
                roomMode = RoomMode.LOCAL,
                canStartLocalGame = false,
                canStartNetworkGame = false,
                canEnableBroadcast = false,
                canManageAiSeats = false,
            )
        }
    }

    private fun broadcastSnapshot() {
        if (roomRole !is RoomRole.Host) return
        socketManager.broadcast(RoomWireMessage.RoomSnapshotMessage(snapshotOfCurrentRoom()))
    }

    private fun snapshotOfCurrentRoom(): RemoteRoomSnapshot {
        return authorityStore.snapshotOfCurrentRoom(
            connectionHint = _roomUiState.value.connectionHint,
            localParticipantId = localParticipantId(),
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
        val slots = authorityStore.buildSlotStates(localParticipantId = localParticipantId)
        val canStart = authorityStore.canStart(localParticipantId)
        _roomUiState.value = RoomUiState(
            isHost = isHost,
            roomMode = roomMode,
            roomName = authorityStore.state.roomName,
            hostDeviceName = authorityStore.state.hostDeviceName,
            currentRule = authorityStore.state.currentRule,
            slots = slots,
            bluetoothVisible = authorityStore.state.bluetoothVisible && roomMode != RoomMode.LOCAL,
            connectionHint = connectionHint ?: _roomUiState.value.connectionHint,
            homeNoticeMessage = _roomUiState.value.homeNoticeMessage,
            discoveredDevices = discoveryManager.devices.value.map { it.toUiState() },
            searchState = searchState,
            selectedDeviceAddress = selectedDeviceAddress,
            canStartGame = canStart,
            canStartLocalGame = canStart && roomMode == RoomMode.LOCAL && isHost,
            canStartNetworkGame = canStart && roomMode == RoomMode.BLUETOOTH_HOST && isHost,
            canEnableBroadcast = roomMode == RoomMode.LOCAL && isHost,
            canManageAiSeats = isHost && roomMode != RoomMode.BLUETOOTH_CLIENT,
            pendingSwapRequest = pendingSwapRequest,
            removedFromRoom = _roomUiState.value.removedFromRoom,
            roomClosedByHost = _roomUiState.value.roomClosedByHost,
            joinErrorMessage = _roomUiState.value.joinErrorMessage,
            showAiDifficultyDialog = showAiDifficultyDialog,
            aiDialogTargetSlot = aiDialogTargetSlot,
            showSlotActionMenu = showSlotActionMenu,
            slotActionMenuTarget = slotActionMenuTarget,
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
        socketManager.shutdown()
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

    private fun Throwable.toUserFacingBluetoothMessage(defaultMessage: String): String {
        val rawMessage = message?.trim().orEmpty()
        if (rawMessage.isChineseText()) {
            return rawMessage
        }

        return when {
            this is SecurityException -> "缺少蓝牙权限，请授权后重试"

            this is IllegalArgumentException &&
                rawMessage.contains("address", ignoreCase = true) -> {
                "蓝牙设备地址无效，请重新搜索房间"
            }

            this is IOException && rawMessage.containsAnyIgnoreCase(
                "read failed",
                "socket closed",
                "bt socket closed",
                "broken pipe",
                "connection reset",
                "software caused connection abort",
            ) -> {
                "蓝牙连接已断开，请确认双方设备蓝牙状态后重试"
            }

            this is IOException && rawMessage.containsAnyIgnoreCase(
                "timed out",
                "timeout",
            ) -> {
                "蓝牙连接超时，请确认双方设备距离和蓝牙状态后重试"
            }

            this is IOException && rawMessage.containsAnyIgnoreCase(
                "service discovery failed",
                "connection refused",
                "connection failure",
            ) -> {
                "蓝牙连接失败，请确认房主已开启房间后重试"
            }

            rawMessage.isNotBlank() && rawMessage.isChineseText() -> rawMessage
            else -> defaultMessage
        }
    }

    private fun String.containsAnyIgnoreCase(vararg keywords: String): Boolean {
        return keywords.any { keyword -> contains(keyword, ignoreCase = true) }
    }

    private fun String.isChineseText(): Boolean {
        return any { it in '\u4E00'..'\u9FFF' }
    }

    private fun BluetoothDiscoveredDevice.toUiState(): DiscoveredDeviceUiState {
        return DiscoveredDeviceUiState(
            name = name,
            address = address,
            isBonded = isBonded,
        )
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
