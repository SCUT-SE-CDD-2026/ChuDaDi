package com.example.chudadi.ai.rulebased

import com.example.chudadi.model.game.entity.Card
import com.example.chudadi.model.game.entity.Match
import com.example.chudadi.model.game.entity.PlayCombination
import com.example.chudadi.model.game.rule.GameRules

internal class RuleBasedAiScoring(
    private val match: Match,
    private val seatIndex: Int,
    private val rules: GameRules,
    private val hand: List<Card>,
    private val handProfile: HandProfile,
) {
    fun scoreLeadCandidate(
        candidate: PlayCombination,
        requiresOpeningThree: Boolean,
    ): Double {
        val remainingCardCount = hand.size - candidate.cardCount

        return computeLeadBaseScore(candidate) -
            computeLeadRankPenalty(candidate) -
            computeBreakPenalty(candidate, handProfile) -
            computeControlLossPenalty(candidate) +
            computeAllInBonus(candidate, hand) +
            computeEndgameBonus(match, seatIndex, remainingCardCount, candidate) +
            computeLeadStructureBonus(candidate, handProfile) +
            computeOpeningAdjustment(candidate, requiresOpeningThree) -
            computeEarlyBombPenalty(candidate, rules, hand)
    }

    fun scoreResponseCandidate(
        candidate: PlayCombination,
        currentCombination: PlayCombination,
    ): Double {
        val remainingCardCount = hand.size - candidate.cardCount

        return RESPONSE_BASE_SCORE +
            computeResponseMatchBonus(candidate, currentCombination, rules) +
            computeAllInBonus(candidate, hand) +
            computeEndgameBonus(match, seatIndex, remainingCardCount, candidate) +
            computeResponseEfficiencyBonus(candidate, currentCombination, rules) -
            computeBreakPenalty(candidate, handProfile) -
            computeResponseControlLossPenalty(candidate) -
            computeOverkillPenalty(candidate, currentCombination, rules) -
            computeResponseBombPenalty(candidate, currentCombination, rules, hand)
    }

    fun computePassProbability(
        bestCandidate: PlayCombination,
        bestScore: Double,
    ): Double {
        val selfSeat = match.seats.first { it.seatId == seatIndex }
        val remainingAfterBest = selfSeat.hand.size - bestCandidate.cardCount
        val probability =
            basePassProbabilityFromScore(bestScore) +
                computePassEndgameAdjustment(remainingAfterBest) +
                computePassOpponentPressureAdjustment(match, seatIndex) +
                computePassBombAdjustment(bestCandidate, rules, match.trickState.currentCombination) +
                computePassControlCardAdjustment(bestCandidate)

        return probability.coerceIn(MIN_PASS_PROBABILITY, MAX_PASS_PROBABILITY)
    }
}
