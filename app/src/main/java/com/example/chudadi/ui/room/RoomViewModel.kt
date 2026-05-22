@file:Suppress("TooManyFunctions")

package com.example.chudadi.ui.room

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.chudadi.BuildConfig
import com.example.chudadi.R
import com.example.chudadi.ai.base.AIDifficulty
import com.example.chudadi.controller.game.LocalGameAction
import com.example.chudadi.controller.game.MatchUiStateMapper
import com.example.chudadi.controller.game.SeatConfig
import com.example.chudadi.data.repository.PlayerPreferencesRepository
import com.example.chudadi.data.repository.ReconnectSessionRepository
import com.example.chudadi.model.game.entity.MatchPhase
import com.example.chudadi.model.game.entity.RoundScore
import com.example.chudadi.model.game.entity.SeatControllerType
import com.example.chudadi.model.game.rule.GameRuleSet
import com.example.chudadi.model.game.snapshot.MatchUiState
import com.example.chudadi.navigation.AppFlowNavigationEvent
import com.example.chudadi.navigation.AppFlowRoute
import com.example.chudadi.network.room.BluetoothDiscoveredDevice
import com.example.chudadi.network.room.BluetoothRoomRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch

class RoomViewModel(
    private val playerPrefsRepository: PlayerPreferencesRepository,
    private val bluetoothRoomRepository: BluetoothRoomRepository,
    private val reconnectSessionRepository: ReconnectSessionRepository,
) : ViewModel() {

    private data class LocalAiState(
        val aiPlaySpeed: AiPlaySpeed = AiPlaySpeed.NORMAL,
        val aiSelectionStep: AiSelectionStep = AiSelectionStep.SELECT_TYPE,
        val selectedAiType: AIType? = null,
        val totalGamesPlayed: Int = 0,
    )

    val playerName: StateFlow<String> = playerPrefsRepository.playerName
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = "默认玩家",
        )

    val avatarResId: StateFlow<Int> = playerPrefsRepository.avatarResId
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = R.drawable.avatar,
        )

    private val scoreAdjustments = MutableStateFlow<Map<Int, Int>>(emptyMap())
    private val _appFlowEvents = MutableSharedFlow<AppFlowNavigationEvent>(extraBufferCapacity = 8)
    private val _gameLaunchEvents = MutableSharedFlow<RoomGameLaunchEvent>(extraBufferCapacity = 4)
    private val _externalEvents = MutableSharedFlow<RoomExternalEvent>(extraBufferCapacity = 4)
    private var connectJob: Job? = null
    private var connectionCancelRequested: Boolean = false
    private var clientJoinCompleted: Boolean = false
    private val localAiState = MutableStateFlow(LocalAiState())

    val matchUiState: StateFlow<MatchUiState> = bluetoothRoomRepository.matchUiState
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = MatchUiState(),
        )

    val uiState: StateFlow<RoomUiState> = combine(
        bluetoothRoomRepository.roomUiState,
        playerName,
        scoreAdjustments,
        localAiState,
    ) { roomState, latestPlayerName, scoreMap, aiState ->
        roomState.copy(
            slots = roomState.slots.map { slot ->
                val slotWithLatestName = if (slot.isLocalPlayer && slot.displayName != latestPlayerName) {
                    slot.copy(displayName = latestPlayerName)
                } else {
                    slot
                }
                if (roomState.roomMode == RoomMode.LOCAL) {
                    slotWithLatestName.copy(
                        cumulativeScore = scoreMap[slot.seatId] ?: slotWithLatestName.cumulativeScore,
                    )
                } else {
                    slotWithLatestName
                }
            },
            aiPlaySpeed = aiState.aiPlaySpeed,
            aiSelectionStep = aiState.aiSelectionStep,
            selectedAiType = aiState.selectedAiType,
            totalGamesPlayed = aiState.totalGamesPlayed,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = RoomUiState(),
    )

    val appFlowEvents: SharedFlow<AppFlowNavigationEvent> = _appFlowEvents.asSharedFlow()
    val gameLaunchEvents: SharedFlow<RoomGameLaunchEvent> = _gameLaunchEvents.asSharedFlow()
    val externalEvents: SharedFlow<RoomExternalEvent> = _externalEvents.asSharedFlow()

    init {
        observeRoomExitEvents()
        observeMatchFinishedEvents()
    }

    @Suppress("CyclomaticComplexMethod", "LongMethod")
    fun dispatch(action: RoomAction) {
        when (action) {
            RoomAction.StartBluetoothDiscovery -> {
                cancelPendingConnection()
                bluetoothRoomRepository.startDiscoveryWithFeedback()
            }
            RoomAction.StopBluetoothDiscovery -> bluetoothRoomRepository.stopDiscovery()
            is RoomAction.ConnectToBluetoothDevice -> connectToBluetoothDevice(action.address)
            RoomAction.CancelPendingConnection -> cancelPendingConnection()
            RoomAction.CancelPendingConnectionIfNotJoined -> cancelPendingConnectionIfNotJoined()
            RoomAction.ToggleRule -> bluetoothRoomRepository.handleToggleRule()
            is RoomAction.AddAiToSlot -> {
                localAiState.update {
                    it.copy(aiSelectionStep = AiSelectionStep.SELECT_TYPE, selectedAiType = null)
                }
                bluetoothRoomRepository.handleAddAiToSlot(action.slotIndex, action.difficulty)
            }
            is RoomAction.RemoveSlotOccupant -> {
                scoreAdjustments.update { it - action.slotIndex }
                bluetoothRoomRepository.handleRemoveSlotOccupant(action.slotIndex)
            }
            is RoomAction.RequestSwapWithSlot -> {
                val localSlot = uiState.value.slots.firstOrNull { it.isLocalPlayer }
                if (uiState.value.roomMode == RoomMode.LOCAL &&
                    localSlot != null &&
                    localSlot.seatId != action.targetSlotIndex
                ) {
                    val fromKey = localSlot.seatId
                    val toKey = action.targetSlotIndex
                    scoreAdjustments.update { map ->
                        val fromScore = map[fromKey]
                        val toScore = map[toKey]
                        val updated = map.toMutableMap()
                        updated.remove(fromKey)
                        updated.remove(toKey)
                        if (toScore != null) updated[fromKey] = toScore
                        if (fromScore != null) updated[toKey] = fromScore
                        updated
                    }
                }
                bluetoothRoomRepository.handleSwapRequest(action.targetSlotIndex)
            }
            is RoomAction.ConfirmSwap -> bluetoothRoomRepository.handleSwapDecision(action.request, accepted = true)
            RoomAction.DeclineSwap -> {
                uiState.value.pendingSwapRequest?.let {
                    bluetoothRoomRepository.handleSwapDecision(it, accepted = false)
                }
            }
            RoomAction.ToggleReady -> bluetoothRoomRepository.handleToggleReady()
            RoomAction.StartGame -> startConfiguredGame()
            RoomAction.StartHostListening -> {
                _externalEvents.tryEmit(RoomExternalEvent.RequestStartHostListening)
            }
            RoomAction.ResetScores -> {
                scoreAdjustments.value = emptyMap()
                bluetoothRoomRepository.resetRoomScores()
            }
            is RoomAction.AccumulateScores -> accumulateScores(action.scores)
            is RoomAction.OpenAiDialog -> {
                localAiState.update {
                    it.copy(aiSelectionStep = AiSelectionStep.SELECT_TYPE, selectedAiType = null)
                }
                bluetoothRoomRepository.openAiDialog(action.slotIndex)
            }
            RoomAction.DismissAiDialog -> {
                localAiState.update {
                    it.copy(aiSelectionStep = AiSelectionStep.SELECT_TYPE, selectedAiType = null)
                }
                bluetoothRoomRepository.dismissMenus()
            }
            is RoomAction.OpenSlotActionMenu -> bluetoothRoomRepository.openSlotActionMenu(action.slotIndex)
            RoomAction.DismissSlotActionMenu -> bluetoothRoomRepository.dismissMenus()
            is RoomAction.ToggleAiPlaySpeed -> toggleAiPlaySpeed()
            is RoomAction.SelectAiType -> {
                localAiState.update {
                    it.copy(
                        selectedAiType = action.aiType,
                        aiSelectionStep = AiSelectionStep.SELECT_DIFFICULTY,
                    )
                }
            }
            RoomAction.BackToAiTypeSelection -> {
                localAiState.update { it.copy(aiSelectionStep = AiSelectionStep.SELECT_TYPE, selectedAiType = null) }
            }
            RoomAction.ExitRoom -> {
                cancelPendingConnection()
                bluetoothRoomRepository.leaveRoom()
            }
            RoomAction.ResetRoom -> {
                cancelPendingConnection()
                bluetoothRoomRepository.leaveRoom()
            }
            RoomAction.ResetRoomAsHost -> Unit
            RoomAction.ConsumeRoomExitNotice -> bluetoothRoomRepository.consumeRoomExitNotice()
            RoomAction.ConsumeHomeNotice -> bluetoothRoomRepository.consumeHomeNotice()
            RoomAction.ConsumeJoinError -> bluetoothRoomRepository.consumeJoinError()
        }
    }

    fun loadJoinableDevices() {
        cancelPendingConnection()
        bluetoothRoomRepository.loadBondedDevicesWithFeedback()
    }

    fun showHomeNotice(message: String) {
        bluetoothRoomRepository.showHomeNotice(message)
    }

    fun showJoinError(
        message: String,
        title: String = "无法加入房间",
    ) {
        bluetoothRoomRepository.showJoinError(message, title)
    }

    fun isBluetoothSupported(): Boolean = bluetoothRoomRepository.isBluetoothSupported

    fun isBluetoothEnabled(): Boolean = bluetoothRoomRepository.isBluetoothEnabled()

    fun hasBluetoothConnectPermission(): Boolean = bluetoothRoomRepository.hasBluetoothConnectPermission()

    fun hasBluetoothScanPermission(): Boolean = bluetoothRoomRepository.hasBluetoothScanPermission()

    suspend fun tryReconnectLastSession(): Boolean {
        val session = reconnectSessionRepository.session.first() ?: return false
        val result = bluetoothRoomRepository.tryReconnectLastSession(
            playerName = playerName.value,
            avatarResId = avatarResId.value,
            session = session,
        )
        if (result.isFailure) {
            reconnectSessionRepository.clearSession()
            return false
        }
        return true
    }

    suspend fun createHostRoom(hostDeviceName: String): Boolean {
        scoreAdjustments.value = emptyMap()
        val result = bluetoothRoomRepository.createHostRoom(
            playerName = playerName.value,
            avatarResId = avatarResId.value,
            hostDeviceName = hostDeviceName,
        )
        return result.isSuccess
    }

    fun createLocalRoom(hostDeviceName: String) {
        scoreAdjustments.value = emptyMap()
        bluetoothRoomRepository.createLocalRoom(
            playerName = playerName.value,
            avatarResId = avatarResId.value,
            hostDeviceName = hostDeviceName,
        )
    }

    suspend fun startHostListening(hostDeviceName: String): Boolean {
        val result = bluetoothRoomRepository.startHostListening(hostDeviceName)
        return result.isSuccess
    }

    fun dispatchGameAction(action: LocalGameAction) {
        bluetoothRoomRepository.onLocalGameAction(action)
    }

    @Suppress("TooGenericExceptionCaught")
    private fun connectToBluetoothDevice(address: String) {
        if (connectJob?.isActive == true) return
        val target = uiState.value.discoveredDevices.firstOrNull { it.address == address } ?: return
        val device = BluetoothDiscoveredDevice(
            name = target.name,
            address = target.address,
            isBonded = target.isBonded,
        )
        clientJoinCompleted = false
        connectionCancelRequested = false
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            val currentJob = coroutineContext.job
            try {
                val result = bluetoothRoomRepository.connectToHost(
                    device = device,
                    playerName = playerName.value,
                    avatarResId = avatarResId.value,
                    shouldAcceptResult = { connectJob === currentJob && currentJob.isActive },
                    onClientJoinAccepted = { clientJoinCompleted = true },
                )
                if (connectJob !== currentJob) return@launch
                val error = result.exceptionOrNull()
                if (error != null && uiState.value.joinErrorMessage == null) {
                    clientJoinCompleted = false
                    bluetoothRoomRepository.showJoinError(error.message ?: "连接失败，请重试")
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (connectJob !== currentJob) return@launch
                clientJoinCompleted = false
                bluetoothRoomRepository.showJoinError(error.message ?: "连接失败，请重试")
            } finally {
                if (connectJob === currentJob) {
                    connectJob = null
                    if (!hasJoinedBluetoothRoom()) {
                        clientJoinCompleted = false
                    }
                    connectionCancelRequested = false
                }
            }
        }
        connectJob = job
        job.start()
    }

    private fun cancelPendingConnection() {
        if (clientJoinCompleted || hasJoinedBluetoothRoom()) return
        val job = connectJob
        connectJob = null
        val hasConnectionInUiState = uiState.value.searchState == BluetoothSearchState.CONNECTING
        if (job != null || (hasConnectionInUiState && !connectionCancelRequested)) {
            connectionCancelRequested = true
            job?.cancel()
            bluetoothRoomRepository.cancelPendingClientConnection()
        }
    }

    private fun cancelPendingConnectionIfNotJoined() {
        if (clientJoinCompleted || hasJoinedBluetoothRoom()) return
        cancelPendingConnection()
    }

    private fun hasJoinedBluetoothRoom(): Boolean {
        return uiState.value.roomMode == RoomMode.BLUETOOTH_CLIENT
    }

    private fun accumulateScores(scores: List<RoundScore>) {
        scoreAdjustments.update { current ->
            val updated = current.toMutableMap()
            scores.forEach { score ->
                updated[score.seatId] = (updated[score.seatId] ?: 0) + score.roundScore
            }
            updated
        }
        localAiState.update { it.copy(totalGamesPlayed = it.totalGamesPlayed + 1) }
    }

    private fun toggleAiPlaySpeed() {
        localAiState.update { current ->
            val allSpeeds = AiPlaySpeed.entries.filter {
                it != AiPlaySpeed.DEBUG_100_ROUNDS || BuildConfig.DEBUG
            }
            val nextIndex = (allSpeeds.indexOf(current.aiPlaySpeed) + 1) % allSpeeds.size
            current.copy(aiPlaySpeed = allSpeeds[nextIndex])
        }
    }

    private fun startConfiguredGame() {
        when (uiState.value.roomMode) {
            RoomMode.LOCAL -> emitLocalGameLaunch()
            RoomMode.BLUETOOTH_HOST -> {
                val aiMoveDelayMillis = localAiState.value.aiPlaySpeed.delayMillis
                bluetoothRoomRepository.startNetworkMatch(aiMoveDelayMillis = aiMoveDelayMillis)
            }
            RoomMode.BLUETOOTH_CLIENT -> Unit
        }
    }

    private fun emitLocalGameLaunch() {
        val slots = uiState.value.slots
        var aiNumber = 0
        val seatConfigs = slots.map { slot ->
            val controllerType: SeatControllerType
            val aiDifficulty: AIDifficulty? = when {
                slot.occupantType == SlotOccupantType.AI && slot.aiType == AIType.ONNX_RL -> {
                    aiNumber++
                    controllerType = SeatControllerType.ONNX_RL_AI
                    slot.aiDifficulty?.difficultyLevel?.toAIDifficulty()
                }
                slot.occupantType == SlotOccupantType.AI -> {
                    aiNumber++
                    controllerType = SeatControllerType.RULE_BASED_AI
                    slot.aiDifficulty?.difficultyLevel?.toAIDifficulty()
                }
                else -> {
                    controllerType = SeatControllerType.HUMAN
                    null
                }
            }
            val displayName = if (slot.occupantType == SlotOccupantType.AI && slot.aiDifficulty != null) {
                generateAiDisplayName(slot.aiDifficulty, aiNumber)
            } else {
                slot.displayName.ifBlank { "玩家${slot.seatId + 1}" }
            }
            SeatConfig(
                seatId = slot.seatId,
                name = displayName,
                controllerType = controllerType,
                aiDifficulty = aiDifficulty,
            )
        }
        val localSeatId = slots.firstOrNull { it.isLocalPlayer }?.seatId
            ?: MatchUiStateMapper.NO_LOCAL_SEAT_ID
        val ruleSet = when (uiState.value.currentRule) {
            GameRuleDisplay.SOUTHERN -> GameRuleSet.SOUTHERN
            GameRuleDisplay.NORTHERN -> GameRuleSet.NORTHERN
        }
        val aiPlaySpeed = localAiState.value.aiPlaySpeed
        _gameLaunchEvents.tryEmit(
            RoomGameLaunchEvent.StartLocalMatch(
                seatConfigs = seatConfigs,
                localSeatId = localSeatId,
                ruleSet = ruleSet,
                aiMoveDelayMillis = aiPlaySpeed.delayMillis,
                aiPlaySpeed = aiPlaySpeed,
            ),
        )
    }

    private fun observeRoomExitEvents() {
        viewModelScope.launch {
            uiState
                .map { it.removedFromRoom to it.homeNoticeMessage }
                .distinctUntilChanged()
                .collect { (removedFromRoom, _) ->
                    if (removedFromRoom) {
                        _appFlowEvents.emit(
                            AppFlowNavigationEvent(
                                route = AppFlowRoute.HOME,
                                popUpTo = AppFlowRoute.HOME,
                                inclusive = true,
                            ),
                        )
                        bluetoothRoomRepository.consumeRoomExitNotice()
                    }
                }
        }
        viewModelScope.launch {
            uiState
                .map { it.roomClosedByHost to it.homeNoticeMessage }
                .distinctUntilChanged()
                .collect { (roomClosedByHost, _) ->
                    if (roomClosedByHost) {
                        _appFlowEvents.emit(
                            AppFlowNavigationEvent(
                                route = AppFlowRoute.HOME,
                                popUpTo = AppFlowRoute.HOME,
                                inclusive = true,
                            ),
                        )
                        bluetoothRoomRepository.consumeRoomExitNotice()
                    }
                }
        }
    }

    private fun observeMatchFinishedEvents() {
        viewModelScope.launch {
            matchUiState
                .map { it.phase == MatchPhase.FINISHED }
                .distinctUntilChanged()
                .collect { isFinished ->
                    if (isFinished) {
                        _appFlowEvents.emit(AppFlowNavigationEvent(route = AppFlowRoute.RESULT))
                    }
                }
        }
    }

    override fun onCleared() {
        cancelPendingConnection()
        bluetoothRoomRepository.clear()
        super.onCleared()
    }

    companion object {
        fun generateAiDisplayName(difficulty: RoomAiDifficulty, aiNumber: Int): String {
            return when (difficulty.aiType) {
                AIType.RULE_BASED -> "AIN$aiNumber"
                AIType.ONNX_RL -> "${difficulty.aiType.shortLabel}${difficulty.difficultyLevel.symbol}$aiNumber"
            }
        }

        private fun DifficultyLevel.toAIDifficulty(): AIDifficulty = when (this) {
            DifficultyLevel.EASY -> AIDifficulty.EASY
            DifficultyLevel.NORMAL -> AIDifficulty.NORMAL
            DifficultyLevel.HARD -> AIDifficulty.HARD
        }

        fun factory(
            playerPrefsRepository: PlayerPreferencesRepository,
            bluetoothRoomRepository: BluetoothRoomRepository,
            reconnectSessionRepository: ReconnectSessionRepository,
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return RoomViewModel(
                        playerPrefsRepository = playerPrefsRepository,
                        bluetoothRoomRepository = bluetoothRoomRepository,
                        reconnectSessionRepository = reconnectSessionRepository,
                    ) as T
                }
            }
        }
    }
}
