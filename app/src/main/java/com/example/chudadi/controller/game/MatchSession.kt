package com.example.chudadi.controller.game

import com.example.chudadi.model.game.entity.Match
import com.example.chudadi.model.game.snapshot.MatchUiState

data class MatchSession(
    val match: Match,
    val localSeatId: Int,
    val uiState: MatchUiState,
)
