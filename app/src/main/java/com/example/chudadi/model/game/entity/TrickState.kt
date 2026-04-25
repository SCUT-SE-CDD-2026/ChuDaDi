package com.example.chudadi.model.game.entity

data class TrickState(
    val leadSeatIndex: Int,
    val lastWinningSeatIndex: Int,
    val currentCombination: PlayCombination?,
    val passCount: Int,
    val roundNumber: Int,
    val tablePlays: Map<Int, PlayCombination> = emptyMap(),
    val playedCardHistory: Map<Int, List<Card>> = emptyMap(),
    /** 当前轮次中已 pass 的座位索引（与 RLCard trace 语义对齐） */
    val passedSeatIndices: Set<Int> = emptySet(),
)
