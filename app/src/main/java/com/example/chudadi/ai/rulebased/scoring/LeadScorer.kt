package com.example.chudadi.ai.rulebased.scoring

import com.example.chudadi.ai.rulebased.EARLY_BOMB_PENALTY
import com.example.chudadi.ai.rulebased.EXTRA_SAFE_CARDS_AFTER_BOMB
import com.example.chudadi.ai.rulebased.LEAD_BASE_SCORE_FLUSH
import com.example.chudadi.ai.rulebased.LEAD_BASE_SCORE_FOUR_OF_A_KIND_BOMB
import com.example.chudadi.ai.rulebased.LEAD_BASE_SCORE_FOUR_WITH_ONE
import com.example.chudadi.ai.rulebased.LEAD_BASE_SCORE_FOUR_WITH_TWO
import com.example.chudadi.ai.rulebased.LEAD_BASE_SCORE_FULL_HOUSE
import com.example.chudadi.ai.rulebased.LEAD_BASE_SCORE_PAIR
import com.example.chudadi.ai.rulebased.LEAD_BASE_SCORE_SINGLE
import com.example.chudadi.ai.rulebased.LEAD_BASE_SCORE_STRAIGHT
import com.example.chudadi.ai.rulebased.LEAD_BASE_SCORE_STRAIGHT_FLUSH
import com.example.chudadi.ai.rulebased.LEAD_BASE_SCORE_TRIPLE
import com.example.chudadi.ai.rulebased.LOW_SINGLETON_LEAD_BONUS
import com.example.chudadi.ai.rulebased.NATURAL_PAIR_LEAD_BONUS
import com.example.chudadi.ai.rulebased.NON_SINGLE_LEAD_RANK_WEIGHT
import com.example.chudadi.ai.rulebased.OPENING_BOMB_PENALTY
import com.example.chudadi.ai.rulebased.OPENING_CARD
import com.example.chudadi.ai.rulebased.OPENING_FIVE_CARD_STRUCTURE_BONUS
import com.example.chudadi.ai.rulebased.OPENING_PAIR_BONUS
import com.example.chudadi.ai.rulebased.OPENING_SINGLE_PENALTY
import com.example.chudadi.ai.rulebased.OPENING_TRIPLE_BONUS
import com.example.chudadi.ai.rulebased.PAIR_COUNT
import com.example.chudadi.ai.rulebased.PLAY_ALL_HAND_BONUS
import com.example.chudadi.ai.rulebased.RuleBasedAiContext
import com.example.chudadi.ai.rulebased.SINGLETON_COUNT
import com.example.chudadi.ai.rulebased.SINGLE_LEAD_RANK_WEIGHT
import com.example.chudadi.ai.rulebased.ZERO_COUNT
import com.example.chudadi.ai.rulebased.ZERO_SCORE
import com.example.chudadi.model.game.entity.CardRank
import com.example.chudadi.model.game.entity.PlayCombination
import com.example.chudadi.model.game.rule.CombinationType

internal class LeadScorer(
    private val context: RuleBasedAiContext,
    private val penaltyEvaluator: PenaltyEvaluator = PenaltyEvaluator(context),
) {
    fun score(
        candidate: PlayCombination,
        requiresOpeningThree: Boolean,
    ): Double {
        val remainingCardCount = context.hand.size - candidate.cardCount

        return computeLeadBaseScore(candidate) -
            computeLeadRankPenalty(candidate) -
            penaltyEvaluator.computeBreakPenalty(candidate) -
            penaltyEvaluator.computeControlLossPenalty(candidate) +
            computeAllInBonus(candidate) +
            penaltyEvaluator.computeEndgameBonus(remainingCardCount, candidate) +
            computeLeadStructureBonus(candidate) +
            computeOpeningAdjustment(candidate, requiresOpeningThree) -
            computeEarlyBombPenalty(candidate)
    }

    private fun computeLeadBaseScore(candidate: PlayCombination): Double =
        when (candidate.type) {
            CombinationType.FULL_HOUSE -> LEAD_BASE_SCORE_FULL_HOUSE
            CombinationType.STRAIGHT -> LEAD_BASE_SCORE_STRAIGHT
            CombinationType.FLUSH -> LEAD_BASE_SCORE_FLUSH
            CombinationType.TRIPLE -> LEAD_BASE_SCORE_TRIPLE
            CombinationType.PAIR -> LEAD_BASE_SCORE_PAIR
            CombinationType.SINGLE -> LEAD_BASE_SCORE_SINGLE
            CombinationType.STRAIGHT_FLUSH -> LEAD_BASE_SCORE_STRAIGHT_FLUSH
            CombinationType.FOUR_OF_A_KIND_BOMB -> LEAD_BASE_SCORE_FOUR_OF_A_KIND_BOMB
            CombinationType.FOUR_WITH_ONE -> LEAD_BASE_SCORE_FOUR_WITH_ONE
            CombinationType.FOUR_WITH_TWO -> LEAD_BASE_SCORE_FOUR_WITH_TWO
        }

    private fun computeLeadRankPenalty(candidate: PlayCombination): Double {
        val rankWeight =
            if (candidate.type == CombinationType.SINGLE) {
                SINGLE_LEAD_RANK_WEIGHT
            } else {
                NON_SINGLE_LEAD_RANK_WEIGHT
            }
        return candidate.primaryRank * rankWeight
    }

    private fun computeAllInBonus(candidate: PlayCombination): Double =
        if (candidate.cards.size == context.hand.size) PLAY_ALL_HAND_BONUS else ZERO_SCORE

    private fun computeLeadStructureBonus(candidate: PlayCombination): Double =
        when (candidate.type) {
            CombinationType.SINGLE -> computeLowSingletonLeadBonus(candidate)
            CombinationType.PAIR -> computeNaturalPairLeadBonus(candidate)
            else -> ZERO_SCORE
        }

    private fun computeLowSingletonLeadBonus(candidate: PlayCombination): Double {
        val card = candidate.cards.singleOrNull() ?: return ZERO_SCORE
        val rankCount = context.handProfile.rankCounts[card.rank] ?: ZERO_COUNT
        return if (rankCount == SINGLETON_COUNT && card.rank.strength <= CardRank.NINE.strength) {
            LOW_SINGLETON_LEAD_BONUS
        } else {
            ZERO_SCORE
        }
    }

    private fun computeNaturalPairLeadBonus(candidate: PlayCombination): Double {
        val rank = candidate.cards.first().rank
        val rankCount = context.handProfile.rankCounts[rank] ?: ZERO_COUNT
        return if (rankCount == PAIR_COUNT && rank.strength <= CardRank.TEN.strength) {
            NATURAL_PAIR_LEAD_BONUS
        } else {
            ZERO_SCORE
        }
    }

    private fun computeOpeningAdjustment(
        candidate: PlayCombination,
        requiresOpeningThree: Boolean,
    ): Double {
        if (!requiresOpeningThree || candidate.cards.none { it.id == OPENING_CARD.id }) {
            return ZERO_SCORE
        }

        return when (candidate.type) {
            CombinationType.SINGLE -> OPENING_SINGLE_PENALTY
            CombinationType.PAIR -> OPENING_PAIR_BONUS
            CombinationType.TRIPLE -> OPENING_TRIPLE_BONUS
            CombinationType.STRAIGHT,
            CombinationType.FLUSH,
            CombinationType.FULL_HOUSE,
            CombinationType.STRAIGHT_FLUSH,
            -> OPENING_FIVE_CARD_STRUCTURE_BONUS
            CombinationType.FOUR_OF_A_KIND_BOMB,
            CombinationType.FOUR_WITH_ONE,
            CombinationType.FOUR_WITH_TWO,
            -> OPENING_BOMB_PENALTY
        }
    }

    private fun computeEarlyBombPenalty(candidate: PlayCombination): Double =
        if (
            context.rules.isBomb(candidate.type) &&
            context.hand.size > candidate.cardCount + EXTRA_SAFE_CARDS_AFTER_BOMB
        ) {
            EARLY_BOMB_PENALTY
        } else {
            ZERO_SCORE
        }
}
