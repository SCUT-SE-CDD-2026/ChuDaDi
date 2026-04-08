package com.example.chudadi.ai.rulebased.scoring

import com.example.chudadi.ai.rulebased.BOMB_PASS_INCREASE
import com.example.chudadi.ai.rulebased.BOMB_VS_BOMB_PASS_INCREASE
import com.example.chudadi.ai.rulebased.DANGER_OPPONENT_CARD_COUNT
import com.example.chudadi.ai.rulebased.DANGER_OPPONENT_PASS_REDUCTION
import com.example.chudadi.ai.rulebased.HIGH_BOMB_PASS_INCREASE
import com.example.chudadi.ai.rulebased.LATE_GAME_HAND_THRESHOLD
import com.example.chudadi.ai.rulebased.LATE_GAME_PASS_REDUCTION
import com.example.chudadi.ai.rulebased.MAX_PASS_PROBABILITY
import com.example.chudadi.ai.rulebased.MIN_PASS_PROBABILITY
import com.example.chudadi.ai.rulebased.NON_SINGLE_TWO_PASS_INCREASE
import com.example.chudadi.ai.rulebased.NORTHERN_BOMB_PASS_INCREASE
import com.example.chudadi.ai.rulebased.PASS_PROBABILITY_HIGH_SCORE
import com.example.chudadi.ai.rulebased.PASS_PROBABILITY_LOW_SCORE
import com.example.chudadi.ai.rulebased.PASS_PROBABILITY_MEDIUM_SCORE
import com.example.chudadi.ai.rulebased.PASS_PROBABILITY_TOP_SCORE
import com.example.chudadi.ai.rulebased.PASS_PROBABILITY_VERY_LOW_SCORE
import com.example.chudadi.ai.rulebased.PASS_SCORE_THRESHOLD_HIGH
import com.example.chudadi.ai.rulebased.PASS_SCORE_THRESHOLD_LOW
import com.example.chudadi.ai.rulebased.PASS_SCORE_THRESHOLD_MEDIUM
import com.example.chudadi.ai.rulebased.PASS_SCORE_THRESHOLD_VERY_LOW
import com.example.chudadi.ai.rulebased.PRESSURE_OPPONENT_CARD_COUNT
import com.example.chudadi.ai.rulebased.PRESSURE_OPPONENT_PASS_REDUCTION
import com.example.chudadi.ai.rulebased.RuleBasedAiContext
import com.example.chudadi.ai.rulebased.SINGLE_ACE_PASS_INCREASE
import com.example.chudadi.ai.rulebased.SINGLE_KING_PASS_INCREASE
import com.example.chudadi.ai.rulebased.SINGLE_TWO_PASS_INCREASE
import com.example.chudadi.ai.rulebased.ZERO_SCORE
import com.example.chudadi.ai.rulebased.getNextActiveOpponentCardCount
import com.example.chudadi.model.game.entity.CardRank
import com.example.chudadi.model.game.entity.PlayCombination
import com.example.chudadi.model.game.rule.CombinationType

internal class PassProbabilityEvaluator(
    private val context: RuleBasedAiContext,
) {
    fun compute(
        bestCandidate: PlayCombination,
        bestScore: Double,
    ): Double {
        val remainingAfterBest = context.seat.hand.size - bestCandidate.cardCount
        val probability =
            basePassProbabilityFromScore(bestScore) +
                computePassEndgameAdjustment(remainingAfterBest) +
                computePassOpponentPressureAdjustment() +
                computePassBombAdjustment(bestCandidate) +
                computePassControlCardAdjustment(bestCandidate)

        return probability.coerceIn(MIN_PASS_PROBABILITY, MAX_PASS_PROBABILITY)
    }

    private fun basePassProbabilityFromScore(bestScore: Double): Double =
        when {
            bestScore < PASS_SCORE_THRESHOLD_VERY_LOW -> PASS_PROBABILITY_VERY_LOW_SCORE
            bestScore < PASS_SCORE_THRESHOLD_LOW -> PASS_PROBABILITY_LOW_SCORE
            bestScore < PASS_SCORE_THRESHOLD_MEDIUM -> PASS_PROBABILITY_MEDIUM_SCORE
            bestScore < PASS_SCORE_THRESHOLD_HIGH -> PASS_PROBABILITY_HIGH_SCORE
            else -> PASS_PROBABILITY_TOP_SCORE
        }

    private fun computePassEndgameAdjustment(remainingAfterBest: Int): Double =
        if (remainingAfterBest <= LATE_GAME_HAND_THRESHOLD) -LATE_GAME_PASS_REDUCTION else ZERO_SCORE

    private fun computePassOpponentPressureAdjustment(): Double {
        val nextOpponentCount = getNextActiveOpponentCardCount(context.match, context.seatIndex)

        return when {
            nextOpponentCount == null -> ZERO_SCORE
            nextOpponentCount <= DANGER_OPPONENT_CARD_COUNT -> -DANGER_OPPONENT_PASS_REDUCTION
            nextOpponentCount <= PRESSURE_OPPONENT_CARD_COUNT -> -PRESSURE_OPPONENT_PASS_REDUCTION
            else -> ZERO_SCORE
        }
    }

    private fun computePassBombAdjustment(bestCandidate: PlayCombination): Double {
        if (!context.rules.isBomb(bestCandidate.type)) {
            return ZERO_SCORE
        }

        var adjustment = BOMB_PASS_INCREASE
        if (context.rules.mustBeatIfPossible) {
            adjustment += NORTHERN_BOMB_PASS_INCREASE
        }
        if (context.currentCombination != null && context.rules.isBomb(context.currentCombination.type)) {
            adjustment += BOMB_VS_BOMB_PASS_INCREASE
        }
        if (bestCandidate.primaryRank >= CardRank.KING.strength) {
            adjustment += HIGH_BOMB_PASS_INCREASE
        }

        return adjustment
    }

    private fun computePassControlCardAdjustment(bestCandidate: PlayCombination): Double {
        if (bestCandidate.type == CombinationType.SINGLE) {
            return when (bestCandidate.cards.single().rank) {
                CardRank.TWO -> SINGLE_TWO_PASS_INCREASE
                CardRank.ACE -> SINGLE_ACE_PASS_INCREASE
                CardRank.KING -> SINGLE_KING_PASS_INCREASE
                else -> ZERO_SCORE
            }
        }

        return if (bestCandidate.cards.any { it.rank == CardRank.TWO }) {
            NON_SINGLE_TWO_PASS_INCREASE
        } else {
            ZERO_SCORE
        }
    }
}
