package com.example.chudadi.ai.rulebased

import com.example.chudadi.model.game.entity.Card
import com.example.chudadi.model.game.entity.CardRank
import com.example.chudadi.model.game.entity.Match
import com.example.chudadi.model.game.entity.PlayCombination
import com.example.chudadi.model.game.entity.SeatStatus

internal data class HandProfile(
    val rankCounts: Map<CardRank, Int>,
    val fiveCardMembershipByCardId: Map<String, Int>,
)

internal fun buildHandProfile(
    hand: List<Card>,
    combinations: List<PlayCombination>,
): HandProfile {
    val rankCounts = hand.groupingBy { it.rank }.eachCount()

    val fiveCardMembershipByCardId =
        combinations
            .filter { it.cardCount >= FIVE_CARD_STRUCTURE_THRESHOLD }
            .flatMap { combination -> combination.cards.map(Card::id) }
            .groupingBy { it }
            .eachCount()

    return HandProfile(
        rankCounts = rankCounts,
        fiveCardMembershipByCardId = fiveCardMembershipByCardId,
    )
}

internal fun getNextActiveOpponentCardCount(
    match: Match,
    seatIndex: Int,
): Int? {
    val activeSeats =
        match.seats
            .filter { it.status != SeatStatus.FINISHED }
            .sortedBy { it.seatId }

    val currentIndex = activeSeats.indexOfFirst { it.seatId == seatIndex }
    if (currentIndex == INVALID_INDEX || activeSeats.size <= MIN_ACTIVE_SEAT_COUNT) {
        return null
    }

    for (offset in FIRST_NEXT_OFFSET until activeSeats.size) {
        val seat = activeSeats[(currentIndex + offset) % activeSeats.size]
        if (seat.seatId != seatIndex) {
            return seat.hand.size
        }
    }

    return null
}

private const val FIVE_CARD_STRUCTURE_THRESHOLD = 5
private const val INVALID_INDEX = -1
private const val MIN_ACTIVE_SEAT_COUNT = 1
private const val FIRST_NEXT_OFFSET = 1
