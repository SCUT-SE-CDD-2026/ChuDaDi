package com.example.chudadi.ai.rulebased.scoring

import com.example.chudadi.ai.rulebased.LOW_SINGLE_RESPONSE_BONUS
import com.example.chudadi.ai.rulebased.PLAY_ALL_HAND_BONUS
import com.example.chudadi.ai.rulebased.RESPONSE_BASE_SCORE
import com.example.chudadi.ai.rulebased.RESPONSE_CONTROL_LOSS_WEIGHT
import com.example.chudadi.ai.rulebased.RuleBasedAiContext
import com.example.chudadi.ai.rulebased.SAME_TYPE_RESPONSE_BONUS
import com.example.chudadi.ai.rulebased.SMALL_BEAT_BONUS
import com.example.chudadi.ai.rulebased.SMALL_BEAT_RANK_GAP
import com.example.chudadi.ai.rulebased.ZERO_SCORE
import com.example.chudadi.model.game.entity.CardRank
import com.example.chudadi.model.game.entity.PlayCombination
import com.example.chudadi.model.game.rule.CombinationType

internal class ResponseScorer(
    private val context: RuleBasedAiContext,
    private val penaltyEvaluator: PenaltyEvaluator = PenaltyEvaluator(context),
) {
    fun score(
        candidate: PlayCombination,
        currentCombination: PlayCombination,
    ): Double {
        val remainingCardCount = context.hand.size - candidate.cardCount

        return RESPONSE_BASE_SCORE +
            computeResponseMatchBonus(candidate, currentCombination) +
            computeAllInBonus(candidate) +
            penaltyEvaluator.computeEndgameBonus(remainingCardCount, candidate) +
            computeResponseEfficiencyBonus(candidate, currentCombination) -
            penaltyEvaluator.computeBreakPenalty(candidate) -
            computeResponseControlLossPenalty(candidate) -
            penaltyEvaluator.computeOverkillPenalty(candidate, currentCombination)
    }

    private fun computeResponseMatchBonus(
        candidate: PlayCombination,
        currentCombination: PlayCombination,
    ): Double =
        if (candidate.type == currentCombination.type && candidate.cardCount == currentCombination.cardCount) {
            SAME_TYPE_RESPONSE_BONUS
        } else {
            ZERO_SCORE
        }

    private fun computeResponseEfficiencyBonus(
        candidate: PlayCombination,
        currentCombination: PlayCombination,
    ): Double {
        var bonus = ZERO_SCORE

        if (
            candidate.type == currentCombination.type &&
            candidate.primaryRank == currentCombination.primaryRank + SMALL_BEAT_RANK_GAP
        ) {
            bonus += SMALL_BEAT_BONUS
        }

        if (
            candidate.type == CombinationType.SINGLE &&
            currentCombination.type == CombinationType.SINGLE &&
            candidate.primaryRank <= CardRank.JACK.strength
        ) {
            bonus += LOW_SINGLE_RESPONSE_BONUS
        }

        return bonus
    }

    private fun computeAllInBonus(candidate: PlayCombination): Double =
        if (candidate.cards.size == context.hand.size) PLAY_ALL_HAND_BONUS else ZERO_SCORE

    private fun computeResponseControlLossPenalty(candidate: PlayCombination): Double =
        penaltyEvaluator.computeControlLossPenalty(candidate) * RESPONSE_CONTROL_LOSS_WEIGHT
}
