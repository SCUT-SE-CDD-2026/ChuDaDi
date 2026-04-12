@file:Suppress("TooManyFunctions")

package com.example.chudadi.ui.room

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.chudadi.R
import com.example.chudadi.data.repository.PlayerPreferencesRepository
import com.example.chudadi.model.game.entity.RoundScore
import com.example.chudadi.network.room.BluetoothDiscoveredDevice
import com.example.chudadi.network.room.BluetoothRoomRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RoomViewModel(
    private val playerPrefsRepository: PlayerPreferencesRepository,
    private val bluetoothRoomRepository: BluetoothRoomRepository,
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

    val uiState: StateFlow<RoomUiState> = combine(
        bluetoothRoomRepository.roomUiState,
        playerName,
        scoreAdjustments,
    ) { roomState, latestPlayerName, scoreMap ->
        roomState.copy(
            slots = roomState.slots.map { slot ->
                val withName = if (slot.isLocalPlayer && slot.displayName != latestPlayerName) {
                    slot.copy(displayName = latestPlayerName)
                } else {
                    slot
                }
                withName.copy(cumulativeScore = scoreMap[slot.seatId] ?: withName.cumulativeScore)
            },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = RoomUiState(),
    )

    @Suppress("CyclomaticComplexMethod")
    fun dispatch(action: RoomAction) {
        when (action) {
            RoomAction.StartBluetoothDiscovery -> bluetoothRoomRepository.startDiscovery()
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
            RoomAction.StartGame -> Unit
            RoomAction.ResetScores -> scoreAdjustments.value = emptyMap()
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
        bluetoothRoomRepository.loadBondedDevices()
    }

    fun createHostRoom(hostDeviceName: String) {
        scoreAdjustments.value = emptyMap()
        bluetoothRoomRepository.createHostRoom(
            playerName = playerName.value,
            avatarResId = avatarResId.value,
            hostDeviceName = hostDeviceName,
        )
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

    override fun onCleared() {
        bluetoothRoomRepository.clear()
        super.onCleared()
    }

    companion object {
        fun factory(
            playerPrefsRepository: PlayerPreferencesRepository,
            bluetoothRoomRepository: BluetoothRoomRepository,
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return RoomViewModel(playerPrefsRepository, bluetoothRoomRepository) as T
                }
            }
        }
    }
}
