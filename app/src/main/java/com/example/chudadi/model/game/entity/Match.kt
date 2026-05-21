package com.example.chudadi.model.game.entity

import com.example.chudadi.model.game.rule.GameRuleSet

data class Match(
    val matchId: String,
    val ruleSet: GameRuleSet,
    val phase: MatchPhase,
    val seats: List<Seat>,
    val activeSeatIndex: Int,
    val trickState: TrickState,
    val playHistory: List<String>,
    val result: RoundResult?,
) {
    init {
        require(seats.isNotEmpty()) { "seats must not be empty" }
        require(seats.any { it.seatId == activeSeatIndex }) {
            "activeSeatIndex must correspond to an existing seat"
        }
        if (result != null) {
            require(phase == MatchPhase.FINISHED) {
                "phase must be FINISHED when result is present"
            }
        }
    }
}
