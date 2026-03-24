package com.example.chudadi.model.game.entity

enum class CardSuit(
    val symbol: String,
    val sortOrder: Int,
) {
    DIAMONDS(symbol = "\u2666", sortOrder = 0),
    CLUBS(symbol = "\u2663", sortOrder = 1),
    HEARTS(symbol = "\u2665", sortOrder = 2),
    SPADES(symbol = "\u2660", sortOrder = 3),
}

enum class CardRank(
    val displayName: String,
    val strength: Int,
) {
    THREE(displayName = "3", strength = 0),
    FOUR(displayName = "4", strength = 1),
    FIVE(displayName = "5", strength = 2),
    SIX(displayName = "6", strength = 3),
    SEVEN(displayName = "7", strength = 4),
    EIGHT(displayName = "8", strength = 5),
    NINE(displayName = "9", strength = 6),
    TEN(displayName = "10", strength = 7),
    JACK(displayName = "J", strength = 8),
    QUEEN(displayName = "Q", strength = 9),
    KING(displayName = "K", strength = 10),
    ACE(displayName = "A", strength = 11),
    TWO(displayName = "2", strength = 12),
}

data class Card(
    val suit: CardSuit,
    val rank: CardRank,
) {
    val id: String = "${rank.name}_${suit.name}"
    val displayName: String = "${rank.displayName}${suit.symbol}"

    companion object {
        fun standardDeck(): List<Card> {
            return CardSuit.entries.flatMap { suit ->
                CardRank.entries.map { rank ->
                    Card(suit = suit, rank = rank)
                }
            }
        }

        val gameComparator: Comparator<Card> =
            compareBy<Card> { it.rank.strength }
                .thenBy { it.suit.sortOrder }
    }
}
