@file:Suppress("ReturnCount")

package com.example.chudadi.model.game.rule

import com.example.chudadi.model.game.entity.Card
import com.example.chudadi.model.game.entity.CardRank
import com.example.chudadi.model.game.entity.PlayCombination

class CombinationEvaluator {
    fun parse(cards: List<Card>): PlayCombination? {
        if (cards.isEmpty()) {
            return null
        }

        val sortedCards = cards.distinctBy(Card::id).sortedWith(Card.gameComparator)
        if (sortedCards.size != cards.size) {
            return null
        }

        return when (sortedCards.size) {
            1 -> buildSingle(sortedCards)
            2 -> buildPair(sortedCards)
            3 -> buildTriple(sortedCards)
            5 -> buildFiveCardCombination(sortedCards)
            else -> null
        }
    }

    fun canBeat(
        candidate: PlayCombination,
        current: PlayCombination?,
    ): Boolean {
        if (current == null) {
            return true
        }

        if (candidate.cardCount != current.cardCount) {
            return false
        }

        if (candidate.cardCount == 5 && candidate.type.typePower != current.type.typePower) {
            return candidate.type.typePower > current.type.typePower
        }

        if (candidate.type != current.type) {
            return false
        }

        return compareSameType(candidate, current) > 0
    }

    fun generateAllValidCombinations(cards: List<Card>): List<PlayCombination> {
        val combinations = mutableListOf<PlayCombination>()
        val sortedCards = cards.sortedWith(Card.gameComparator)

        combinations += sortedCards.mapNotNull { parse(listOf(it)) }
        combinations += sortedCards.combinations(2).mapNotNull(::parse)
        combinations += sortedCards.combinations(3).mapNotNull(::parse)
        combinations += sortedCards.combinations(5).mapNotNull(::parse)

        return combinations
            .distinctBy { combination ->
                combination.type to combination.cards.map(Card::id).sorted()
            }
            .sortedWith { left, right ->
                when {
                    left.cardCount != right.cardCount -> left.cardCount.compareTo(right.cardCount)
                    left.cardCount == 5 && left.type.typePower != right.type.typePower ->
                        left.type.typePower.compareTo(right.type.typePower)
                    else -> compareSameType(left, right)
                }
            }
    }

    private fun buildSingle(cards: List<Card>): PlayCombination {
        val card = cards.single()
        return PlayCombination(
            type = CombinationType.SINGLE,
            cards = cards,
            primaryRank = card.rank.strength,
            primarySuit = card.suit.sortOrder,
        )
    }

    private fun buildPair(cards: List<Card>): PlayCombination? {
        if (cards.map { it.rank }.distinct().size != 1) {
            return null
        }

        return PlayCombination(
            type = CombinationType.PAIR,
            cards = cards,
            primaryRank = cards.first().rank.strength,
            primarySuit = cards.maxOf { it.suit.sortOrder },
        )
    }

    private fun buildTriple(cards: List<Card>): PlayCombination? {
        if (cards.map { it.rank }.distinct().size != 1) {
            return null
        }

        return PlayCombination(
            type = CombinationType.TRIPLE,
            cards = cards,
            primaryRank = cards.first().rank.strength,
            primarySuit = cards.maxOf { it.suit.sortOrder },
        )
    }

    private fun buildFiveCardCombination(cards: List<Card>): PlayCombination? {
        val rankGroups = cards.groupBy { it.rank }
        val isFlush = cards.map { it.suit }.distinct().size == 1
        val straightHighCard = getStraightHighCard(cards)

        if (isFlush && straightHighCard != null) {
            return PlayCombination(
                type = CombinationType.STRAIGHT_FLUSH,
                cards = cards,
                primaryRank = straightHighCard.rank.strength,
                primarySuit = straightHighCard.suit.sortOrder,
            )
        }

        if (rankGroups.size == 2 && rankGroups.any { it.value.size == 4 }) {
            val fourRank = rankGroups.entries.first { it.value.size == 4 }.key
            val fourCards = rankGroups.getValue(fourRank)
            return PlayCombination(
                type = CombinationType.FOUR_WITH_ONE,
                cards = cards,
                primaryRank = fourRank.strength,
                primarySuit = fourCards.maxOf { it.suit.sortOrder },
            )
        }

        if (rankGroups.size == 2 && rankGroups.any { it.value.size == 3 }) {
            val tripleRank = rankGroups.entries.first { it.value.size == 3 }.key
            val tripleCards = rankGroups.getValue(tripleRank)
            return PlayCombination(
                type = CombinationType.FULL_HOUSE,
                cards = cards,
                primaryRank = tripleRank.strength,
                primarySuit = tripleCards.maxOf { it.suit.sortOrder },
            )
        }

        if (isFlush) {
            return PlayCombination(
                type = CombinationType.FLUSH,
                cards = cards,
                primaryRank = cards.maxOf { it.rank.strength },
                primarySuit = cards.maxOf { it.suit.sortOrder },
                rankVector = cards.sortedByDescending { it.rank.strength }.map { it.rank.strength },
            )
        }

        if (straightHighCard != null) {
            return PlayCombination(
                type = CombinationType.STRAIGHT,
                cards = cards,
                primaryRank = straightHighCard.rank.strength,
                primarySuit = straightHighCard.suit.sortOrder,
            )
        }

        return null
    }

    private fun getStraightHighCard(cards: List<Card>): Card? {
        if (cards.map { it.rank }.distinct().size != cards.size) {
            return null
        }
        if (cards.any { it.rank == CardRank.TWO }) {
            return null
        }

        val sortedCards = cards.sortedBy { it.rank.strength }
        val isStraight = sortedCards
            .zipWithNext()
            .all { (left, right) -> right.rank.strength - left.rank.strength == 1 }

        return if (isStraight) sortedCards.last() else null
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
}

private fun <T> List<T>.combinations(size: Int): List<List<T>> {
    if (size == 0) {
        return listOf(emptyList())
    }
    if (size > this.size) {
        return emptyList()
    }

    val combinations = mutableListOf<List<T>>()

    fun backtrack(
        startIndex: Int,
        current: MutableList<T>,
    ) {
        if (current.size == size) {
            combinations += current.toList()
            return
        }

        for (index in startIndex until this.size) {
            current += this[index]
            backtrack(index + 1, current)
            current.removeAt(current.lastIndex)
        }
    }

    backtrack(startIndex = 0, current = mutableListOf())
    return combinations
}
