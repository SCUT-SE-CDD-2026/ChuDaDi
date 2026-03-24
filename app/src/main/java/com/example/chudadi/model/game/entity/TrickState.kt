package com.example.chudadi.model.game.entity

data class TrickState(
    val leadSeatIndex: Int,
    val lastWinningSeatIndex: Int,
    val currentCombination: PlayCombination?,
    val passCount: Int,
    val roundNumber: Int,
    val tablePlays: Map<Int, PlayCombination> = emptyMap(),
)
