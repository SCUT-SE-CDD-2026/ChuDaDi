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
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.example.chudadi.R
import com.example.chudadi.ai.rulebased.RuleBasedAiPlayer
import com.example.chudadi.controller.client.BluetoothRemoteMatchController
import com.example.chudadi.controller.game.LocalGameAction
import com.example.chudadi.controller.game.MatchUiStateMapper
import com.example.chudadi.controller.server.BluetoothAuthoritativeMatchController
import com.example.chudadi.data.repository.ReconnectSession
import com.example.chudadi.data.repository.ReconnectSessionRepository
import com.example.chudadi.model.game.engine.GameEngine
import com.example.chudadi.model.game.entity.MatchPhase
import com.example.chudadi.model.game.entity.SeatControllerType
import com.example.chudadi.model.game.rule.GameRuleSet
import com.example.chudadi.model.game.snapshot.MatchUiState
import com.example.chudadi.network.game.GameWireMessage
import com.example.chudadi.network.bluetooth.BluetoothPermissionUtils
import com.example.chudadi.ui.room.AiDifficulty
import com.example.chudadi.ui.room.BluetoothSearchState
import com.example.chudadi.ui.room.DiscoveredDeviceUiState
import com.example.chudadi.ui.room.GameRuleDisplay
import com.example.chudadi.ui.room.MemberConnectionStatus
import com.example.chudadi.ui.room.RoomMatchRules
import com.example.chudadi.ui.room.RoomUiState
import com.example.chudadi.ui.room.SlotOccupantType
import com.example.chudadi.ui.room.SlotState
import com.example.chudadi.ui.room.SwapRequest
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.util.UUID
import com.example.chudadi.network.game.toRemoteMatchSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class BluetoothDiscoveredDevice(
    val name: String,
    val address: String,
    val isBonded: Boolean,
)

private data class ParticipantRecord(
    val participantId: String,
    val occupantType: SlotOccupantType,
    val displayName: String,
    val avatarResId: Int?,
    val connectionStatus: MemberConnectionStatus?,
    val aiDifficulty: AiDifficulty? = null,
    val cumulativeScore: Int = 0,
)

private data class RoomConnection(
    val participantId: String,
    val connection: RoomSocketConnection,
    var lastHeartbeatAt: Long = System.currentTimeMillis(),
)

private sealed interface RoomRole {
    data object Idle : RoomRole

    data class Host(
        val localParticipantId: String,
        val hostDeviceName: String,
    ) : RoomRole

    data class Client(
        val localParticipantId: String,
        val hostDeviceName: String,
        val connection: RoomSocketConnection,
    ) : RoomRole
}

private data class RoomAuthorityState(
    val roomName: String = "",
    val hostDeviceName: String = "",
    val currentRule: GameRuleDisplay = GameRuleDisplay.SOUTHERN,
    val bluetoothVisible: Boolean = false,
    val participants: Map<String, ParticipantRecord> = emptyMap(),
    val slotAssignments: Map<Int, String?> = emptySlotAssignments(),
)

private const val DEFAULT_SLOT_COUNT = 4

private fun emptySlotAssignments(): Map<Int, String?> {
    return (0 until DEFAULT_SLOT_COUNT).associate { it to null }
}

class BluetoothRoomRepository(
    private val context: Context,
    private val reconnectSessionRepository: ReconnectSessionRepository,
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val frameCodec = RoomFrameCodec()
    private val engine = GameEngine()
    private val matchMapper = MatchUiStateMapper(engine)
    private val aiPlayer = RuleBasedAiPlayer()
    private val hostMatchController = BluetoothAuthoritativeMatchController(engine, matchMapper, aiPlayer)
    private val localMatchController = BluetoothRemoteMatchController()

    private val participantConnections = linkedMapOf<String, RoomConnection>()

    private val _roomUiState = MutableStateFlow(RoomUiState())
    val roomUiState: StateFlow<RoomUiState> = _roomUiState.asStateFlow()
    val matchUiState: StateFlow<MatchUiState> = localMatchController.uiState

    private var roomRole: RoomRole = RoomRole.Idle
    private var authorityState = RoomAuthorityState()
    private var serverSocket: BluetoothServerSocket? = null
    private var acceptJob: Job? = null
    private var heartbeatJob: Job? = null
    private var clientHeartbeatJob: Job? = null
    private var matchLoopJob: Job? = null
    private var lastHostHeartbeatAt: Long = 0L
    private var activeMatchSeatAssignments: Map<String, Int> = emptyMap()
    private var currentHostAddress: String? = null
    private var discoveryReceiverRegistered = false

    private val discoveryReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return
                        addDiscoveredDevice(device = device, isBonded = false)
                    }

                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
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

    val isBluetoothSupported: Boolean
        get() = bluetoothAdapter != null

    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

    fun hasBluetoothConnectPermission(): Boolean = BluetoothPermissionUtils.hasConnectPermission(appContext)

    fun hasBluetoothScanPermission(): Boolean = BluetoothPermissionUtils.hasScanPermission(appContext)

    @SuppressLint("MissingPermission")
    fun loadBondedDevices() {
        val devices = getBondedDevices().map { it.toUiState() }
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
        registerDiscoveryReceiverIfNeeded()
        if (adapter.isDiscovering) {
            adapter.cancelDiscovery()
        }
        loadBondedDevices()
        _roomUiState.update {
            it.copy(searchState = BluetoothSearchState.SCANNING, connectionHint = "正在搜索蓝牙房间...")
        }
        adapter.startDiscovery()
    }

    @SuppressLint("MissingPermission")
    fun getBondedDevices(): List<BluetoothDiscoveredDevice> {
        val adapter = bluetoothAdapter ?: return emptyList()
        if (!BluetoothPermissionUtils.hasConnectPermission(appContext)) {
            return emptyList()
        }
        return adapter.bondedDevices.orEmpty()
            .sortedBy { it.name.orEmpty() }
            .map { device ->
                BluetoothDiscoveredDevice(
                    name = device.name.orEmpty().ifBlank { "未知设备" },
                    address = device.address,
                    isBonded = true,
                )
            }
    }

    fun createHostRoom(
        playerName: String,
        avatarResId: Int?,
        hostDeviceName: String,
    ) {
        shutdownCurrentRole()
        localMatchController.reset()
        roomRole = RoomRole.Host(
            localParticipantId = HOST_PARTICIPANT_ID,
            hostDeviceName = hostDeviceName,
        )
        currentHostAddress = null
        participantConnections.clear()
        authorityState = RoomAuthorityState(
            roomName = "$playerName 的房间",
            hostDeviceName = hostDeviceName,
            currentRule = GameRuleDisplay.SOUTHERN,
            bluetoothVisible = true,
            participants = mapOf(
                HOST_PARTICIPANT_ID to ParticipantRecord(
                    participantId = HOST_PARTICIPANT_ID,
                    occupantType = SlotOccupantType.HUMAN_HOST,
                    displayName = playerName,
                    avatarResId = avatarResId ?: R.drawable.avatar,
                    connectionStatus = MemberConnectionStatus.READY,
                ),
            ),
            slotAssignments = (0 until SLOT_COUNT).associateWith { slotIndex ->
                if (slotIndex == 0) HOST_PARTICIPANT_ID else null
            },
        )
        publishUiState(connectionHint = "已开启蓝牙房间，等待成员加入")
        startServer()
    }

    suspend fun connectToHost(
        device: BluetoothDiscoveredDevice,
        playerName: String,
        avatarResId: Int?,
        resumeParticipantId: String? = null,
    ): Result<Unit> {
        val adapter = bluetoothAdapter ?: return Result.failure(IllegalStateException("设备不支持蓝牙"))
        if (!BluetoothPermissionUtils.hasConnectPermission(appContext)) {
            return Result.failure(IllegalStateException("缺少蓝牙连接权限"))
        }
        return withContext(Dispatchers.IO) {
            runCatching {
                shutdownCurrentRole()
                _roomUiState.update {
                    it.copy(
                        searchState = BluetoothSearchState.CONNECTING,
                        selectedDeviceAddress = device.address,
                        connectionHint = "正在连接 ${device.name}...",
                    )
                }
                stopDiscoveryIfNeeded(adapter)
                val remoteDevice = adapter.getRemoteDevice(device.address)
                val socket = remoteDevice.createRfcommSocketToServiceRecord(ROOM_UUID)
                socket.connect()
                val connection = RoomSocketConnection(socket = socket, frameCodec = frameCodec)
                val accepted = connection.awaitJoinAccepted(
                    playerName = playerName,
                    avatarResId = avatarResId,
                    resumeParticipantId = resumeParticipantId,
                )
                roomRole = RoomRole.Client(
                    localParticipantId = accepted.localParticipantId,
                    hostDeviceName = accepted.snapshot.hostDeviceName,
                    connection = connection,
                )
                currentHostAddress = device.address
                lastHostHeartbeatAt = System.currentTimeMillis()
                authorityState = accepted.snapshot.toAuthorityState()
                publishUiState(
                    connectionHint = accepted.snapshot.connectionHint,
                    localParticipantId = accepted.localParticipantId,
                    isHost = false,
                    searchState = BluetoothSearchState.IDLE,
                    selectedDeviceAddress = device.address,
                )
                persistReconnectSession(
                    participantId = accepted.localParticipantId,
                    hostAddress = device.address,
                    hostDeviceName = accepted.snapshot.hostDeviceName,
                    roomName = accepted.snapshot.roomName,
                )
                startClientReadLoop(connection)
                startClientHeartbeatWatchdog()
            }.onFailure { error ->
                _roomUiState.update {
                    it.copy(
                        searchState = BluetoothSearchState.FAILED,
                        selectedDeviceAddress = device.address,
                        connectionHint = error.message ?: "连接失败，请重试",
                        joinErrorMessage = error.message,
                    )
                }
            }
        }
    }

    suspend fun tryReconnectLastSession(
        playerName: String,
        avatarResId: Int?,
        session: ReconnectSession,
    ): Result<Unit> {
        val result = connectToHost(
            device = BluetoothDiscoveredDevice(
                name = session.hostDeviceName,
                address = session.hostAddress,
                isBonded = true,
            ),
            playerName = playerName,
            avatarResId = avatarResId,
            resumeParticipantId = session.participantId,
        )
        return result
    }

    fun handleToggleReady() {
        when (val role = roomRole) {
            is RoomRole.Host -> toggleReadyForParticipant(role.localParticipantId)
            is RoomRole.Client -> {
                val currentSlot = _roomUiState.value.slots.firstOrNull { it.isLocalPlayer } ?: return
                val nextReady = currentSlot.connectionStatus != MemberConnectionStatus.READY
                scope.launch {
                    role.connection.send(RoomWireMessage.ReadyStateChangeMessage(nextReady))
                }
            }
            RoomRole.Idle -> Unit
        }
    }

    fun startNetworkMatch() {
        if (roomRole !is RoomRole.Host) return
        activeMatchSeatAssignments = buildActiveMatchSeatAssignments()
        val seatConfigs = buildSeatConfigs()
        hostMatchController.startMatch(
            seatConfigs = seatConfigs,
            ruleSet = authorityState.currentRule.toGameRuleSet(),
        )
        val hostSeatId = localMatchSeatId(localParticipantId()) ?: return
        val hostSnapshot = hostMatchController.buildSnapshotForSeat(localSeatId = hostSeatId)
        localMatchController.onMatchStarted(
            GameWireMessage.MatchStarted(
                localSeatId = hostSeatId,
                snapshot = hostSnapshot.toRemoteMatchSnapshot(hostMatchController.currentMatch()?.matchId.orEmpty()),
            ),
        )
        activeMatchSeatAssignments.forEach { (participantId, seatId) ->
            if (participantId == HOST_PARTICIPANT_ID) return@forEach
            val connection = participantConnections[participantId] ?: return@forEach
            connection.connection.sendSafely(
                RoomWireMessage.GameEnvelope(
                    hostMatchController.buildMatchStartedMessage(localSeatId = seatId),
                ),
            )
        }
        startMatchLoop()
    }

    fun onLocalGameAction(action: LocalGameAction) {
        when (val role = roomRole) {
            is RoomRole.Host -> handleHostGameAction(action)
            is RoomRole.Client -> handleClientGameAction(role, action)
            RoomRole.Idle -> Unit
        }
    }

    fun handleToggleRule() {
        if (roomRole !is RoomRole.Host) return
        authorityState = authorityState.copy(
            currentRule = if (authorityState.currentRule == GameRuleDisplay.SOUTHERN) {
                GameRuleDisplay.NORTHERN
            } else {
                GameRuleDisplay.SOUTHERN
            },
        )
        publishUiState(connectionHint = "房主已切换为${authorityState.currentRule.label}")
        broadcastSnapshot()
    }

    fun handleAddAiToSlot(slotIndex: Int, difficulty: AiDifficulty) {
        if (roomRole !is RoomRole.Host) return
        if (occupantAt(slotIndex) != null) return
        val aiId = nextAiParticipantId()
        authorityState = authorityState.copy(
            participants = authorityState.participants + (
                aiId to ParticipantRecord(
                    participantId = aiId,
                    occupantType = SlotOccupantType.AI,
                    displayName = nextAiDisplayName(difficulty),
                    avatarResId = R.drawable.avatar,
                    connectionStatus = MemberConnectionStatus.READY,
                    aiDifficulty = difficulty,
                )
            ),
            slotAssignments = authorityState.slotAssignments + (slotIndex to aiId),
        )
        publishUiState(
            connectionHint = "已更新房间成员",
            showAiDifficultyDialog = false,
            aiDialogTargetSlot = -1,
        )
        broadcastSnapshot()
    }

    fun handleRemoveSlotOccupant(slotIndex: Int) {
        if (roomRole !is RoomRole.Host) return
        val participantId = occupantAt(slotIndex) ?: return
        if (participantId == HOST_PARTICIPANT_ID) return
        val removedConnection = participantConnections[participantId]?.connection
        removedConnection?.sendSafely(RoomWireMessage.RemovedFromRoom("你已被房主移出房间"))
        authorityState = authorityState.copy(
            participants = authorityState.participants - participantId,
            slotAssignments = authorityState.slotAssignments + (slotIndex to null),
        )
        publishUiState(
            connectionHint = "已移除位置 ${slotIndex + 1} 的成员",
            showSlotActionMenu = false,
            slotActionMenuTarget = -1,
            pendingSwapRequest = null,
        )
        broadcastSnapshot()
        participantConnections.remove(participantId)?.connection?.close()
    }

    fun handleSwapRequest(targetSlotIndex: Int) {
        when (val role = roomRole) {
            is RoomRole.Host -> handleHostSwapRequest(role.localParticipantId, targetSlotIndex)
            is RoomRole.Client -> {
                scope.launch {
                    role.connection.send(RoomWireMessage.SwapSeatRequestMessage(targetSlotIndex))
                }
            }
            RoomRole.Idle -> Unit
        }
    }

    fun handleSwapDecision(request: SwapRequest, accepted: Boolean) {
        when (val role = roomRole) {
            is RoomRole.Host -> {
                if (accepted) {
                    applySeatSwap(request.requesterSlotIndex, request.targetSlotIndex)
                } else {
                    notifyRequesterSwapRejected(request.requesterSlotIndex)
                    publishUiState(pendingSwapRequest = null, connectionHint = "已拒绝换位请求")
                }
            }
            is RoomRole.Client -> {
                _roomUiState.update { it.copy(pendingSwapRequest = null) }
                scope.launch {
                    role.connection.send(
                        RoomWireMessage.SwapSeatDecisionMessage(
                            requesterSlotIndex = request.requesterSlotIndex,
                            targetSlotIndex = request.targetSlotIndex,
                            accepted = accepted,
                        ),
                    )
                }
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
        when (val role = roomRole) {
            is RoomRole.Client -> role.connection.sendSafely(RoomWireMessage.LeaveRoomMessage)
            is RoomRole.Host -> {
                participantConnections.values.forEach { roomConnection ->
                    roomConnection.connection.sendSafely(
                        RoomWireMessage.RoomClosedByHost("房主已关闭房间"),
                    )
                }
            }
            RoomRole.Idle -> Unit
        }
        localMatchController.reset()
        shutdownCurrentRole()
        authorityState = RoomAuthorityState()
        _roomUiState.value = RoomUiState(homeNoticeMessage = _roomUiState.value.homeNoticeMessage)
        scope.launch { reconnectSessionRepository.clearSession() }
    }

    fun exitMatchToRoom() {
        localMatchController.reset()
        clearCurrentNetworkMatch()
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

    fun resetRoomScores() {
        if (roomRole !is RoomRole.Host) return
        authorityState = authorityState.copy(
            participants = authorityState.participants.mapValues { (_, participant) ->
                participant.copy(cumulativeScore = 0)
            },
        )
        publishUiState(connectionHint = "已重置房间分数")
        broadcastSnapshot()
    }

    private fun startServer() {
        val adapter = bluetoothAdapter ?: return
        if (!BluetoothPermissionUtils.hasConnectPermission(appContext)) {
            _roomUiState.update { it.copy(connectionHint = "缺少蓝牙连接权限，无法开启房间") }
            return
        }
        acceptJob?.cancel()
        heartbeatJob?.cancel()
        scope.launch {
            try {
                @SuppressLint("MissingPermission")
                val socket = adapter.listenUsingRfcommWithServiceRecord(SERVICE_NAME, ROOM_UUID)
                serverSocket = socket
                acceptJob = launchAcceptLoop(socket)
                heartbeatJob = launchHeartbeatLoop()
            } catch (_: IOException) {
                _roomUiState.update { it.copy(connectionHint = "蓝牙房间监听启动失败") }
            }
        }
    }

    private fun launchAcceptLoop(socket: BluetoothServerSocket): Job {
        return scope.launch {
            while (isActive) {
                try {
                    val clientSocket = socket.accept() ?: continue
                    val connection = RoomSocketConnection(clientSocket, frameCodec)
                    launch { handleIncomingConnection(connection) }
                } catch (_: IOException) {
                    break
                }
            }
        }
    }

    private fun launchHeartbeatLoop(): Job {
        return scope.launch {
            while (isActive) {
                delay(HEARTBEAT_INTERVAL_MS)
                val expired = participantConnections.values.filter {
                    System.currentTimeMillis() - it.lastHeartbeatAt > HEARTBEAT_TIMEOUT_MS
                }
                expired.forEach { connection -> markParticipantDisconnected(connection.participantId) }
                participantConnections.values.forEach { connection ->
                    connection.connection.sendSafely(RoomWireMessage.HeartbeatPing)
                }
            }
        }
    }

    private fun startMatchLoop() {
        matchLoopJob?.cancel()
        matchLoopJob = scope.launch {
            while (isActive) {
                delay(MATCH_LOOP_INTERVAL_MS)
                val match = hostMatchController.currentMatch() ?: continue
                if (match.phase == MatchPhase.FINISHED) {
                    break
                }
                if (!hostMatchController.isCurrentTurnExpired()) {
                    updateAllMatchSnapshots(lastActionMessage = null)
                    continue
                }
                val seatId = match.activeSeatIndex
                val seatName = seatDisplayName(seatId)
                val result = when {
                    hostMatchController.isCurrentActorAi() -> {
                        val thinkingMessage = if (isSeatDisconnected(seatId)) "$seatName 托管中" else "$seatName 思考中"
                        hostMatchController.resolveCurrentSeatByAi(thinkingMessage)
                    }
                    isSeatDisconnected(seatId) -> {
                        hostMatchController.markDisconnected(seatId, disconnected = true)
                        updateAllMatchSnapshots(lastActionMessage = "$seatName 托管中")
                        continue
                    }
                    hostMatchController.canCurrentSeatPass() -> {
                        val passResult = hostMatchController.handlePassRequest(seatId)
                        passResult.copy(message = if (passResult.success) "$seatName 超时过牌" else passResult.message)
                    }
                    else -> {
                        hostMatchController.resolveCurrentSeatByAi("$seatName 超时，系统已代出")
                    }
                }
                if (result.success || result.message != null) {
                    updateAllMatchSnapshots(lastActionMessage = result.message)
                }
            }
        }
    }

    private suspend fun handleIncomingConnection(connection: RoomSocketConnection) {
        try {
            when (val firstMessage = connection.read()) {
                is RoomWireMessage.JoinRoomRequest -> handleJoinRequest(connection, firstMessage)
                else -> connection.close()
            }
        } catch (_: IOException) {
            connection.close()
        }
    }

    private suspend fun handleJoinRequest(
        connection: RoomSocketConnection,
        request: RoomWireMessage.JoinRoomRequest,
    ) {
        val participantId = request.resumeParticipantId
            ?.takeIf { authorityState.participants.containsKey(it) }
            ?: participantIdForConnection(connection)
        val existingParticipant = authorityState.participants[participantId]
        val existingSlotIndex = slotIndexOfParticipant(participantId)

        if (existingParticipant != null && existingSlotIndex != null) {
            participantConnections.remove(participantId)?.connection?.close()
            participantConnections[participantId] = RoomConnection(
                participantId = participantId,
                connection = connection,
            )
            authorityState = authorityState.copy(
                slotAssignments = normalizedSlotAssignments(participantId = participantId, keepSlotIndex = existingSlotIndex),
            )
            updateParticipant(participantId) {
                it.copy(
                    displayName = request.playerName,
                    avatarResId = request.avatarResId ?: R.drawable.avatar,
                    connectionStatus = participantReconnectStatus(participantId),
                )
            }
            hostMatchController.onSeatReconnected(matchSeatIdOfParticipant(participantId) ?: existingSlotIndex)
            publishUiState(connectionHint = "${request.playerName} 已重新连接")
            connection.send(
                RoomWireMessage.JoinRoomAccepted(
                    localParticipantId = participantId,
                    snapshot = snapshotOfCurrentRoom(),
                ),
            )
            sendMatchRecoveryMessage(participantId)
            broadcastSnapshot()
            startHostReadLoop(participantId = participantId, connection = connection)
            return
        }

        val targetSlotIndex = firstAvailableRemoteSlotIndex()
        if (targetSlotIndex == null) {
            connection.send(RoomWireMessage.JoinRoomRejected("房间已满，请等待房主调整座位"))
            connection.close()
            return
        }
        participantConnections[participantId] = RoomConnection(
            participantId = participantId,
            connection = connection,
        )
        authorityState = authorityState.copy(
            participants = authorityState.participants + (
                participantId to ParticipantRecord(
                    participantId = participantId,
                    occupantType = SlotOccupantType.HUMAN_MEMBER,
                    displayName = request.playerName,
                    avatarResId = request.avatarResId ?: R.drawable.avatar,
                    connectionStatus = MemberConnectionStatus.CONNECTED,
                )
            ),
            slotAssignments = authorityState.slotAssignments + (targetSlotIndex to participantId),
        )
        publishUiState(connectionHint = "${request.playerName} 已加入房间")
        connection.send(
            RoomWireMessage.JoinRoomAccepted(
                localParticipantId = participantId,
                snapshot = snapshotOfCurrentRoom(),
            ),
        )
        broadcastSnapshot()
        startHostReadLoop(participantId = participantId, connection = connection)
    }

    private fun startHostReadLoop(participantId: String, connection: RoomSocketConnection) {
        scope.launch {
            try {
                while (isActive) {
                    when (val message = connection.read()) {
                        is RoomWireMessage.ReadyStateChangeMessage -> setRemoteReady(participantId, message.ready)
                        is RoomWireMessage.SwapSeatRequestMessage -> handleHostSwapRequest(participantId, message.targetSlotIndex)
                        is RoomWireMessage.SwapSeatDecisionMessage -> handleRemoteSwapDecision(message)
                        is RoomWireMessage.GameEnvelope -> handleHostGameEnvelope(participantId, message.message)
                        RoomWireMessage.LeaveRoomMessage -> {
                            handleRemoteLeave(participantId)
                            break
                        }
                        RoomWireMessage.HeartbeatPong -> {
                            participantConnections[participantId]?.lastHeartbeatAt = System.currentTimeMillis()
                        }
                        else -> Unit
                    }
                }
            } catch (_: IOException) {
                markParticipantDisconnected(participantId)
            }
        }
    }

    private fun startClientReadLoop(connection: RoomSocketConnection) {
        scope.launch {
            try {
                while (isActive) {
                    when (val message = connection.read()) {
                        is RoomWireMessage.RoomSnapshotMessage -> {
                            val localParticipantId =
                                (roomRole as? RoomRole.Client)?.localParticipantId ?: return@launch
                            authorityState = message.snapshot.toAuthorityState()
                            publishUiState(
                                connectionHint = message.snapshot.connectionHint,
                                localParticipantId = localParticipantId,
                                isHost = false,
                            )
                        }
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
                        RoomWireMessage.HeartbeatPing -> {
                            lastHostHeartbeatAt = System.currentTimeMillis()
                            connection.sendSafely(RoomWireMessage.HeartbeatPong)
                        }
                        is RoomWireMessage.JoinRoomRejected -> {
                            _roomUiState.update {
                                it.copy(
                                    connectionHint = message.reason,
                                    joinErrorMessage = message.reason,
                                    searchState = BluetoothSearchState.FAILED,
                                )
                            }
                        }
                        is RoomWireMessage.RemovedFromRoom -> {
                            scope.launch { reconnectSessionRepository.clearSession() }
                            _roomUiState.update {
                                it.copy(
                                    connectionHint = message.reason,
                                    removedFromRoom = true,
                                    homeNoticeMessage = message.reason,
                                )
                            }
                            localMatchController.reset()
                            shutdownCurrentRole()
                            authorityState = RoomAuthorityState()
                            _roomUiState.update {
                                it.copy(
                                    slots = List(SLOT_COUNT) { index -> SlotState(slotIndex = index) },
                                    bluetoothVisible = false,
                                )
                            }
                            break
                        }
                        is RoomWireMessage.RoomClosedByHost -> {
                            scope.launch { reconnectSessionRepository.clearSession() }
                            _roomUiState.update {
                                it.copy(
                                    connectionHint = message.reason,
                                    roomClosedByHost = true,
                                    homeNoticeMessage = message.reason,
                                )
                            }
                            localMatchController.reset()
                            shutdownCurrentRole()
                            authorityState = RoomAuthorityState()
                            _roomUiState.update {
                                it.copy(
                                    slots = List(SLOT_COUNT) { index -> SlotState(slotIndex = index) },
                                    bluetoothVisible = false,
                                )
                            }
                            break
                        }
                        is RoomWireMessage.GameEnvelope -> handleClientGameEnvelope(message.message)
                        else -> Unit
                    }
                }
            } catch (_: IOException) {
                handleHostConnectionLost("与房主连接中断，房间已关闭")
            }
        }
    }

    private fun handleHostGameAction(action: LocalGameAction) {
        when (action) {
            is LocalGameAction.ToggleCardSelection -> localMatchController.onToggleCardSelection(action.cardId)
            LocalGameAction.ClearSelection -> localMatchController.onClearSelection()
            LocalGameAction.SubmitSelectedCards -> {
                val selected = localMatchController.selectedCardIds()
                val result = hostMatchController.handlePlayRequest(
                    seatId = localMatchController.localSeatId(),
                    selectedCardIds = selected,
                )
                if (!result.success) {
                    localMatchController.onActionRejected(result.message ?: "出牌失败")
                    return
                }
                updateAllMatchSnapshots(lastActionMessage = result.message)
            }
            LocalGameAction.PassTurn -> {
                val result = hostMatchController.handlePassRequest(localMatchController.localSeatId())
                if (!result.success) {
                    localMatchController.onActionRejected(result.message ?: "过牌失败")
                    return
                }
                updateAllMatchSnapshots(lastActionMessage = result.message)
            }
            LocalGameAction.ExitToHome -> exitMatchToRoom()
            LocalGameAction.RestartMatch -> Unit
            is LocalGameAction.StartLocalMatch -> Unit
        }
    }

    private fun handleClientGameAction(role: RoomRole.Client, action: LocalGameAction) {
        when (action) {
            is LocalGameAction.ToggleCardSelection -> localMatchController.onToggleCardSelection(action.cardId)
            LocalGameAction.ClearSelection -> localMatchController.onClearSelection()
            LocalGameAction.SubmitSelectedCards -> {
                role.connection.sendSafely(
                    RoomWireMessage.GameEnvelope(
                        GameWireMessage.PlayCardsRequest(localMatchController.selectedCardIds()),
                    ),
                )
            }
            LocalGameAction.PassTurn -> {
                role.connection.sendSafely(RoomWireMessage.GameEnvelope(GameWireMessage.PassRequest))
            }
            LocalGameAction.ExitToHome -> exitMatchToRoom()
            LocalGameAction.RestartMatch -> Unit
            is LocalGameAction.StartLocalMatch -> Unit
        }
    }

    private fun handleClientGameEnvelope(message: GameWireMessage) {
        when (message) {
            is GameWireMessage.MatchStarted -> localMatchController.onMatchStarted(message)
            is GameWireMessage.MatchSnapshotMessage -> localMatchController.onSnapshot(message.snapshot)
            is GameWireMessage.ActionRejected -> localMatchController.onActionRejected(message.message)
            is GameWireMessage.MatchClosed -> {
                localMatchController.reset()
                handleHostConnectionLost(message.reason)
            }
            is GameWireMessage.PassRequest,
            is GameWireMessage.PlayCardsRequest,
            -> Unit
        }
    }

    private fun handleHostGameEnvelope(participantId: String, message: GameWireMessage) {
        val seatId = matchSeatIdOfParticipant(participantId) ?: return
        when (message) {
            is GameWireMessage.PlayCardsRequest -> {
                val result = hostMatchController.handlePlayRequest(seatId, message.selectedCardIds)
                if (!result.success) {
                    participantConnections[participantId]?.connection?.sendSafely(
                        RoomWireMessage.GameEnvelope(
                            GameWireMessage.ActionRejected(result.message ?: "出牌失败"),
                        ),
                    )
                    return
                }
                updateAllMatchSnapshots(lastActionMessage = result.message)
            }
            GameWireMessage.PassRequest -> {
                val result = hostMatchController.handlePassRequest(seatId)
                if (!result.success) {
                    participantConnections[participantId]?.connection?.sendSafely(
                        RoomWireMessage.GameEnvelope(
                            GameWireMessage.ActionRejected(result.message ?: "过牌失败"),
                        ),
                    )
                    return
                }
                updateAllMatchSnapshots(lastActionMessage = result.message)
            }
            is GameWireMessage.MatchStarted,
            is GameWireMessage.MatchSnapshotMessage,
            is GameWireMessage.ActionRejected,
            is GameWireMessage.MatchClosed,
            -> Unit
        }
    }

    private fun updateAllMatchSnapshots(lastActionMessage: String?) {
        val match = hostMatchController.currentMatch() ?: return
        val hostSeatId = localMatchSeatId(localParticipantId()) ?: return
        localMatchController.onSnapshot(
            hostMatchController.buildSnapshotMessage(localSeatId = hostSeatId, lastActionMessage = lastActionMessage).snapshot,
        )
        activeMatchSeatAssignments.forEach { (participantId, seatId) ->
            if (participantId == HOST_PARTICIPANT_ID) return@forEach
            participantConnections[participantId]?.connection?.sendSafely(
                RoomWireMessage.GameEnvelope(
                    hostMatchController.buildSnapshotMessage(seatId, lastActionMessage),
                ),
            )
        }
        if (match.phase == MatchPhase.FINISHED) {
            val roundScores = match.result?.scoreSummary?.roundScores.orEmpty()
            applyRoundScores(roundScores)
            resetReadyStatesAfterMatchFinished()
            clearActiveMatchState()
            broadcastSnapshot()
            matchLoopJob?.cancel()
        }
    }

    private fun startClientHeartbeatWatchdog() {
        clientHeartbeatJob?.cancel()
        clientHeartbeatJob = scope.launch {
            while (isActive) {
                delay(CLIENT_HEARTBEAT_CHECK_INTERVAL_MS)
                if (roomRole !is RoomRole.Client) {
                    break
                }
                if (lastHostHeartbeatAt == 0L) {
                    continue
                }
                if (System.currentTimeMillis() - lastHostHeartbeatAt > HEARTBEAT_TIMEOUT_MS) {
                    handleHostConnectionLost("房主连接已断开，房间已关闭")
                    break
                }
            }
        }
    }

    private fun handleHostConnectionLost(message: String) {
        _roomUiState.update {
            it.copy(
                connectionHint = message,
                roomClosedByHost = true,
                homeNoticeMessage = message,
            )
        }
        localMatchController.reset()
        shutdownCurrentRole()
        authorityState = RoomAuthorityState()
        _roomUiState.update {
            it.copy(
                slots = List(SLOT_COUNT) { index -> SlotState(slotIndex = index) },
                bluetoothVisible = false,
            )
        }
    }

    private fun setRemoteReady(participantId: String, ready: Boolean) {
        updateParticipant(participantId) { participant ->
            participant.copy(
                connectionStatus = if (ready) MemberConnectionStatus.READY else MemberConnectionStatus.NOT_READY,
            )
        }
        val displayName = authorityState.participants[participantId]?.displayName.orEmpty()
        publishUiState(connectionHint = "$displayName ${if (ready) "已准备" else "取消准备"}")
        broadcastSnapshot()
    }

    private fun toggleReadyForParticipant(participantId: String) {
        val participant = authorityState.participants[participantId] ?: return
        val nextStatus = if (participant.connectionStatus == MemberConnectionStatus.READY) {
            MemberConnectionStatus.NOT_READY
        } else {
            MemberConnectionStatus.READY
        }
        updateParticipant(participantId) { it.copy(connectionStatus = nextStatus) }
        publishUiState(connectionHint = "准备状态已更新")
        broadcastSnapshot()
    }

    private fun handleHostSwapRequest(requesterParticipantId: String, targetSlotIndex: Int) {
        val requesterSlotIndex = slotIndexOfParticipant(requesterParticipantId)
        val requester = authorityState.participants[requesterParticipantId]
        if (requesterSlotIndex == null || requester == null || requesterSlotIndex == targetSlotIndex) {
            return
        }
        val targetParticipantId = occupantAt(targetSlotIndex)
        if (targetParticipantId == null) {
            applySeatSwap(requesterSlotIndex, targetSlotIndex)
            return
        }
        val targetParticipant = authorityState.participants[targetParticipantId]
        if (targetParticipant == null) {
            return
        }
        when (targetParticipant.occupantType) {
            SlotOccupantType.AI -> applySeatSwap(requesterSlotIndex, targetSlotIndex)
            SlotOccupantType.HUMAN_HOST -> {
                publishUiState(
                    pendingSwapRequest = SwapRequest(
                        requesterSlotIndex = requesterSlotIndex,
                        targetSlotIndex = targetSlotIndex,
                        requesterName = requester.displayName,
                    ),
                    connectionHint = "${requester.displayName} 请求与房主换位",
                )
            }
            SlotOccupantType.HUMAN_MEMBER -> {
                participantConnections[targetParticipantId]?.connection?.sendSafely(
                    RoomWireMessage.SwapSeatPromptMessage(
                        RemoteSwapRequest(
                            requesterSlotIndex = requesterSlotIndex,
                            targetSlotIndex = targetSlotIndex,
                            requesterName = requester.displayName,
                        ),
                    ),
                )
                publishUiState(connectionHint = "已向 ${targetParticipant.displayName} 发送换位请求")
            }
        }
    }

    private fun handleRemoteSwapDecision(message: RoomWireMessage.SwapSeatDecisionMessage) {
        if (message.accepted) {
            applySeatSwap(message.requesterSlotIndex, message.targetSlotIndex)
            return
        }
        notifyRequesterSwapRejected(message.requesterSlotIndex)
        publishUiState(connectionHint = "换位请求被拒绝", pendingSwapRequest = null)
    }

    private fun applySeatSwap(slotIndexA: Int, slotIndexB: Int) {
        val participantA = occupantAt(slotIndexA)
        val participantB = occupantAt(slotIndexB)
        authorityState = authorityState.copy(
            slotAssignments = authorityState.slotAssignments
                .toMutableMap()
                .apply {
                    this[slotIndexA] = participantB
                    this[slotIndexB] = participantA
                },
        )
        publishUiState(
            connectionHint = "已完成换位",
            pendingSwapRequest = null,
            showSlotActionMenu = false,
        )
        broadcastSnapshot()
    }

    private fun notifyRequesterSwapRejected(requesterSlotIndex: Int) {
        val requesterParticipantId = occupantAt(requesterSlotIndex) ?: return
        if (requesterParticipantId == localParticipantId()) {
            publishUiState(connectionHint = "换位请求已被拒绝")
            return
        }
        participantConnections[requesterParticipantId]?.connection?.sendSafely(
            RoomWireMessage.RoomSnapshotMessage(snapshotOfCurrentRoom()),
        )
    }

    private fun handleRemoteLeave(participantId: String) {
        participantConnections.remove(participantId)?.connection?.close()
        removeParticipantFromRoom(participantId, clearSlot = true, reason = "已离开房间")
    }

    private fun markParticipantDisconnected(participantId: String) {
        participantConnections.remove(participantId)?.connection?.close()
        updateParticipant(participantId) { participant ->
            participant.copy(connectionStatus = MemberConnectionStatus.DISCONNECTED)
        }
        matchSeatIdOfParticipant(participantId)?.let { seatId ->
            hostMatchController.markDisconnected(seatId, disconnected = true)
        }
        val displayName = authorityState.participants[participantId]?.displayName.orEmpty()
        publishUiState(connectionHint = "$displayName 连接中断")
        broadcastSnapshot()
        if (hostMatchController.currentMatch()?.phase != MatchPhase.FINISHED && activeMatchSeatAssignments.isNotEmpty()) {
            updateAllMatchSnapshots(lastActionMessage = "$displayName 已掉线")
        }
    }

    private fun removeParticipantFromRoom(participantId: String, clearSlot: Boolean, reason: String) {
        val slotIndex = slotIndexOfParticipant(participantId)
        val displayName = authorityState.participants[participantId]?.displayName.orEmpty()
        activeMatchSeatAssignments = activeMatchSeatAssignments - participantId
        authorityState = authorityState.copy(
            participants = authorityState.participants - participantId,
            slotAssignments = authorityState.slotAssignments.toMutableMap().apply {
                if (clearSlot && slotIndex != null) {
                    this[slotIndex] = null
                }
            },
        )
        publishUiState(connectionHint = "$displayName $reason")
        broadcastSnapshot()
    }

    private fun broadcastSnapshot() {
        if (roomRole !is RoomRole.Host) return
        val message = RoomWireMessage.RoomSnapshotMessage(snapshotOfCurrentRoom())
        participantConnections.values.forEach { connection ->
            connection.connection.sendSafely(message)
        }
    }

    private fun snapshotOfCurrentRoom(): RemoteRoomSnapshot {
        return RemoteRoomSnapshot(
            roomName = authorityState.roomName,
            hostDeviceName = authorityState.hostDeviceName,
            currentRule = authorityState.currentRule.name,
            connectionHint = _roomUiState.value.connectionHint,
            slots = buildSlotStates(localParticipantId = localParticipantId()).map { slot ->
                RemoteSlotSnapshot(
                    slotIndex = slot.slotIndex,
                    seatId = slot.seatId,
                    participantId = slot.participantId,
                    occupantType = slot.occupantType?.name,
                    displayName = slot.displayName,
                    avatarResId = slot.avatarResId,
                    connectionStatus = slot.connectionStatus?.name,
                    aiDifficulty = slot.aiDifficulty?.name,
                    cumulativeScore = slot.cumulativeScore,
                )
            },
        )
    }

    private fun RemoteRoomSnapshot.toAuthorityState(): RoomAuthorityState {
        val participants = mutableMapOf<String, ParticipantRecord>()
        val slotAssignments = emptySlotAssignments().toMutableMap()
        slots.forEach { slot ->
            val participantId = slot.participantId ?: return@forEach
            participants[participantId] = ParticipantRecord(
                participantId = participantId,
                occupantType = slot.occupantType?.let(SlotOccupantType::valueOf) ?: return@forEach,
                displayName = slot.displayName,
                avatarResId = slot.avatarResId,
                connectionStatus = slot.connectionStatus?.let(MemberConnectionStatus::valueOf),
                aiDifficulty = slot.aiDifficulty?.let(AiDifficulty::valueOf),
                cumulativeScore = slot.cumulativeScore,
            )
            slotAssignments[slot.slotIndex] = participantId
        }
        return RoomAuthorityState(
            roomName = roomName,
            hostDeviceName = hostDeviceName,
            currentRule = GameRuleDisplay.valueOf(currentRule),
            bluetoothVisible = false,
            participants = participants,
            slotAssignments = slotAssignments,
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
        searchState: BluetoothSearchState = _roomUiState.value.searchState,
        selectedDeviceAddress: String? = _roomUiState.value.selectedDeviceAddress,
    ) {
        val slots = buildSlotStates(localParticipantId = localParticipantId)
        _roomUiState.value = RoomUiState(
            isHost = isHost,
            roomName = authorityState.roomName,
            hostDeviceName = authorityState.hostDeviceName,
            currentRule = authorityState.currentRule,
            slots = slots,
            bluetoothVisible = authorityState.bluetoothVisible && isHost,
            connectionHint = connectionHint ?: _roomUiState.value.connectionHint,
            discoveredDevices = _roomUiState.value.discoveredDevices,
            searchState = searchState,
            selectedDeviceAddress = selectedDeviceAddress,
            canStartGame = canStart(slots),
            pendingSwapRequest = pendingSwapRequest,
            showAiDifficultyDialog = showAiDifficultyDialog,
            aiDialogTargetSlot = aiDialogTargetSlot,
            showSlotActionMenu = showSlotActionMenu,
            slotActionMenuTarget = slotActionMenuTarget,
        )
    }

    @Suppress("UnusedParameter")
    private fun buildSlotStates(localParticipantId: String): List<SlotState> {
        return (0 until SLOT_COUNT).map { slotIndex ->
            val participantId = authorityState.slotAssignments[slotIndex]
            val participant = participantId?.let(authorityState.participants::get)
            if (participant == null) {
                SlotState(slotIndex = slotIndex, seatId = slotIndex)
            } else {
                SlotState(
                    slotIndex = slotIndex,
                    seatId = slotIndex,
                    participantId = participant.participantId,
                    occupantType = participant.occupantType,
                    displayName = participant.displayName,
                    avatarResId = participant.avatarResId,
                    connectionStatus = participant.connectionStatus,
                    aiDifficulty = participant.aiDifficulty,
                    cumulativeScore = participant.cumulativeScore,
                    isLocalPlayer = participant.participantId == localParticipantId,
                )
            }
        }
    }

    private fun updateParticipant(participantId: String, transform: (ParticipantRecord) -> ParticipantRecord) {
        val participant = authorityState.participants[participantId] ?: return
        authorityState = authorityState.copy(
            participants = authorityState.participants + (participantId to transform(participant)),
        )
    }

    private fun occupantAt(slotIndex: Int): String? = authorityState.slotAssignments[slotIndex]

    private fun slotIndexOfParticipant(participantId: String): Int? {
        return authorityState.slotAssignments.entries.firstOrNull { it.value == participantId }?.key
    }

    private fun firstAvailableRemoteSlotIndex(): Int? {
        return (1 until SLOT_COUNT).firstOrNull { authorityState.slotAssignments[it] == null }
    }

    private fun normalizedSlotAssignments(participantId: String, keepSlotIndex: Int): Map<Int, String?> {
        return authorityState.slotAssignments.toMutableMap().apply {
            entries.forEach { entry ->
                if (entry.key != keepSlotIndex && entry.value == participantId) {
                    this[entry.key] = null
                }
            }
            this[keepSlotIndex] = participantId
        }
    }

    private fun nextAiParticipantId(): String {
        var index = 1
        while (authorityState.participants.containsKey("ai-$index")) {
            index += 1
        }
        return "ai-$index"
    }

    private fun nextAiDisplayName(difficulty: AiDifficulty): String {
        val usedNumbers = authorityState.participants.values
            .filter { it.occupantType == SlotOccupantType.AI }
            .mapNotNull { it.displayName.substringAfterLast(' ').toIntOrNull() }
            .toSet()
        val nextNumber = generateSequence(1) { it + 1 }.first { it !in usedNumbers }
        return "AI(${difficulty.label}) $nextNumber"
    }

    private fun buildSeatConfigs(): List<Triple<Int, String, SeatControllerType>> {
        return (0 until SLOT_COUNT).map { slotIndex ->
            val participantId = authorityState.slotAssignments[slotIndex]
            val participant = participantId?.let(authorityState.participants::get)
            val controllerType = if (participant?.occupantType == SlotOccupantType.AI) {
                SeatControllerType.RULE_BASED_AI
            } else {
                SeatControllerType.HUMAN
            }
            val displayName = participant?.displayName ?: "玩家${slotIndex + 1}"
            Triple(slotIndex, displayName, controllerType)
        }
    }

    private fun applyRoundScores(roundScores: List<com.example.chudadi.model.game.entity.RoundScore>) {
        val scoreMap = roundScores.associateBy { it.seatId }
        authorityState = authorityState.copy(
            participants = authorityState.participants.mapValues { (_, participant) ->
                val slotIndex = slotIndexOfParticipant(participant.participantId) ?: return@mapValues participant
                val roundScore = scoreMap[slotIndex]?.roundScore ?: 0
                participant.copy(cumulativeScore = participant.cumulativeScore + roundScore)
            },
        )
    }

    private fun resetReadyStatesAfterMatchFinished() {
        authorityState = authorityState.copy(
            participants = authorityState.participants.mapValues { (_, participant) ->
                participant.copy(connectionStatus = finishedMatchStatus(participant))
            },
        )
    }

    private fun finishedMatchStatus(participant: ParticipantRecord): MemberConnectionStatus? {
        return RoomMatchRules.finishedMatchStatus(
            occupantType = participant.occupantType,
            currentStatus = participant.connectionStatus,
        )
    }

    private fun participantReconnectStatus(participantId: String): MemberConnectionStatus {
        val participant = authorityState.participants[participantId]
        return RoomMatchRules.participantReconnectStatus(
            occupantType = participant?.occupantType,
            isFinishedMatch = hostMatchController.currentMatch()?.phase == MatchPhase.FINISHED,
            hasActiveMatchSeatAssignments = activeMatchSeatAssignments.isNotEmpty(),
        )
    }

    private fun clearCurrentNetworkMatch() {
        hostMatchController.clearCurrentMatch()
        clearActiveMatchState()
        matchLoopJob?.cancel()
    }

    private fun stopDiscoveryIfNeeded(adapter: BluetoothAdapter) {
        if (!BluetoothPermissionUtils.hasScanPermission(appContext)) {
            return
        }
        @SuppressLint("MissingPermission")
        if (adapter.isDiscovering) {
            adapter.cancelDiscovery()
        }
    }

    private fun GameRuleDisplay.toGameRuleSet(): GameRuleSet {
        return when (this) {
            GameRuleDisplay.SOUTHERN -> GameRuleSet.SOUTHERN
            GameRuleDisplay.NORTHERN -> GameRuleSet.NORTHERN
        }
    }

    private fun canStart(slots: List<SlotState>): Boolean {
        return RoomMatchRules.canStart(slots)
    }

    private fun buildActiveMatchSeatAssignments(): Map<String, Int> {
        return authorityState.slotAssignments.entries
            .mapNotNull { (slotIndex, participantId) -> participantId?.let { it to slotIndex } }
            .toMap()
    }

    private fun localMatchSeatId(participantId: String): Int? {
        return matchSeatIdOfParticipant(participantId)
    }

    private fun matchSeatIdOfParticipant(participantId: String): Int? {
        return activeMatchSeatAssignments[participantId] ?: slotIndexOfParticipant(participantId)
    }

    private fun clearActiveMatchState() {
        activeMatchSeatAssignments = emptyMap()
    }

    private fun sendMatchRecoveryMessage(participantId: String) {
        val connection = participantConnections[participantId]?.connection
        val seatId = matchSeatIdOfParticipant(participantId)
        val match = hostMatchController.currentMatch()
        val hasRecoveryTarget = connection != null && seatId != null && match != null
        val isRecoverableMatch = match?.phase != MatchPhase.FINISHED
        if (!hasRecoveryTarget || !isRecoverableMatch) {
            return
        }
        connection.sendSafely(
            RoomWireMessage.GameEnvelope(
                hostMatchController.buildMatchStartedMessage(localSeatId = seatId),
            ),
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
            )
        )
    }

    private fun shutdownCurrentRole() {
        acceptJob?.cancel()
        heartbeatJob?.cancel()
        clientHeartbeatJob?.cancel()
        matchLoopJob?.cancel()
        serverSocket?.closeSafely()
        serverSocket = null
        lastHostHeartbeatAt = 0L
        when (val role = roomRole) {
            is RoomRole.Client -> role.connection.close()
            is RoomRole.Host -> participantConnections.values.forEach { it.connection.close() }
            RoomRole.Idle -> Unit
        }
        participantConnections.clear()
        clearActiveMatchState()
        currentHostAddress = null
        roomRole = RoomRole.Idle
    }

    private fun isSeatDisconnected(seatId: Int): Boolean {
        val participantId = occupantAt(seatId) ?: return false
        return authorityState.participants[participantId]?.connectionStatus == MemberConnectionStatus.DISCONNECTED
    }

    private fun seatDisplayName(seatId: Int): String {
        val participantId = occupantAt(seatId) ?: return "玩家${seatId + 1}"
        return authorityState.participants[participantId]?.displayName ?: "玩家${seatId + 1}"
    }

    fun clear() {
        shutdownCurrentRole()
        unregisterDiscoveryReceiverIfNeeded()
        scope.cancel()
    }

    private fun registerDiscoveryReceiverIfNeeded() {
        if (discoveryReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        appContext.registerReceiver(discoveryReceiver, filter)
        discoveryReceiverRegistered = true
    }

    private fun unregisterDiscoveryReceiverIfNeeded() {
        if (!discoveryReceiverRegistered) return
        appContext.unregisterReceiver(discoveryReceiver)
        discoveryReceiverRegistered = false
    }

    @SuppressLint("MissingPermission")
    private fun addDiscoveredDevice(device: BluetoothDevice, isBonded: Boolean) {
        if (!BluetoothPermissionUtils.hasConnectPermission(appContext)) {
            return
        }
        val item = BluetoothDiscoveredDevice(
            name = device.name.orEmpty().ifBlank { "未知设备" },
            address = device.address,
            isBonded = isBonded,
        ).toUiState()
        _roomUiState.update { state ->
            val merged = (state.discoveredDevices + item)
                .distinctBy { it.address }
                .sortedWith(compareByDescending<DiscoveredDeviceUiState> { it.isBonded }.thenBy { it.name })
            state.copy(discoveredDevices = merged)
        }
    }

    private fun BluetoothDiscoveredDevice.toUiState(): DiscoveredDeviceUiState {
        return DiscoveredDeviceUiState(
            name = name,
            address = address,
            isBonded = isBonded,
        )
    }

    private fun localParticipantId(): String {
        return when (val role = roomRole) {
            is RoomRole.Host -> role.localParticipantId
            is RoomRole.Client -> role.localParticipantId
            RoomRole.Idle -> HOST_PARTICIPANT_ID
        }
    }

    private fun participantIdForConnection(connection: RoomSocketConnection): String {
        return connection.remoteAddress.ifBlank { "member-${System.currentTimeMillis()}" }
    }

    private fun BluetoothServerSocket.closeSafely() {
        try {
            close()
        } catch (_: IOException) {
            // ignore close failure
        }
    }

    private companion object {
        const val SLOT_COUNT = 4
        const val HOST_PARTICIPANT_ID = "host"
        const val SERVICE_NAME = "ChuDaDiRoom"
        val ROOM_UUID: UUID = UUID.fromString("a9b56c03-6cae-417b-a522-3b299d790e14")
        const val HEARTBEAT_INTERVAL_MS = 5_000L
        const val HEARTBEAT_TIMEOUT_MS = 15_000L
        const val CLIENT_HEARTBEAT_CHECK_INTERVAL_MS = 3_000L
        const val MATCH_LOOP_INTERVAL_MS = 250L
    }
}

private class RoomSocketConnection(
    socket: BluetoothSocket,
    private val frameCodec: RoomFrameCodec,
) {
    private val input = DataInputStream(socket.inputStream)
    private val output = DataOutputStream(socket.outputStream)
    private val bluetoothSocket = socket
    val remoteAddress: String = socket.remoteDevice?.address.orEmpty()

    suspend fun awaitJoinAccepted(
        playerName: String,
        avatarResId: Int?,
        resumeParticipantId: String? = null,
    ): RoomWireMessage.JoinRoomAccepted {
        send(
            RoomWireMessage.JoinRoomRequest(
                playerName = playerName,
                avatarResId = avatarResId,
                resumeParticipantId = resumeParticipantId,
            ),
        )
        return when (val response = read()) {
            is RoomWireMessage.JoinRoomAccepted -> response
            is RoomWireMessage.JoinRoomRejected -> throw IOException(response.reason)
            else -> throw IOException("Unexpected join response")
        }
    }

    suspend fun send(message: RoomWireMessage) {
        withContext(Dispatchers.IO) {
            frameCodec.writeMessage(output, message)
        }
    }

    fun sendSafely(message: RoomWireMessage) {
        try {
            frameCodec.writeMessage(output, message)
        } catch (_: IOException) {
            close()
        }
    }

    suspend fun read(): RoomWireMessage {
        return withContext(Dispatchers.IO) {
            frameCodec.readMessage(input)
        }
    }

    fun close() {
        try {
            bluetoothSocket.close()
        } catch (_: IOException) {
            // ignore close failure
        }
    }
}
