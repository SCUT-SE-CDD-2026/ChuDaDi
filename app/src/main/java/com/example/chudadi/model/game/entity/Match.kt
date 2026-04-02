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
    val totalBombCount: Int,
    val result: RoundResult?,
)
