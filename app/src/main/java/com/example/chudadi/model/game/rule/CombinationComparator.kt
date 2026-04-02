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

        val candidateIsBomb = rules.isBomb(candidate.type)
        val currentIsBomb = rules.isBomb(current.type)
        val result =
            when {
                candidateIsBomb || currentIsBomb ->
                    canBeatWithBombRules(
                        candidate = candidate,
                        current = current,
                        candidateIsBomb = candidateIsBomb,
                        currentIsBomb = currentIsBomb,
                    )

                candidate.cardCount != current.cardCount -> false
                candidate.type != current.type -> false
                else -> compareSameType(candidate, current) > 0
            }

        return result
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

        if (left.type == CombinationType.FLUSH) {
            left.rankVector.zip(right.rankVector).forEach { (leftRank, rightRank) ->
                val comparison = leftRank.compareTo(rightRank)
                if (comparison != 0) {
                    return comparison
                }
            }
        }

        return left.primarySuit.compareTo(right.primarySuit)
    }

    private fun canBeatWithBombRules(
        candidate: PlayCombination,
        current: PlayCombination,
        candidateIsBomb: Boolean,
        currentIsBomb: Boolean,
    ): Boolean {
        if (candidateIsBomb && currentIsBomb) {
            return compareBomb(candidate, current) > 0
        }
        if (!candidateIsBomb) {
            return false
        }
        return true
    }

    private fun compareBomb(
        left: PlayCombination,
        right: PlayCombination,
    ): Int {
        val bombPowerComparison = bombPower(left).compareTo(bombPower(right))
        if (bombPowerComparison != 0) {
            return bombPowerComparison
        }
        return compareSameType(left, right)
    }

    private fun bombPower(combination: PlayCombination): Int {
        return when (combination.type) {
            CombinationType.FOUR_OF_A_KIND_BOMB -> STANDARD_BOMB_POWER
            CombinationType.FOUR_WITH_ONE -> STANDARD_BOMB_POWER
            CombinationType.FOUR_WITH_TWO -> if (rules.fourWithTwoIsBomb) STANDARD_BOMB_POWER else NO_BOMB_POWER
            CombinationType.STRAIGHT_FLUSH -> if (rules.straightFlushIsBomb) HIGH_BOMB_POWER else NO_BOMB_POWER
            else -> NO_BOMB_POWER
        }
    }

    private companion object {
        const val NO_BOMB_POWER = 0
        const val STANDARD_BOMB_POWER = 1
        const val HIGH_BOMB_POWER = 2
    }
}
