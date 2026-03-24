package com.example.chudadi.model.game.entity

data class RoundResult(
    val winnerSeatIndex: Int,
    val ranking: List<Int>,
    val summaryLines: List<String>,
)
