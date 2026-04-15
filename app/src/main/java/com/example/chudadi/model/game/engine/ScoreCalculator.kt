package com.example.chudadi.model.game.engine

import com.example.chudadi.model.game.entity.CardRank
import com.example.chudadi.model.game.entity.RoundScore
import com.example.chudadi.model.game.entity.Seat
import com.example.chudadi.model.game.rule.GameRuleSet

object ScoreCalculator {
    fun calculateRoundScores(
        ruleSet: GameRuleSet,
        seats: List<Seat>,
        winnerSeatId: Int,
        baoPaySeatId: Int? = null,
    ): List<RoundScore> {
        val loserPenalties = seats
            .filterNot { it.seatId == winnerSeatId }
            .associate { seat ->
                seat.seatId to when (ruleSet) {
                    GameRuleSet.SOUTHERN -> calculateSouthernPenalty(seat)
                    GameRuleSet.NORTHERN -> calculateNorthernPenalty(seat.hand.size)
                }
            }

        val totalLoserPenalty = loserPenalties.values.sum()
        val loserScores = seats
            .filterNot { it.seatId == winnerSeatId }
            .map { seat ->
                val roundScore = when {
                    baoPaySeatId == null -> -loserPenalties.getValue(seat.seatId)
                    seat.seatId == baoPaySeatId -> -totalLoserPenalty
                    else -> 0
                }
                RoundScore(
                    seatId = seat.seatId,
                    playerName = seat.displayName,
                    remainingCards = seat.hand.size,
                    roundScore = roundScore,
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

    private fun calculateSouthernPenalty(seat: Seat): Int {
        val basePenalty = seat.hand.size
        val containsTwo = seat.hand.any { it.rank == CardRank.TWO }
        return if (containsTwo) basePenalty * 2 else basePenalty
    }

    private fun calculateNorthernPenalty(remainingCards: Int): Int {
        return when {
            remainingCards == FULL_HAND_CARD_COUNT -> remainingCards * 3
            remainingCards >= DOUBLE_PENALTY_THRESHOLD -> remainingCards * 2
            else -> remainingCards
        }
    }

    private const val DOUBLE_PENALTY_THRESHOLD = 10
    private const val FULL_HAND_CARD_COUNT = 13
}
