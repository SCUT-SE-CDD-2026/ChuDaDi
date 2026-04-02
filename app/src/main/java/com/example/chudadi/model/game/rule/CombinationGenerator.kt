package com.example.chudadi.model.game.rule

import com.example.chudadi.model.game.entity.Card
import com.example.chudadi.model.game.entity.PlayCombination

class CombinationGenerator(
    private val parser: CombinationParser,
    private val comparator: CombinationComparator,
) {
    fun generateAllValidCombinations(cards: List<Card>): List<PlayCombination> {
        val combinations = mutableListOf<PlayCombination>()
        val sortedCards = cards.sortedWith(Card.gameComparator)

        combinations += sortedCards.mapNotNull { parser.parse(listOf(it)) }
        combinations += sortedCards.combinations(2).mapNotNull(parser::parse)
        combinations += sortedCards.combinations(3).mapNotNull(parser::parse)
        combinations += sortedCards.combinations(FOUR_CARD_COUNT).mapNotNull(parser::parse)
        combinations += sortedCards.combinations(FIVE_CARD_COUNT).mapNotNull(parser::parse)
        combinations += sortedCards.combinations(SIX_CARD_COUNT).mapNotNull(parser::parse)

        return combinations
            .distinctBy { combination ->
                combination.type to combination.cards.map(Card::id).sorted()
            }
            .sortedWith(comparator::compareForSorting)
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

    private companion object {
        const val FOUR_CARD_COUNT = 4
        const val FIVE_CARD_COUNT = 5
        const val SIX_CARD_COUNT = 6
    }
}
