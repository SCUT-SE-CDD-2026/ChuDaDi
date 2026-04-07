package com.example.chudadi.ai.rulebased.scoring

import com.example.chudadi.ai.rulebased.BOMB_BREAK_BASE_PENALTY
import com.example.chudadi.ai.rulebased.BOMB_CONTROL_LOSS_FACTOR
import com.example.chudadi.ai.rulebased.BOMB_OVERKILL_PENALTY
import com.example.chudadi.ai.rulebased.BOMB_OVERKILL_RANK_FACTOR
import com.example.chudadi.ai.rulebased.CONTROL_LOSS_ACE_PENALTY
import com.example.chudadi.ai.rulebased.CONTROL_LOSS_KING_PENALTY
import com.example.chudadi.ai.rulebased.CONTROL_LOSS_TWO_PENALTY
import com.example.chudadi.ai.rulebased.DANGER_OPPONENT_BONUS
import com.example.chudadi.ai.rulebased.DANGER_OPPONENT_CARD_COUNT
import com.example.chudadi.ai.rulebased.DANGER_OPPONENT_CARD_WEIGHT
import com.example.chudadi.ai.rulebased.FAST_FINISH_COMBINATION_BONUS
import com.example.chudadi.ai.rulebased.FAST_FINISH_COMBINATION_THRESHOLD
import com.example.chudadi.ai.rulebased.FAST_FINISH_HAND_THRESHOLD
import com.example.chudadi.ai.rulebased.FINISHING_PLAY_BONUS
import com.example.chudadi.ai.rulebased.FIVE_CARD_CONTROL_LOSS_FACTOR
import com.example.chudadi.ai.rulebased.FIVE_CARD_LINK_UNIT
import com.example.chudadi.ai.rulebased.FIVE_CARD_OVERKILL_RANK_FACTOR
import com.example.chudadi.ai.rulebased.FOUR_OF_A_KIND_COUNT
import com.example.chudadi.ai.rulebased.GLOBAL_DANGER_OPPONENT_BONUS
import com.example.chudadi.ai.rulebased.HIGH_SMALL_COMBINATION_CARD_COUNT
import com.example.chudadi.ai.rulebased.HIGH_SMALL_COMBINATION_RANK_WEIGHT
import com.example.chudadi.ai.rulebased.LATE_GAME_HAND_THRESHOLD
import com.example.chudadi.ai.rulebased.LOW_REMAINING_BONUS
import com.example.chudadi.ai.rulebased.MID_GAME_HAND_THRESHOLD
import com.example.chudadi.ai.rulebased.MID_REMAINING_BONUS
import com.example.chudadi.ai.rulebased.ONE_RANK_STEP
import com.example.chudadi.ai.rulebased.PAIR_BREAK_FIVE_CARD_LINK_WEIGHT
import com.example.chudadi.ai.rulebased.PAIR_BREAK_FOUR_OF_A_KIND_PENALTY
import com.example.chudadi.ai.rulebased.PAIR_BREAK_TRIPLE_PENALTY
import com.example.chudadi.ai.rulebased.PAIR_CONTROL_LOSS_FACTOR
import com.example.chudadi.ai.rulebased.PAIR_COUNT
import com.example.chudadi.ai.rulebased.PAIR_OVERKILL_RANK_FACTOR
import com.example.chudadi.ai.rulebased.PRESSURE_OPPONENT_BONUS
import com.example.chudadi.ai.rulebased.PRESSURE_OPPONENT_CARD_COUNT
import com.example.chudadi.ai.rulebased.RuleBasedAiContext
import com.example.chudadi.ai.rulebased.SINGLE_BREAK_FIVE_CARD_PENALTY
import com.example.chudadi.ai.rulebased.SINGLE_BREAK_PAIR_PENALTY
import com.example.chudadi.ai.rulebased.SINGLE_BREAK_TRIPLE_PENALTY
import com.example.chudadi.ai.rulebased.SINGLE_CONTROL_LOSS_FACTOR
import com.example.chudadi.ai.rulebased.SINGLE_OVERKILL_RANK_FACTOR
import com.example.chudadi.ai.rulebased.STRUCTURE_PLAY_BASE_BREAK_PENALTY
import com.example.chudadi.ai.rulebased.SUIT_GAP_OVERKILL_WEIGHT
import com.example.chudadi.ai.rulebased.TRIPLE_BREAK_FOUR_OF_A_KIND_PENALTY
import com.example.chudadi.ai.rulebased.TRIPLE_BREAK_OTHER_PAIR_PENALTY
import com.example.chudadi.ai.rulebased.TRIPLE_CONTROL_LOSS_FACTOR
import com.example.chudadi.ai.rulebased.TRIPLE_COUNT
import com.example.chudadi.ai.rulebased.TRIPLE_OVERKILL_RANK_FACTOR
import com.example.chudadi.ai.rulebased.TYPE_GAP_OVERKILL_WEIGHT
import com.example.chudadi.ai.rulebased.VERY_LOW_REMAINING_BONUS
import com.example.chudadi.ai.rulebased.ZERO_COUNT
import com.example.chudadi.ai.rulebased.ZERO_SCORE
import com.example.chudadi.ai.rulebased.getNextActiveOpponentCardCount
import com.example.chudadi.model.game.entity.CardRank
import com.example.chudadi.model.game.entity.PlayCombination
import com.example.chudadi.model.game.entity.SeatStatus
import com.example.chudadi.model.game.rule.CombinationType

internal class PenaltyEvaluator(
    private val context: RuleBasedAiContext,
) {
    fun computeBreakPenalty(candidate: PlayCombination): Double =
        when (candidate.type) {
            CombinationType.SINGLE -> computeSingleBreakPenalty(candidate)
            CombinationType.PAIR -> computePairBreakPenalty(candidate)
            CombinationType.TRIPLE -> computeTripleBreakPenalty(candidate)
            CombinationType.STRAIGHT,
            CombinationType.FLUSH,
            CombinationType.FULL_HOUSE,
            CombinationType.STRAIGHT_FLUSH,
            -> STRUCTURE_PLAY_BASE_BREAK_PENALTY
            CombinationType.FOUR_OF_A_KIND_BOMB,
            CombinationType.FOUR_WITH_ONE,
            CombinationType.FOUR_WITH_TWO,
            -> BOMB_BREAK_BASE_PENALTY
        }

    fun computeControlLossPenalty(candidate: PlayCombination): Double {
        val typeFactor =
            when (candidate.type) {
                CombinationType.SINGLE -> SINGLE_CONTROL_LOSS_FACTOR
                CombinationType.PAIR -> PAIR_CONTROL_LOSS_FACTOR
                CombinationType.TRIPLE -> TRIPLE_CONTROL_LOSS_FACTOR
                CombinationType.STRAIGHT,
                CombinationType.FLUSH,
                CombinationType.FULL_HOUSE,
                CombinationType.STRAIGHT_FLUSH,
                -> FIVE_CARD_CONTROL_LOSS_FACTOR
                CombinationType.FOUR_OF_A_KIND_BOMB,
                CombinationType.FOUR_WITH_ONE,
                CombinationType.FOUR_WITH_TWO,
                -> BOMB_CONTROL_LOSS_FACTOR
            }

        var penalty = ZERO_SCORE
        candidate.cards.forEach { card ->
            penalty +=
                when (card.rank) {
                    CardRank.TWO -> CONTROL_LOSS_TWO_PENALTY
                    CardRank.ACE -> CONTROL_LOSS_ACE_PENALTY
                    CardRank.KING -> CONTROL_LOSS_KING_PENALTY
                    else -> ZERO_SCORE
                }
        }

        if (
            candidate.cardCount <= HIGH_SMALL_COMBINATION_CARD_COUNT &&
            candidate.primaryRank >= CardRank.KING.strength
        ) {
            penalty +=
                (candidate.primaryRank - CardRank.KING.strength + ONE_RANK_STEP) *
                    HIGH_SMALL_COMBINATION_RANK_WEIGHT
        }

        return penalty * typeFactor
    }

    fun computeOverkillPenalty(
        candidate: PlayCombination,
        currentCombination: PlayCombination,
    ): Double {
        if (context.rules.isBomb(candidate.type) && !context.rules.isBomb(currentCombination.type)) {
            return BOMB_OVERKILL_PENALTY
        }

        var penalty = ZERO_SCORE
        if (candidate.cardCount == currentCombination.cardCount) {
            val typeGap = candidate.type.typePower - currentCombination.type.typePower
            if (typeGap > ZERO_COUNT) {
                penalty += typeGap * TYPE_GAP_OVERKILL_WEIGHT
            }
        }

        if (candidate.type == currentCombination.type) {
            val rankGap = candidate.primaryRank - currentCombination.primaryRank
            val suitGap = candidate.primarySuit - currentCombination.primarySuit
            val rankFactor =
                when (candidate.type) {
                    CombinationType.SINGLE -> SINGLE_OVERKILL_RANK_FACTOR
                    CombinationType.PAIR -> PAIR_OVERKILL_RANK_FACTOR
                    CombinationType.TRIPLE -> TRIPLE_OVERKILL_RANK_FACTOR
                    CombinationType.STRAIGHT,
                    CombinationType.FLUSH,
                    CombinationType.FULL_HOUSE,
                    CombinationType.STRAIGHT_FLUSH,
                    -> FIVE_CARD_OVERKILL_RANK_FACTOR
                    CombinationType.FOUR_OF_A_KIND_BOMB,
                    CombinationType.FOUR_WITH_ONE,
                    CombinationType.FOUR_WITH_TWO,
                    -> BOMB_OVERKILL_RANK_FACTOR
                }

            penalty += rankGap * rankFactor
            if (rankGap == ZERO_COUNT && suitGap > ZERO_COUNT) {
                penalty += suitGap * SUIT_GAP_OVERKILL_WEIGHT
            }
        }

        return penalty
    }

    fun computeEndgameBonus(
        remainingCardCount: Int,
        candidate: PlayCombination,
    ): Double {
        var bonus = ZERO_SCORE

        bonus +=
            when {
                remainingCardCount <= ZERO_COUNT -> FINISHING_PLAY_BONUS
                remainingCardCount <= DANGER_OPPONENT_CARD_COUNT -> VERY_LOW_REMAINING_BONUS
                remainingCardCount <= LATE_GAME_HAND_THRESHOLD -> LOW_REMAINING_BONUS
                remainingCardCount <= MID_GAME_HAND_THRESHOLD -> MID_REMAINING_BONUS
                else -> ZERO_SCORE
            }

        val nextOpponentCount = getNextActiveOpponentCardCount(context.match, context.seatIndex)
        if (nextOpponentCount != null) {
            if (nextOpponentCount <= DANGER_OPPONENT_CARD_COUNT) {
                bonus += DANGER_OPPONENT_BONUS + candidate.cardCount * DANGER_OPPONENT_CARD_WEIGHT
            } else if (nextOpponentCount <= PRESSURE_OPPONENT_CARD_COUNT) {
                bonus += PRESSURE_OPPONENT_BONUS
            }
        }

        val minOpponentCount =
            context.match.seats
                .filter { it.seatId != context.seatIndex && it.status != SeatStatus.FINISHED }
                .minOfOrNull { it.hand.size }

        if (minOpponentCount != null && minOpponentCount <= DANGER_OPPONENT_CARD_COUNT) {
            bonus += GLOBAL_DANGER_OPPONENT_BONUS
        }
        if (
            remainingCardCount <= FAST_FINISH_HAND_THRESHOLD &&
            candidate.cardCount >= FAST_FINISH_COMBINATION_THRESHOLD
        ) {
            bonus += FAST_FINISH_COMBINATION_BONUS
        }

        return bonus
    }

    private fun computeSingleBreakPenalty(candidate: PlayCombination): Double {
        val card = candidate.cards.single()
        val rankCount = context.handProfile.rankCounts[card.rank] ?: ZERO_COUNT
        var penalty = ZERO_SCORE

        if (rankCount >= PAIR_COUNT) penalty += SINGLE_BREAK_PAIR_PENALTY
        if (rankCount >= TRIPLE_COUNT) penalty += SINGLE_BREAK_TRIPLE_PENALTY
        if ((context.handProfile.fiveCardMembershipByCardId[card.id] ?: ZERO_COUNT) > ZERO_COUNT) {
            penalty += SINGLE_BREAK_FIVE_CARD_PENALTY
        }

        return penalty
    }

    private fun computePairBreakPenalty(candidate: PlayCombination): Double {
        val rank = candidate.cards.first().rank
        val rankCount = context.handProfile.rankCounts[rank] ?: ZERO_COUNT
        var penalty = ZERO_SCORE

        if (rankCount >= TRIPLE_COUNT) penalty += PAIR_BREAK_TRIPLE_PENALTY
        if (rankCount >= FOUR_OF_A_KIND_COUNT) penalty += PAIR_BREAK_FOUR_OF_A_KIND_PENALTY

        val fiveCardLinks =
            candidate.cards.sumOf { card ->
                if ((context.handProfile.fiveCardMembershipByCardId[card.id] ?: ZERO_COUNT) > ZERO_COUNT) {
                    FIVE_CARD_LINK_UNIT
                } else {
                    ZERO_SCORE
                }
            }

        return penalty + fiveCardLinks * PAIR_BREAK_FIVE_CARD_LINK_WEIGHT
    }

    private fun computeTripleBreakPenalty(candidate: PlayCombination): Double {
        val rank = candidate.cards.first().rank
        val rankCount = context.handProfile.rankCounts[rank] ?: ZERO_COUNT
        var penalty = ZERO_SCORE

        if (rankCount >= FOUR_OF_A_KIND_COUNT) penalty += TRIPLE_BREAK_FOUR_OF_A_KIND_PENALTY
        if (context.handProfile.rankCounts.any { (otherRank, count) -> otherRank != rank && count >= PAIR_COUNT }) {
            penalty += TRIPLE_BREAK_OTHER_PAIR_PENALTY
        }

        return penalty
    }
}
