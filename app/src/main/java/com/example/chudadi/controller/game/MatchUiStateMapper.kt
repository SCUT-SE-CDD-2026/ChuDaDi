package com.example.chudadi.controller.game

import com.example.chudadi.BuildConfig
import com.example.chudadi.R
import com.example.chudadi.model.game.engine.GameEngine
import com.example.chudadi.model.game.entity.Card
import com.example.chudadi.model.game.entity.Match
import com.example.chudadi.model.game.entity.MatchPhase
import com.example.chudadi.model.game.entity.SeatStatus
import com.example.chudadi.model.game.snapshot.DebugHandSummary
import com.example.chudadi.model.game.snapshot.MatchUiState
import com.example.chudadi.model.game.snapshot.OpponentSummary
import com.example.chudadi.model.game.snapshot.ResultSummary
import com.example.chudadi.model.game.snapshot.TablePlaySummary

class MatchUiStateMapper(
    private val engine: GameEngine,
    private val enableDebugHands: Boolean = BuildConfig.DEBUG,
) {
    fun map(
        match: Match?,
        selectedCardIds: Set<String>,
        lastActionMessage: String?,
        localSeatId: Int = DEFAULT_LOCAL_SEAT_ID,
    ): MatchUiState {
        if (match == null) {
            return MatchUiState()
        }

        val localSeat = match.seats.firstOrNull { it.seatId == localSeatId }
        val selectedCards = localSeat?.hand?.filter { it.id in selectedCardIds }?.map(Card::id)?.toSet().orEmpty()
        val tablePlays = match.trickState.tablePlays.entries
            .sortedBy { it.key }
            .map { (seatId, combination) ->
                val owner = match.seats.first { it.seatId == seatId }
                TablePlaySummary(
                    ownerSeatId = owner.seatId,
                    ownerName = owner.displayName,
                    combinationLabel = combination.type.displayName,
                    cardLabels = combination.cards.sortedWith(Card.gameComparator).map(Card::displayName),
                )
            }

        return MatchUiState(
            phase = match.phase,
            playerHand = localSeat?.hand?.sortedWith(Card.gameComparator).orEmpty(),
            selectedCards = selectedCards,
            opponentSummaries = buildOpponentSummaries(match, localSeatId, localSeat != null),
            currentActorName = match.seats.first { it.seatId == match.activeSeatIndex }.displayName,
            tablePlays = tablePlays,
            currentTablePlay = match.trickState.currentCombination?.let { combination ->
                val owner = match.seats.first { it.seatId == match.trickState.lastWinningSeatIndex }
                TablePlaySummary(
                    ownerSeatId = owner.seatId,
                    ownerName = owner.displayName,
                    combinationLabel = combination.type.displayName,
                    cardLabels = combination.cards.sortedWith(Card.gameComparator).map(Card::displayName),
                )
            },
            lastActionMessage = lastActionMessage,
            canSubmitPlay = engine.canSubmitSelectedCards(
                match = match,
                seatIndex = localSeat?.seatId ?: NO_LOCAL_SEAT_ID,
                selectedCardIds = selectedCardIds,
            ),
            canPass = engine.canPass(
                match = match,
                seatIndex = localSeat?.seatId ?: NO_LOCAL_SEAT_ID,
            ),
            resultSummary = match.result?.let { result ->
                ResultSummary(
                    winnerName = match.seats.first { it.seatId == result.winnerSeatIndex }.displayName,
                    rankingLines = result.scoreSummary.summaryLines,
                    roundScores = result.scoreSummary.roundScores,
                )
            },
            isHumanTurn = localSeat != null &&
                match.phase != MatchPhase.FINISHED &&
                match.activeSeatIndex == localSeatId,
            debugOpponentHands = buildDebugHands(match, localSeatId, localSeat != null),
        )
    }

    private fun buildOpponentSummaries(
        match: Match,
        localSeatId: Int,
        hasLocalSeat: Boolean,
    ): List<OpponentSummary> {
        return match.seats
            .filterNot { it.seatId == localSeatId && hasLocalSeat }
            .map { seat ->
                OpponentSummary(
                    seatId = seat.seatId,
                    displayName = seat.displayName,
                    avatarResId = R.drawable.avatar,
                    remainingCards = seat.hand.size,
                    isCurrentActor = seat.seatId == match.activeSeatIndex,
                    hasPassed = seat.status == SeatStatus.PASSED,
                )
            }
    }

    private fun buildDebugHands(
        match: Match,
        localSeatId: Int,
        hasLocalSeat: Boolean,
    ): List<DebugHandSummary> {
        if (!enableDebugHands) {
            return emptyList()
        }
        return match.seats
            .filterNot { it.seatId == localSeatId && hasLocalSeat }
            .sortedBy { it.seatId }
            .map { seat ->
                DebugHandSummary(
                    seatId = seat.seatId,
                    displayName = seat.displayName,
                    cards = seat.hand.sortedWith(Card.gameComparator).map(Card::displayName),
                )
            }
    }

    companion object {
        const val DEFAULT_LOCAL_SEAT_ID = 0
        const val NO_LOCAL_SEAT_ID = -1
    }
}
