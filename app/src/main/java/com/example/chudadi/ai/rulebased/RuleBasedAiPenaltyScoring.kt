package com.example.chudadi.ai.rulebased

import com.example.chudadi.model.game.entity.CardRank
import com.example.chudadi.model.game.entity.Match
import com.example.chudadi.model.game.entity.PlayCombination
import com.example.chudadi.model.game.entity.SeatStatus
import com.example.chudadi.model.game.rule.CombinationType
import com.example.chudadi.model.game.rule.GameRules

internal fun computeBreakPenalty(
    candidate: PlayCombination,
    handProfile: HandProfile,
): Double =
    when (candidate.type) {
        CombinationType.SINGLE -> computeSingleBreakPenalty(candidate, handProfile)
        CombinationType.PAIR -> computePairBreakPenalty(candidate, handProfile)
        CombinationType.TRIPLE -> computeTripleBreakPenalty(candidate, handProfile)
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

internal fun computeSingleBreakPenalty(
    candidate: PlayCombination,
    handProfile: HandProfile,
): Double {
    val card = candidate.cards.single()
    val rankCount = handProfile.rankCounts[card.rank] ?: ZERO_COUNT
    var penalty = ZERO_SCORE

    if (rankCount >= PAIR_COUNT) penalty += SINGLE_BREAK_PAIR_PENALTY
    if (rankCount >= TRIPLE_COUNT) penalty += SINGLE_BREAK_TRIPLE_PENALTY
    if ((handProfile.fiveCardMembershipByCardId[card.id] ?: ZERO_COUNT) > ZERO_COUNT) {
        penalty += SINGLE_BREAK_FIVE_CARD_PENALTY
    }

    return penalty
}

internal fun computePairBreakPenalty(
    candidate: PlayCombination,
    handProfile: HandProfile,
): Double {
    val rank = candidate.cards.first().rank
    val rankCount = handProfile.rankCounts[rank] ?: ZERO_COUNT
    var penalty = ZERO_SCORE

    if (rankCount >= TRIPLE_COUNT) penalty += PAIR_BREAK_TRIPLE_PENALTY
    if (rankCount >= FOUR_OF_A_KIND_COUNT) penalty += PAIR_BREAK_FOUR_OF_A_KIND_PENALTY

    val fiveCardLinks =
        candidate.cards.sumOf { card ->
            if ((handProfile.fiveCardMembershipByCardId[card.id] ?: ZERO_COUNT) > ZERO_COUNT) {
                FIVE_CARD_LINK_UNIT
            } else {
                ZERO_SCORE
            }
        }

    return penalty + fiveCardLinks * PAIR_BREAK_FIVE_CARD_LINK_WEIGHT
}

internal fun computeTripleBreakPenalty(
    candidate: PlayCombination,
    handProfile: HandProfile,
): Double {
    val rank = candidate.cards.first().rank
    val rankCount = handProfile.rankCounts[rank] ?: ZERO_COUNT
    var penalty = ZERO_SCORE

    if (rankCount >= FOUR_OF_A_KIND_COUNT) penalty += TRIPLE_BREAK_FOUR_OF_A_KIND_PENALTY
    if (handProfile.rankCounts.any { (otherRank, count) -> otherRank != rank && count >= PAIR_COUNT }) {
        penalty += TRIPLE_BREAK_OTHER_PAIR_PENALTY
    }

    return penalty
}

internal fun computeControlLossPenalty(candidate: PlayCombination): Double {
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

    if (candidate.cardCount <= HIGH_SMALL_COMBINATION_CARD_COUNT &&
        candidate.primaryRank >= CardRank.KING.strength
    ) {
        penalty += (candidate.primaryRank - CardRank.KING.strength + ONE_RANK_STEP) * HIGH_SMALL_COMBINATION_RANK_WEIGHT
    }

    return penalty * typeFactor
}

internal fun computeOverkillPenalty(
    candidate: PlayCombination,
    currentCombination: PlayCombination,
    rules: GameRules,
): Double {
    if (rules.isBomb(candidate.type) && !rules.isBomb(currentCombination.type)) {
        return BOMB_OVERKILL_PENALTY
    }

    var penalty = ZERO_SCORE
    if (candidate.cardCount == currentCombination.cardCount) {
        val typeGap = candidate.type.typePower - currentCombination.type.typePower
        if (typeGap > ZERO_COUNT) penalty += typeGap * TYPE_GAP_OVERKILL_WEIGHT
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
        if (rankGap == ZERO_COUNT && suitGap > ZERO_COUNT) penalty += suitGap * SUIT_GAP_OVERKILL_WEIGHT
    }

    return penalty
}

internal fun computeEndgameBonus(
    match: Match,
    seatIndex: Int,
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

    val nextOpponentCount = getNextActiveOpponentCardCount(match, seatIndex)
    if (nextOpponentCount != null) {
        if (nextOpponentCount <= DANGER_OPPONENT_CARD_COUNT) {
            bonus += DANGER_OPPONENT_BONUS + candidate.cardCount * DANGER_OPPONENT_CARD_WEIGHT
        } else if (nextOpponentCount <= PRESSURE_OPPONENT_CARD_COUNT) {
            bonus += PRESSURE_OPPONENT_BONUS
        }
    }

    val minOpponentCount =
        match.seats
            .filter { it.seatId != seatIndex && it.status != SeatStatus.FINISHED }
            .minOfOrNull { it.hand.size }

    if (minOpponentCount != null && minOpponentCount <= DANGER_OPPONENT_CARD_COUNT) {
        bonus += GLOBAL_DANGER_OPPONENT_BONUS
    }
    if (remainingCardCount <= FAST_FINISH_HAND_THRESHOLD &&
        candidate.cardCount >= FAST_FINISH_COMBINATION_THRESHOLD
    ) {
        bonus += FAST_FINISH_COMBINATION_BONUS
    }

    return bonus
}
