package com.example.chudadi.model.game.snapshot

import androidx.annotation.DrawableRes
import com.example.chudadi.model.game.entity.Card
import com.example.chudadi.model.game.entity.MatchPhase
import com.example.chudadi.model.game.entity.RoundScore

enum class ViewSeat {
    SELF,
    LEFT,
    TOP,
    RIGHT,
}

data class OpponentSummary(
    val viewSeat: ViewSeat,
    val authoritySeatId: Int = -1,
    val displayName: String,
    @param:DrawableRes val avatarResId: Int,
    val remainingCards: Int,
    val isCurrentActor: Boolean,
    val hasPassed: Boolean,
    val isDisconnected: Boolean = false,
)

data class TablePlaySummary(
    val ownerViewSeat: ViewSeat,
    val ownerName: String,
    val combinationLabel: String,
    val cardLabels: List<String>,
    val stackOrder: Int = 0,
)

data class ResultSummary(
    val winnerName: String,
    val rankingLines: List<String>,
    val roundScores: List<RoundScore> = emptyList(),
)

data class MatchUiState(
    val phase: MatchPhase = MatchPhase.NOT_STARTED,
    val matchId: String? = null,
    val playerHand: List<Card> = emptyList(),
    val selectedCards: Set<String> = emptySet(),
    val opponentSummaries: List<OpponentSummary> = emptyList(),
    val currentActorName: String = "",
    val tablePlays: List<TablePlaySummary> = emptyList(),
    val currentTablePlay: TablePlaySummary? = null,
    val lastActionMessage: String? = null,
    val canSubmitPlay: Boolean = false,
    val canPass: Boolean = false,
    val remainingTurnSeconds: Int? = null,
    val resultSummary: ResultSummary? = null,
    val isHumanTurn: Boolean = false,
    val isLocalDisconnected: Boolean = false,
)
