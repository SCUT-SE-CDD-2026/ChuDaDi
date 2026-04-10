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
                    slotIndex = 0,
                    occupantType = SlotOccupantType.HUMAN_HOST,
                    displayName = "默认玩家",
                    avatarResId = R.drawable.avatar,
                    connectionStatus = MemberConnectionStatus.READY,
                    isLocalPlayer = true,
                ),
                SlotState(slotIndex = 1),
                SlotState(slotIndex = 2),
                SlotState(slotIndex = 3),
            ),
        ).recalcCanStart(),
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
            val originalSlot = state.slots[slotIndex]
            newSlots[slotIndex] = SlotState(
                slotIndex = slotIndex,
                seatId = originalSlot.seatId,
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
            newSlots[slotIndex] = SlotState(slotIndex = slotIndex, seatId = state.slots[slotIndex].seatId)
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
            swapSlots(localSlot.slotIndex, targetSlotIndex)
        } else if (targetSlot.occupantType == null) {
            // Move to empty slot
            swapSlots(localSlot.slotIndex, targetSlotIndex)
        } else {
            // Request swap with human
            _uiState.update {
                it.copy(
                    pendingSwapRequest = SwapRequest(
                        requesterSlotIndex = localSlot.slotIndex,
                        targetSlotIndex = targetSlotIndex,
                        requesterName = localSlot.displayName,
                    ),
                    showSlotActionMenu = false,
                )
            }
        }
    }

    private fun confirmSwap(request: SwapRequest) {
        swapSlots(request.requesterSlotIndex, request.targetSlotIndex)
        _uiState.update { it.copy(pendingSwapRequest = null) }
    }

    private fun swapSlots(slotIndexA: Int, slotIndexB: Int) {
        _uiState.update { state ->
            val newSlots = state.slots.toMutableList()
            val slotA = newSlots.getOrNull(slotIndexA) ?: return@update state
            val slotB = newSlots.getOrNull(slotIndexB) ?: return@update state
            newSlots[slotIndexA] = slotA.copyOccupantFrom(slotB)
            newSlots[slotIndexB] = slotB.copyOccupantFrom(slotA)
            state.copy(slots = newSlots, showSlotActionMenu = false).recalcCanStart()
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
                val match = scores.firstOrNull { it.seatId == slot.seatId }
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
                    slotIndex = 0,
                    occupantType = SlotOccupantType.HUMAN_HOST,
                    displayName = "默认玩家",
                    avatarResId = R.drawable.avatar,
                    connectionStatus = MemberConnectionStatus.READY,
                    isLocalPlayer = true,
                ),
                SlotState(slotIndex = 1),
                SlotState(slotIndex = 2),
                SlotState(slotIndex = 3),
            ),
        ).recalcCanStart()
    }

    private fun RoomUiState.recalcCanStart(): RoomUiState {
        val allFilled = slots.all { it.occupantType != null }
        val allReady = slots.all { it.connectionStatus == MemberConnectionStatus.READY }
        val slotIndexesStable = slots.withIndex().all { (index, slot) -> slot.slotIndex == index }
        val seatIdsDistinct = slots.map { it.seatId }.distinct().size == slots.size
        return copy(canStartGame = allFilled && allReady && slotIndexesStable && seatIdsDistinct)
    }

    private fun SlotState.copyOccupantFrom(source: SlotState): SlotState {
        return copy(
            occupantType = source.occupantType,
            displayName = source.displayName,
            avatarResId = source.avatarResId,
            connectionStatus = source.connectionStatus,
            aiDifficulty = source.aiDifficulty,
            cumulativeScore = source.cumulativeScore,
            isLocalPlayer = source.isLocalPlayer,
            seatId = source.seatId,
        )
    }
}
