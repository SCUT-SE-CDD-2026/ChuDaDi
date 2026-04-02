@file:Suppress("ReturnCount")

package com.example.chudadi.model.game.rule

import com.example.chudadi.model.game.entity.Card
import com.example.chudadi.model.game.entity.CardRank
import com.example.chudadi.model.game.entity.PlayCombination

class CombinationParser(
    private val rules: GameRules,
) {
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
            FOUR_CARD_COUNT -> buildFourCardCombination(sortedCards)
            5 -> buildFiveCardCombination(sortedCards)
            SIX_CARD_COUNT -> buildSixCardCombination(sortedCards)
            else -> null
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

    private fun buildFourCardCombination(cards: List<Card>): PlayCombination? {
        if (!rules.fourOfAKindBombEnabled) {
            return null
        }
        if (cards.map { it.rank }.distinct().size != 1) {
            return null
        }

        return PlayCombination(
            type = CombinationType.FOUR_OF_A_KIND_BOMB,
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

        if (rankGroups.size == 2 && rankGroups.any { it.value.size == FOUR_OF_A_KIND_GROUP_SIZE }) {
            val fourRank = rankGroups.entries.first { it.value.size == FOUR_OF_A_KIND_GROUP_SIZE }.key
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

    private fun buildSixCardCombination(cards: List<Card>): PlayCombination? {
        if (!rules.fourWithTwoEnabled) {
            return null
        }

        val rankGroups = cards.groupBy { it.rank }
        if (rankGroups.none { it.value.size == FOUR_OF_A_KIND_GROUP_SIZE }) {
            return null
        }

        val fourRank = rankGroups.entries.first { it.value.size == FOUR_OF_A_KIND_GROUP_SIZE }.key
        val fourCards = rankGroups.getValue(fourRank)
        return PlayCombination(
            type = CombinationType.FOUR_WITH_TWO,
            cards = cards,
            primaryRank = fourRank.strength,
            primarySuit = fourCards.maxOf { it.suit.sortOrder },
        )
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

    private companion object {
        const val FOUR_CARD_COUNT = 4
        const val SIX_CARD_COUNT = 6
        const val FOUR_OF_A_KIND_GROUP_SIZE = 4
    }
}
