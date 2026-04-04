package com.example.chudadi.ai.rulebased

import com.example.chudadi.model.game.entity.Card
import com.example.chudadi.model.game.entity.CardRank
import com.example.chudadi.model.game.entity.Match
import com.example.chudadi.model.game.entity.PlayCombination
import com.example.chudadi.model.game.rule.CombinationType
import com.example.chudadi.model.game.rule.GameRules

internal fun computeResponseMatchBonus(
    candidate: PlayCombination,
    currentCombination: PlayCombination,
    rules: GameRules,
): Double =
    if (!rules.isBomb(candidate.type) &&
        candidate.type == currentCombination.type &&
        candidate.cardCount == currentCombination.cardCount
    ) {
        SAME_TYPE_RESPONSE_BONUS
    } else {
        ZERO_SCORE
    }

internal fun computeResponseEfficiencyBonus(
    candidate: PlayCombination,
    currentCombination: PlayCombination,
    rules: GameRules,
): Double {
    var bonus = ZERO_SCORE

    if (!rules.isBomb(candidate.type) &&
        candidate.type == currentCombination.type &&
        candidate.primaryRank == currentCombination.primaryRank + SMALL_BEAT_RANK_GAP
    ) {
        bonus += SMALL_BEAT_BONUS
    }

    if (candidate.type == CombinationType.SINGLE &&
        currentCombination.type == CombinationType.SINGLE &&
        candidate.primaryRank <= CardRank.JACK.strength
    ) {
        bonus += LOW_SINGLE_RESPONSE_BONUS
    }

    return bonus
}

internal fun computeResponseControlLossPenalty(candidate: PlayCombination): Double =
    computeControlLossPenalty(candidate) * RESPONSE_CONTROL_LOSS_WEIGHT

internal fun computeResponseBombPenalty(
    candidate: PlayCombination,
    currentCombination: PlayCombination,
    rules: GameRules,
    hand: List<Card>,
): Double {
    var penalty = ZERO_SCORE

    if (rules.isBomb(candidate.type) && !rules.isBomb(currentCombination.type)) {
        penalty += BOMB_OVER_NON_BOMB_PENALTY
    }
    if (rules.isBomb(candidate.type) && hand.size > BOMB_RESPONSE_SAFE_HAND_SIZE) {
        penalty += BOMB_RESPONSE_PENALTY
    }

    return penalty
}

internal fun basePassProbabilityFromScore(bestScore: Double): Double =
    when {
        bestScore < PASS_SCORE_THRESHOLD_VERY_LOW -> PASS_PROBABILITY_VERY_LOW_SCORE
        bestScore < PASS_SCORE_THRESHOLD_LOW -> PASS_PROBABILITY_LOW_SCORE
        bestScore < PASS_SCORE_THRESHOLD_MEDIUM -> PASS_PROBABILITY_MEDIUM_SCORE
        bestScore < PASS_SCORE_THRESHOLD_HIGH -> PASS_PROBABILITY_HIGH_SCORE
        else -> PASS_PROBABILITY_TOP_SCORE
    }

internal fun computePassEndgameAdjustment(remainingAfterBest: Int): Double =
    if (remainingAfterBest <= LATE_GAME_HAND_THRESHOLD) -LATE_GAME_PASS_REDUCTION else ZERO_SCORE

internal fun computePassOpponentPressureAdjustment(
    match: Match,
    seatIndex: Int,
): Double {
    val nextOpponentCount = getNextActiveOpponentCardCount(match, seatIndex)

    return when {
        nextOpponentCount == null -> ZERO_SCORE
        nextOpponentCount <= DANGER_OPPONENT_CARD_COUNT -> -DANGER_OPPONENT_PASS_REDUCTION
        nextOpponentCount <= PRESSURE_OPPONENT_CARD_COUNT -> -PRESSURE_OPPONENT_PASS_REDUCTION
        else -> ZERO_SCORE
    }
}

internal fun computePassBombAdjustment(
    bestCandidate: PlayCombination,
    rules: GameRules,
    currentCombination: PlayCombination?,
): Double {
    if (!rules.isBomb(bestCandidate.type)) {
        return ZERO_SCORE
    }

    var adjustment = BOMB_PASS_INCREASE
    if (rules.mustBeatIfPossible) {
        adjustment += NORTHERN_BOMB_PASS_INCREASE
    }
    if (currentCombination != null && rules.isBomb(currentCombination.type)) {
        adjustment += BOMB_VS_BOMB_PASS_INCREASE
    }
    if (bestCandidate.primaryRank >= CardRank.KING.strength) {
        adjustment += HIGH_BOMB_PASS_INCREASE
    }

    return adjustment
}

internal fun computePassControlCardAdjustment(bestCandidate: PlayCombination): Double {
    if (bestCandidate.type == CombinationType.SINGLE) {
        return when (bestCandidate.cards.single().rank) {
            CardRank.TWO -> SINGLE_TWO_PASS_INCREASE
            CardRank.ACE -> SINGLE_ACE_PASS_INCREASE
            CardRank.KING -> SINGLE_KING_PASS_INCREASE
            else -> ZERO_SCORE
        }
    }

    return if (bestCandidate.cards.any { it.rank == CardRank.TWO }) NON_SINGLE_TWO_PASS_INCREASE else ZERO_SCORE
}
