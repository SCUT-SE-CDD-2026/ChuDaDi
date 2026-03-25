package com.example.chudadi.ui.game

data class GameScreenActions(
    val onToggleCardSelection: (String) -> Unit,
    val onClearSelection: () -> Unit,
    val onSubmitSelectedCards: () -> Unit,
    val onPassTurn: () -> Unit,
)
