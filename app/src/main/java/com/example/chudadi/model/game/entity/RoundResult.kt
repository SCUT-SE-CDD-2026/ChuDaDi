package com.example.chudadi.model.game.entity

data class RoundScore(
    val seatId: Int,
    val playerName: String,
    val remainingCards: Int,
    val roundScore: Int,
)

data class ScoreSummary(
    val summaryLines: List<String>,
    val bombCount: Int,
    val roundScores: List<RoundScore>,
)

data class RoundResult(
    val winnerSeatIndex: Int,
    val ranking: List<Int>,
    val scoreSummary: ScoreSummary,
)
