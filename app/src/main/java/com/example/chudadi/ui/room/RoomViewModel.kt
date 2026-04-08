@file:Suppress("TooManyFunctions")

package com.example.chudadi.ui.room
import androidx.lifecycle.ViewModel
import com.example.chudadi.R
import com.example.chudadi.model.game.entity.RoundScore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class RoomViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(
        RoomUiState(
            isHost = true,
            roomName = "我的房间",
            hostDeviceName = "本机",
            slots = listOf(
                SlotState(
                    seatIndex = 0,
                    occupantType = SlotOccupantType.HUMAN_HOST,
                    displayName = "默认玩家",
                    avatarResId = R.drawable.avatar,
                    connectionStatus = MemberConnectionStatus.READY,
                    isLocalPlayer = true,
                ),
                SlotState(seatIndex = 1),
                SlotState(seatIndex = 2),
                SlotState(seatIndex = 3),
            ),
        ),
    )
    val uiState: StateFlow<RoomUiState> = _uiState.asStateFlow()

    @Suppress("CyclomaticComplexMethod")
    fun dispatch(action: RoomAction) {
        when (action) {
            is RoomAction.ToggleRule -> toggleRule()
            is RoomAction.AddAiToSlot -> addAiToSlot(action.slotIndex, action.difficulty)
            is RoomAction.RemoveSlotOccupant -> removeSlotOccupant(action.slotIndex)
            is RoomAction.RequestSwapWithSlot -> requestSwap(action.targetSlotIndex)
            is RoomAction.ConfirmSwap -> confirmSwap(action.request)
            is RoomAction.DeclineSwap -> _uiState.update { it.copy(pendingSwapRequest = null) }
            is RoomAction.ToggleReady -> toggleReady()
            is RoomAction.StartGame -> { /* handled by NavGraph */ }
            is RoomAction.ResetScores -> resetScores()
            is RoomAction.AccumulateScores -> accumulateScores(action.scores)
            is RoomAction.OpenAiDialog -> _uiState.update {
                it.copy(showAiDifficultyDialog = true, aiDialogTargetSlot = action.slotIndex)
            }
            is RoomAction.DismissAiDialog -> _uiState.update {
                it.copy(showAiDifficultyDialog = false, aiDialogTargetSlot = -1)
            }
            is RoomAction.OpenSlotActionMenu -> _uiState.update {
                it.copy(showSlotActionMenu = true, slotActionMenuTarget = action.slotIndex)
            }
            is RoomAction.DismissSlotActionMenu -> _uiState.update {
                it.copy(showSlotActionMenu = false, slotActionMenuTarget = -1)
            }
            is RoomAction.ExitRoom -> { /* handled by NavGraph */ }
            is RoomAction.ResetRoom -> resetRoom()
        }
    }

    private fun toggleRule() {
        _uiState.update { state ->
            val next = if (state.currentRule == GameRuleDisplay.SOUTHERN) {
                GameRuleDisplay.NORTHERN
            } else {
                GameRuleDisplay.SOUTHERN
            }
            state.copy(currentRule = next)
        }
    }

    private fun addAiToSlot(slotIndex: Int, difficulty: AiDifficulty) {
        _uiState.update { state ->
            val usedNumbers = state.slots
                .filter { it.occupantType == SlotOccupantType.AI }
                .mapNotNull { it.displayName.substringAfterLast(' ').toIntOrNull() }
                .toSet()
            val aiNumber = generateSequence(1) { it + 1 }.first { it !in usedNumbers }
            val newSlots = state.slots.toMutableList()
            newSlots[slotIndex] = SlotState(
                seatIndex = slotIndex,
                occupantType = SlotOccupantType.AI,
                displayName = "AI(${difficulty.label}) $aiNumber",
                avatarResId = R.drawable.avatar,
                connectionStatus = MemberConnectionStatus.READY,
                aiDifficulty = difficulty,
            )
            state.copy(
                slots = newSlots,
                showAiDifficultyDialog = false,
                aiDialogTargetSlot = -1,
            ).recalcCanStart()
        }
    }

    private fun removeSlotOccupant(slotIndex: Int) {
        _uiState.update { state ->
            val newSlots = state.slots.toMutableList()
            newSlots[slotIndex] = SlotState(seatIndex = slotIndex)
            state.copy(
                slots = newSlots,
                showSlotActionMenu = false,
                slotActionMenuTarget = -1,
            ).recalcCanStart()
        }
    }

    private fun requestSwap(targetSlotIndex: Int) {
        val state = _uiState.value
        val localSlot = state.slots.firstOrNull { it.isLocalPlayer } ?: return
        val targetSlot = state.slots.getOrNull(targetSlotIndex) ?: return

        if (targetSlot.occupantType == SlotOccupantType.AI) {
            // AI swap is immediate
            swapSlots(localSlot.seatIndex, targetSlotIndex)
        } else if (targetSlot.occupantType == null) {
            // Move to empty slot
            swapSlots(localSlot.seatIndex, targetSlotIndex)
        } else {
            // Request swap with human
            _uiState.update {
                it.copy(
                    pendingSwapRequest = SwapRequest(
                        requesterSeatIndex = localSlot.seatIndex,
                        targetSeatIndex = targetSlotIndex,
                        requesterName = localSlot.displayName,
                    ),
                    showSlotActionMenu = false,
                )
            }
        }
    }

    private fun confirmSwap(request: SwapRequest) {
        swapSlots(request.requesterSeatIndex, request.targetSeatIndex)
        _uiState.update { it.copy(pendingSwapRequest = null) }
    }

    private fun swapSlots(seatIndexA: Int, seatIndexB: Int) {
        _uiState.update { state ->
            val newSlots = state.slots.toMutableList()
            val posA = newSlots.indexOfFirst { it.seatIndex == seatIndexA }
            val posB = newSlots.indexOfFirst { it.seatIndex == seatIndexB }
            if (posA < 0 || posB < 0) return@update state
            val tmp = newSlots[posA]
            newSlots[posA] = newSlots[posB]
            newSlots[posB] = tmp
            state.copy(slots = newSlots, showSlotActionMenu = false)
        }
    }

    private fun toggleReady() {
        _uiState.update { state ->
            val newSlots = state.slots.toMutableList()
            val localIdx = newSlots.indexOfFirst { it.isLocalPlayer }
            if (localIdx >= 0) {
                val current = newSlots[localIdx]
                val newStatus = if (current.connectionStatus == MemberConnectionStatus.READY) {
                    MemberConnectionStatus.NOT_READY
                } else {
                    MemberConnectionStatus.READY
                }
                newSlots[localIdx] = current.copy(connectionStatus = newStatus)
            }
            state.copy(slots = newSlots).recalcCanStart()
        }
    }

    private fun resetScores() {
        _uiState.update { state ->
            state.copy(slots = state.slots.map { it.copy(cumulativeScore = 0) })
        }
    }

    private fun accumulateScores(scores: List<RoundScore>) {
        _uiState.update { state ->
            val newSlots = state.slots.map { slot ->
                val match = scores.firstOrNull { it.seatId == slot.seatIndex }
                if (match != null) slot.copy(cumulativeScore = slot.cumulativeScore + match.roundScore)
                else slot
            }
            state.copy(slots = newSlots)
        }
    }

    private fun resetRoom() {
        _uiState.value = RoomUiState(
            isHost = true,
            roomName = "我的房间",
            hostDeviceName = "本机",
            slots = listOf(
                SlotState(
                    seatIndex = 0,
                    occupantType = SlotOccupantType.HUMAN_HOST,
                    displayName = "默认玩家",
                    avatarResId = R.drawable.avatar,
                    connectionStatus = MemberConnectionStatus.READY,
                    isLocalPlayer = true,
                ),
                SlotState(seatIndex = 1),
                SlotState(seatIndex = 2),
                SlotState(seatIndex = 3),
            ),
        )
    }

    private fun RoomUiState.recalcCanStart(): RoomUiState {
        val allFilled = slots.all { it.occupantType != null }
        val allReady = slots.all { it.connectionStatus == MemberConnectionStatus.READY }
        return copy(canStartGame = allFilled && allReady)
    }
}
