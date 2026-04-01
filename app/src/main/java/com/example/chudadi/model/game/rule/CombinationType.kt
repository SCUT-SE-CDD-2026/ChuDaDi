package com.example.chudadi.model.game.rule

enum class CombinationType(
    val displayName: String,
    val cardCount: Int,
    val typePower: Int,
) {
    SINGLE(displayName = "Single", cardCount = 1, typePower = 0),
    PAIR(displayName = "Pair", cardCount = 2, typePower = 0),
    TRIPLE(displayName = "Triple", cardCount = 3, typePower = 0),
    FOUR_OF_A_KIND_BOMB(displayName = "Four of a Kind Bomb", cardCount = 4, typePower = 0),
    STRAIGHT(displayName = "Straight", cardCount = 5, typePower = 1),
    FLUSH(displayName = "Flush", cardCount = 5, typePower = 2),
    FULL_HOUSE(displayName = "Full House", cardCount = 5, typePower = 3),
    FOUR_WITH_ONE(displayName = "Four with One", cardCount = 5, typePower = 4),
    FOUR_WITH_TWO(displayName = "Four with Two", cardCount = 6, typePower = 4),
    STRAIGHT_FLUSH(displayName = "Straight Flush", cardCount = 5, typePower = 5),
}
