package com.example.chudadi.model.game.snapshot

import androidx.annotation.DrawableRes
import com.example.chudadi.model.game.entity.Card
import com.example.chudadi.model.game.entity.MatchPhase
import com.example.chudadi.model.game.entity.RoundScore

data class OpponentSummary(
    val seatId: Int,
    val displayName: String,
    @param:DrawableRes val avatarResId: Int,
    val remainingCards: Int,
    val isCurrentActor: Boolean,
    val hasPassed: Boolean,
)

data class TablePlaySummary(
    val ownerSeatId: Int,
    val ownerName: String,
    val combinationLabel: String,
    val cardLabels: List<String>,
)

data class ResultSummary(
    val winnerName: String,
    val rankingLines: List<String>,
    val roundScores: List<RoundScore> = emptyList(),
)

data class DebugHandSummary(
    val seatId: Int,
    val displayName: String,
    val cards: List<String>,
)

data class MatchUiState(
    val phase: MatchPhase = MatchPhase.NOT_STARTED,
    val playerHand: List<Card> = emptyList(),
    val selectedCards: Set<String> = emptySet(),
    val opponentSummaries: List<OpponentSummary> = emptyList(),
    val currentActorName: String = "",
    val tablePlays: List<TablePlaySummary> = emptyList(),
    val currentTablePlay: TablePlaySummary? = null,
    val lastActionMessage: String? = null,
    val canSubmitPlay: Boolean = false,
    val canPass: Boolean = false,
    val resultSummary: ResultSummary? = null,
    val isHumanTurn: Boolean = false,
    val debugOpponentHands: List<DebugHandSummary> = emptyList(),
)
