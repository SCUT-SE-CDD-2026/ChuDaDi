package com.example.chudadi.model.game.rule

import com.example.chudadi.model.game.entity.PlayCombination

class CombinationComparator(
    private val rules: GameRules,
) {
    fun canBeat(
        candidate: PlayCombination,
        current: PlayCombination?,
    ): Boolean {
        if (current == null) {
            return true
        }

        return when (rules.ruleSet) {
            GameRuleSet.SOUTHERN -> canBeatSouthern(candidate, current)
            GameRuleSet.NORTHERN -> canBeatNorthern(candidate, current)
        }
    }

    fun compareForSorting(
        left: PlayCombination,
        right: PlayCombination,
    ): Int {
        return when {
            left.cardCount != right.cardCount -> left.cardCount.compareTo(right.cardCount)
            left.type != right.type ->
                left.type.typePower.compareTo(right.type.typePower)
            else -> compareSameType(left, right)
        }
    }

    private fun compareSameType(
        left: PlayCombination,
        right: PlayCombination,
    ): Int {
        val primaryRankComparison = left.primaryRank.compareTo(right.primaryRank)
        if (primaryRankComparison != 0) {
            return primaryRankComparison
        }

        return left.primarySuit.compareTo(right.primarySuit)
    }

    private fun canBeatSouthern(
        candidate: PlayCombination,
        current: PlayCombination,
    ): Boolean =
        when {
            candidate.cardCount == FIVE_CARD_COUNT &&
                candidate.type == CombinationType.STRAIGHT_FLUSH -> true
            candidate.cardCount == FIVE_CARD_COUNT &&
                candidate.type == CombinationType.FOUR_WITH_ONE &&
                current.type != CombinationType.STRAIGHT_FLUSH -> true
            candidate.cardCount != current.cardCount -> false
            candidate.type != current.type -> false
            else -> compareSameType(candidate, current) > 0
        }

    private fun canBeatNorthern(
        candidate: PlayCombination,
        current: PlayCombination,
    ): Boolean =
        when {
            candidate.cardCount == FIVE_CARD_COUNT && current.cardCount == FIVE_CARD_COUNT ->
                when {
                    candidate.type == current.type -> compareSameType(candidate, current) > 0
                    candidate.type.typePower != current.type.typePower ->
                        candidate.type.typePower > current.type.typePower
                    else -> false
                }
            candidate.cardCount != current.cardCount -> false
            candidate.type != current.type -> false
            else -> compareSameType(candidate, current) > 0
        }

    private companion object {
        const val FIVE_CARD_COUNT = 5
    }
}
