package com.example.chudadi.ui.room

import com.example.chudadi.model.game.entity.RoundScore

sealed interface RoomAction {
    data object ToggleRule : RoomAction
    data class AddAiToSlot(val slotIndex: Int, val difficulty: RoomAiDifficulty) : RoomAction
    data class RemoveSlotOccupant(val slotIndex: Int) : RoomAction
    data class RequestSwapWithSlot(val targetSlotIndex: Int) : RoomAction
    data class ConfirmSwap(val request: SwapRequest) : RoomAction
    data object DeclineSwap : RoomAction
    data object ToggleReady : RoomAction
    data object StartGame : RoomAction
    data object ResetScores : RoomAction
    data class AccumulateScores(val scores: List<RoundScore>) : RoomAction
    data class OpenAiDialog(val slotIndex: Int) : RoomAction
    data object DismissAiDialog : RoomAction
    data class SelectAiType(val aiType: AIType) : RoomAction
    data object BackToAiTypeSelection : RoomAction
    data class OpenSlotActionMenu(val slotIndex: Int) : RoomAction
    data object DismissSlotActionMenu : RoomAction
    data object ExitRoom : RoomAction
    data object ResetRoom : RoomAction
}
