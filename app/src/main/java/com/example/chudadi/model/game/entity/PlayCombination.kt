package com.example.chudadi.model.game.entity

import com.example.chudadi.model.game.rule.CombinationType

data class PlayCombination(
    val type: CombinationType,
    val cards: List<Card>,
    val primaryRank: Int,
    val primarySuit: Int,
    val rankVector: List<Int> = emptyList(),
) {
    val cardCount: Int = cards.size
    val displayName: String = buildString {
        append(type.displayName)
        append(": ")
        append(cards.sortedWith(Card.gameComparator).joinToString(" ") { it.displayName })
    }
}
