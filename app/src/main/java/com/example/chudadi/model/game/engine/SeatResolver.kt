package com.example.chudadi.model.game.engine

import com.example.chudadi.model.game.entity.Seat
import com.example.chudadi.model.game.entity.SeatStatus

/**
 * Finds the next active seat (status != FINISHED) starting from the seat after [fromSeatId].
 * Returns null if no active seat is found.
 */
fun nextActiveSeatIndex(seats: List<Seat>, fromSeatId: Int): Int? {
    if (seats.isEmpty()) return null
    val maxIndex = seats.maxOf { it.seatId }
    var cursor = fromSeatId
    repeat(maxIndex + 1) {
        cursor = (cursor + 1) % (maxIndex + 1)
        val nextSeat = seats.firstOrNull { it.seatId == cursor }
            ?: return@repeat
        if (nextSeat.status == SeatStatus.ACTIVE) {
            return cursor
        }
    }
    return null
}

