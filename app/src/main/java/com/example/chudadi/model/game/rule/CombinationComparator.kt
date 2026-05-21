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
                candidate.type != current.type ->
                    canBeatWithTypePower(candidate, current)
                else -> compareSameType(candidate, current) > 0
            }

        return result
    }

    /**
     * 当牌型不同时，检查是否可以通过 typePower 压牌。
     * 主要用于5张牌牌型之间的比较：顺子 < 同花 < 葫芦 < 铁支 < 同花顺
     */
    private fun canBeatWithTypePower(
        candidate: PlayCombination,
        current: PlayCombination,
    ): Boolean {
        if (candidate.cardCount != current.cardCount) {
            return false
        }
        if (!rules.crossTypeFiveCardAllowed && candidate.cardCount == FIVE_CARD_COUNT) {
            return false
        }
        return candidate.type.typePower > current.type.typePower
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

    /**
     * 同牌型比较：先比 primaryRank（主点数），若相同再比 primarySuit（主花色）。
     *
     * 对于葫芦（FULL_HOUSE）和铁支（FOUR_WITH_ONE），规则文档未明确同点数时的花色
     * 比较策略。此处统一以组成主牌型的牌中最大花色作为 primarySuit 进行比较，
     * 保证同点数时仍有确定性的全序关系。
     */
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

    private fun canBeatWithBombRules(
        candidate: PlayCombination,
        current: PlayCombination,
        candidateIsBomb: Boolean,
        currentIsBomb: Boolean,
    ): Boolean {
        if (candidateIsBomb && currentIsBomb) {
            return bombPower(candidate) > bombPower(current)
        }
        if (candidateIsBomb) {
            return true
        }
        return false
    }

    private fun bombPower(combination: PlayCombination): Int {
        return when (combination.type) {
            CombinationType.FOUR_WITH_ONE -> STANDARD_BOMB_POWER
            CombinationType.STRAIGHT_FLUSH -> HIGH_BOMB_POWER
            else -> NO_BOMB_POWER
        }
    }

    private companion object {
        const val NO_BOMB_POWER = 0
        const val STANDARD_BOMB_POWER = 1
        const val HIGH_BOMB_POWER = 2
        const val FIVE_CARD_COUNT = 5
    }
}
