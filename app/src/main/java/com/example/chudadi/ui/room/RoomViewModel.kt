@file:Suppress("TooManyFunctions")

package com.example.chudadi.ui.room

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.chudadi.R
import com.example.chudadi.controller.game.LocalGameAction
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
import kotlinx.coroutines.launch

class RoomViewModel(
    private val playerPrefsRepository: PlayerPreferencesRepository,
    private val bluetoothRoomRepository: BluetoothRoomRepository,
    private val reconnectSessionRepository: ReconnectSessionRepository,
) : ViewModel() {

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
    ) { roomState, latestPlayerName, scoreMap ->
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

    @Suppress("CyclomaticComplexMethod")
    fun dispatch(action: RoomAction) {
        when (action) {
            RoomAction.StartBluetoothDiscovery -> bluetoothRoomRepository.startDiscoveryWithFeedback()
            is RoomAction.ConnectToBluetoothDevice -> connectToBluetoothDevice(action.address)
            RoomAction.ToggleRule -> bluetoothRoomRepository.handleToggleRule()
            is RoomAction.AddAiToSlot -> bluetoothRoomRepository.handleAddAiToSlot(action.slotIndex, action.difficulty)
            is RoomAction.RemoveSlotOccupant -> bluetoothRoomRepository.handleRemoveSlotOccupant(action.slotIndex)
            is RoomAction.RequestSwapWithSlot -> bluetoothRoomRepository.handleSwapRequest(action.targetSlotIndex)
            is RoomAction.ConfirmSwap -> bluetoothRoomRepository.handleSwapDecision(action.request, accepted = true)
            RoomAction.DeclineSwap -> {
                uiState.value.pendingSwapRequest?.let {
                    bluetoothRoomRepository.handleSwapDecision(it, accepted = false)
                }
            }
            RoomAction.ToggleReady -> bluetoothRoomRepository.handleToggleReady()
            RoomAction.StartGame -> startConfiguredGame()
            RoomAction.EnableBluetoothBroadcast -> {
                _externalEvents.tryEmit(RoomExternalEvent.RequestEnableBluetoothBroadcast)
            }
            RoomAction.ResetScores -> {
                scoreAdjustments.value = emptyMap()
                bluetoothRoomRepository.resetRoomScores()
            }
            is RoomAction.AccumulateScores -> accumulateScores(action.scores)
            is RoomAction.OpenAiDialog -> bluetoothRoomRepository.openAiDialog(action.slotIndex)
            RoomAction.DismissAiDialog -> bluetoothRoomRepository.dismissMenus()
            is RoomAction.OpenSlotActionMenu -> bluetoothRoomRepository.openSlotActionMenu(action.slotIndex)
            RoomAction.DismissSlotActionMenu -> bluetoothRoomRepository.dismissMenus()
            RoomAction.ExitRoom -> bluetoothRoomRepository.leaveRoom()
            RoomAction.ResetRoom -> bluetoothRoomRepository.leaveRoom()
            RoomAction.ResetRoomAsHost -> Unit
            RoomAction.ConsumeRoomExitNotice -> bluetoothRoomRepository.consumeRoomExitNotice()
            RoomAction.ConsumeHomeNotice -> bluetoothRoomRepository.consumeHomeNotice()
            RoomAction.ConsumeJoinError -> bluetoothRoomRepository.consumeJoinError()
        }
    }

    fun loadJoinableDevices() {
        bluetoothRoomRepository.loadBondedDevicesWithFeedback()
    }

    fun showHomeNotice(message: String) {
        bluetoothRoomRepository.showHomeNotice(message)
    }

    fun showJoinError(message: String) {
        bluetoothRoomRepository.showJoinError(message)
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
        return result.isSuccess
    }

    fun createHostRoom(hostDeviceName: String): Boolean {
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

    fun enableBluetoothBroadcast(hostDeviceName: String): Boolean {
        val result = bluetoothRoomRepository.enableBluetoothBroadcast(hostDeviceName)
        return result.isSuccess
    }

    fun dispatchGameAction(action: LocalGameAction) {
        bluetoothRoomRepository.onLocalGameAction(action)
    }

    private fun connectToBluetoothDevice(address: String) {
        val target = uiState.value.discoveredDevices.firstOrNull { it.address == address } ?: return
        viewModelScope.launch {
            bluetoothRoomRepository.connectToHost(
                device = BluetoothDiscoveredDevice(
                    name = target.name,
                    address = target.address,
                    isBonded = target.isBonded,
                ),
                playerName = playerName.value,
                avatarResId = avatarResId.value,
            )
        }
    }

    private fun accumulateScores(scores: List<RoundScore>) {
        scoreAdjustments.update { current ->
            val updated = current.toMutableMap()
            scores.forEach { score ->
                updated[score.seatId] = (updated[score.seatId] ?: 0) + score.roundScore
            }
            updated
        }
    }

    private fun startConfiguredGame() {
        when (uiState.value.roomMode) {
            RoomMode.LOCAL -> emitLocalGameLaunch()
            RoomMode.BLUETOOTH_HOST -> bluetoothRoomRepository.startNetworkMatch()
            RoomMode.BLUETOOTH_CLIENT -> Unit
        }
    }

    private fun emitLocalGameLaunch() {
        val slots = uiState.value.slots
        val seatConfigs = slots.map { slot ->
            val controllerType = if (slot.occupantType == SlotOccupantType.AI) {
                SeatControllerType.RULE_BASED_AI
            } else {
                SeatControllerType.HUMAN
            }
            Triple(
                slot.seatId,
                slot.displayName.ifBlank { "玩家${slot.seatId + 1}" },
                controllerType,
            )
        }
        val localSeatId = slots.firstOrNull { it.isLocalPlayer }?.seatId ?: 0
        val ruleSet = when (uiState.value.currentRule) {
            GameRuleDisplay.SOUTHERN -> GameRuleSet.SOUTHERN
            GameRuleDisplay.NORTHERN -> GameRuleSet.NORTHERN
        }
        _gameLaunchEvents.tryEmit(
            RoomGameLaunchEvent.StartLocalMatch(
                seatConfigs = seatConfigs,
                localSeatId = localSeatId,
                ruleSet = ruleSet,
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
        bluetoothRoomRepository.clear()
        super.onCleared()
    }

    companion object {
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
