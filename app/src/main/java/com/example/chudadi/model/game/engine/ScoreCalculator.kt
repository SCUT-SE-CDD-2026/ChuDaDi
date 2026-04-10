package com.example.chudadi.model.game.engine

import com.example.chudadi.model.game.entity.RoundScore
import com.example.chudadi.model.game.entity.Seat
import com.example.chudadi.model.game.rule.GameRuleSet

object ScoreCalculator {
    fun calculateRoundScores(
        ruleSet: GameRuleSet,
        seats: List<Seat>,
        winnerSeatId: Int,
        totalBombCount: Int,
    ): List<RoundScore> {
        val loserScores = seats
            .filterNot { it.seatId == winnerSeatId }
            .map { seat ->
                val penalty = when (ruleSet) {
                    GameRuleSet.SOUTHERN -> calculateSouthernPenalty(
                        remainingCards = seat.hand.size,
                        totalBombCount = totalBombCount,
                    )
                    GameRuleSet.NORTHERN -> calculateNorthernPenalty(
                        remainingCards = seat.hand.size,
                    )
                }

                RoundScore(
                    seatId = seat.seatId,
                    playerName = seat.displayName,
                    remainingCards = seat.hand.size,
                    roundScore = -penalty,
                )
            }

        val winner = seats.first { it.seatId == winnerSeatId }
        val winnerScore = loserScores.sumOf { -it.roundScore }

        return listOf(
            RoundScore(
                seatId = winner.seatId,
                playerName = winner.displayName,
                remainingCards = winner.hand.size,
                roundScore = winnerScore,
            ),
        ) + loserScores
    }

    private fun calculateSouthernPenalty(
        remainingCards: Int,
        totalBombCount: Int,
    ): Int {
        return remainingCards * (1 shl totalBombCount)
    }

    private fun calculateNorthernPenalty(remainingCards: Int): Int {
        return when {
            remainingCards >= FULL_HAND_CARD_COUNT -> {
                if (remainingCards == FULL_HAND_CARD_COUNT) {
                    NO_PLAY_PENALTY
                } else {
                    remainingCards * 3
                }
            }
            remainingCards >= DOUBLE_PENALTY_THRESHOLD -> remainingCards * 2
            else -> remainingCards
        }
    }

    private const val DOUBLE_PENALTY_THRESHOLD = 10
    private const val FULL_HAND_CARD_COUNT = 13
    private const val NO_PLAY_PENALTY = 39
}
