package com.example.chudadi.controller.game

sealed interface LocalGameAction {
    data object StartLocalMatch : LocalGameAction
    data class ToggleCardSelection(
        val cardId: String,
    ) : LocalGameAction

    data object ClearSelection : LocalGameAction
    data object SubmitSelectedCards : LocalGameAction
    data object PassTurn : LocalGameAction
    data object RestartMatch : LocalGameAction
    data object ExitToHome : LocalGameAction
}
