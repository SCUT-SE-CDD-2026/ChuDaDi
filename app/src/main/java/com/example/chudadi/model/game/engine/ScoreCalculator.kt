package com.example.chudadi.model.game.engine

import com.example.chudadi.model.game.entity.CardRank
import com.example.chudadi.model.game.entity.CardSuit
import com.example.chudadi.model.game.entity.PlayCombination
import com.example.chudadi.model.game.entity.RoundScore
import com.example.chudadi.model.game.entity.Seat
import com.example.chudadi.model.game.rule.GameRuleSet

object ScoreCalculator {
    fun calculateRoundScores(
        ruleSet: GameRuleSet,
        seats: List<Seat>,
        winnerSeatId: Int,
        winningCombination: PlayCombination?,
        baopeiSeatId: Int? = null,
    ): List<RoundScore> {
        require(seats.isNotEmpty()) { "Seats list must not be empty" }
        require(seats.any { it.seatId == winnerSeatId }) { "Winner seat must exist in seats" }
        require(seats.first { it.seatId == winnerSeatId }.hand.isEmpty()) {
            "Winner seat must have no remaining cards"
        }
        require(baopeiSeatId == null || seats.any { it.seatId == baopeiSeatId }) {
            "Baopei seat must exist in seats"
        }
        require(baopeiSeatId == null || baopeiSeatId != winnerSeatId) {
            "Baopei seat cannot be the winner seat"
        }

        val hasSpadeTwoInWinningPlay = winningCombination?.cards?.any {
            it.rank == CardRank.TWO && it.suit == CardSuit.SPADES
        } == true

        val loserScores = seats
            .filterNot { it.seatId == winnerSeatId }
            .map { seat ->
                val basePenalty = when (ruleSet) {
                    GameRuleSet.SOUTHERN -> calculateSouthernPenalty(
                        remainingCards = seat.hand.size,
                        hasTwoInHand = seat.hand.any { it.rank == CardRank.TWO },
                    )
                    GameRuleSet.NORTHERN -> calculateNorthernPenalty(
                        remainingCards = seat.hand.size,
                    )
                }

                val finalPenalty = if (hasSpadeTwoInWinningPlay) {
                    basePenalty * SPADE_TWO_MULTIPLIER
                } else {
                    basePenalty
                }

                RoundScore(
                    seatId = seat.seatId,
                    playerName = seat.displayName,
                    remainingCards = seat.hand.size,
                    roundScore = -finalPenalty,
                )
            }

        val totalPenalty = loserScores.sumOf { -it.roundScore }

        val finalLoserScores = if (baopeiSeatId != null && baopeiSeatId != winnerSeatId) {
            loserScores.map { score ->
                if (score.seatId == baopeiSeatId) {
                    score.copy(roundScore = -totalPenalty, isBaopei = true)
                } else {
                    score.copy(roundScore = 0)
                }
            }
        } else {
            loserScores
        }

        val winner = seats.first { it.seatId == winnerSeatId }

        val result = listOf(
            RoundScore(
                seatId = winner.seatId,
                playerName = winner.displayName,
                remainingCards = winner.hand.size,
                roundScore = totalPenalty,
            ),
        ) + finalLoserScores

        check(result.sumOf { it.roundScore } == 0) { "Round scores must sum to zero" }
        return result
    }

    private fun calculateSouthernPenalty(
        remainingCards: Int,
        hasTwoInHand: Boolean,
    ): Int {
        val basePenalty = remainingCards
        return if (hasTwoInHand) basePenalty * HOLDING_TWO_MULTIPLIER else basePenalty
    }

    private fun calculateNorthernPenalty(remainingCards: Int): Int {
        return when {
            remainingCards == FULL_HAND_CARD_COUNT -> 52
            remainingCards >= DOUBLE_PENALTY_THRESHOLD -> remainingCards * 2
            else -> remainingCards
        }
    }

    private const val DOUBLE_PENALTY_THRESHOLD = 10
    private const val FULL_HAND_CARD_COUNT = 13
    /** 获胜牌型包含黑桃2时，所有计分翻倍 */
    private const val SPADE_TWO_MULTIPLIER = 2
    /** 南方规则：剩余手牌中有2时，该玩家罚分翻倍 */
    private const val HOLDING_TWO_MULTIPLIER = 2
}
