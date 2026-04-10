package com.example.chudadi.controller.game

import com.example.chudadi.model.game.entity.SeatControllerType

sealed interface LocalGameAction {
    data class StartLocalMatch(
        val seatConfigs: List<Triple<Int, String, SeatControllerType>>? = null,
        val localSeatId: Int = 0,
    ) : LocalGameAction
    data class ToggleCardSelection(val cardId: String) : LocalGameAction
    data object ClearSelection : LocalGameAction
    data object SubmitSelectedCards : LocalGameAction
    data object PassTurn : LocalGameAction
    data object RestartMatch : LocalGameAction
    data object ExitToHome : LocalGameAction
}
