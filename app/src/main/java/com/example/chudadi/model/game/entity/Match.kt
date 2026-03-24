package com.example.chudadi.model.game.entity

data class Match(
    val matchId: String,
    val phase: MatchPhase,
    val seats: List<Seat>,
    val activeSeatIndex: Int,
    val trickState: TrickState,
    val playHistory: List<String>,
    val result: RoundResult?,
)
