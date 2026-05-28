package com.example.chudadi.model.game.entity

data class RoundScore(
    val seatId: Int,
    val playerName: String,
    val remainingCards: Int,
    val roundScore: Int,
    /** 是否因包赔承担全部罚分 */
    val isBaopei: Boolean = false,
)

data class ScoreSummary(
    val summaryLines: List<String>,
    val roundScores: List<RoundScore>,
)

data class RoundResult(
    val winnerSeatIndex: Int,
    val ranking: List<Int>,
    val scoreSummary: ScoreSummary,
) {
    init {
        require(ranking.isNotEmpty()) { "ranking must not be empty" }
        require(winnerSeatIndex == ranking.first()) {
            "winnerSeatIndex must be the first element in ranking"
        }
        require(scoreSummary.roundScores.size == ranking.size) {
            "roundScores size must match ranking size"
        }
        require(scoreSummary.roundScores.all { it.seatId in ranking }) {
            "all roundScores seatIds must be present in ranking"
        }
    }
}
