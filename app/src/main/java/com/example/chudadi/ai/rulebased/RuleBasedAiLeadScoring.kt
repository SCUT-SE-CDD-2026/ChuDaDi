package com.example.chudadi.ai.rulebased

import com.example.chudadi.model.game.entity.Card
import com.example.chudadi.model.game.entity.CardRank
import com.example.chudadi.model.game.entity.PlayCombination
import com.example.chudadi.model.game.rule.CombinationType
import com.example.chudadi.model.game.rule.GameRules

internal fun computeLeadBaseScore(candidate: PlayCombination): Double =
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

internal fun computeLeadRankPenalty(candidate: PlayCombination): Double {
    val rankWeight =
        if (candidate.type == CombinationType.SINGLE) {
            SINGLE_LEAD_RANK_WEIGHT
        } else {
            NON_SINGLE_LEAD_RANK_WEIGHT
        }
    return candidate.primaryRank * rankWeight
}

internal fun computeAllInBonus(candidate: PlayCombination, hand: List<Card>): Double =
    if (candidate.cards.size == hand.size) PLAY_ALL_HAND_BONUS else ZERO_SCORE

internal fun computeLeadStructureBonus(candidate: PlayCombination, handProfile: HandProfile): Double =
    when (candidate.type) {
        CombinationType.SINGLE -> computeLowSingletonLeadBonus(candidate, handProfile)
        CombinationType.PAIR -> computeNaturalPairLeadBonus(candidate, handProfile)
        else -> ZERO_SCORE
    }

internal fun computeLowSingletonLeadBonus(candidate: PlayCombination, handProfile: HandProfile): Double {
    val card = candidate.cards.singleOrNull() ?: return ZERO_SCORE
    val rankCount = handProfile.rankCounts[card.rank] ?: ZERO_COUNT
    return if (rankCount == SINGLETON_COUNT &&
        card.rank.strength <= CardRank.NINE.strength
    ) {
        LOW_SINGLETON_LEAD_BONUS
    } else {
        ZERO_SCORE
    }
}

internal fun computeNaturalPairLeadBonus(candidate: PlayCombination, handProfile: HandProfile): Double {
    val rank = candidate.cards.first().rank
    val rankCount = handProfile.rankCounts[rank] ?: ZERO_COUNT
    return if (rankCount == PAIR_COUNT &&
        rank.strength <= CardRank.TEN.strength
    ) {
        NATURAL_PAIR_LEAD_BONUS
    } else {
        ZERO_SCORE
    }
}

internal fun computeOpeningAdjustment(candidate: PlayCombination, requiresOpeningThree: Boolean): Double {
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

internal fun computeEarlyBombPenalty(
    candidate: PlayCombination,
    rules: GameRules,
    hand: List<Card>,
): Double =
    if (rules.isBomb(candidate.type) && hand.size > candidate.cardCount + EXTRA_SAFE_CARDS_AFTER_BOMB) {
        EARLY_BOMB_PENALTY
    } else {
        ZERO_SCORE
    }
