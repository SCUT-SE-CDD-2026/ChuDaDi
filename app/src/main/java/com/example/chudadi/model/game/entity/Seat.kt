package com.example.chudadi.model.game.entity

enum class SeatControllerType {
    HUMAN,
    RULE_BASED_AI,
}

enum class SeatStatus {
    ACTIVE,
    PASSED,
    FINISHED,
}

data class Seat(
    val seatId: Int,
    val displayName: String,
    val controllerType: SeatControllerType,
    val hand: List<Card>,
    val status: SeatStatus = SeatStatus.ACTIVE,
    val finishOrder: Int? = null,
)
