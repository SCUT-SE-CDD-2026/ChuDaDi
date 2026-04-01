package com.example.chudadi.controller.game

import com.example.chudadi.R
import com.example.chudadi.model.game.engine.GameEngine
import com.example.chudadi.model.game.entity.Card
import com.example.chudadi.model.game.entity.Match
import com.example.chudadi.model.game.entity.MatchPhase
import com.example.chudadi.model.game.entity.SeatStatus
import com.example.chudadi.model.game.snapshot.MatchUiState
import com.example.chudadi.model.game.snapshot.OpponentSummary
import com.example.chudadi.model.game.snapshot.ResultSummary
import com.example.chudadi.model.game.snapshot.TablePlaySummary

class MatchUiStateMapper(
    private val engine: GameEngine,
) {
    fun map(
        match: Match?,
        selectedCardIds: Set<String>,
        lastActionMessage: String?,
    ): MatchUiState {
        if (match == null) {
            return MatchUiState()
        }

        val humanSeat = match.seats.first { it.seatId == HUMAN_SEAT_ID }
        val selectedCards = humanSeat.hand.filter { it.id in selectedCardIds }.map(Card::id).toSet()
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
            playerHand = humanSeat.hand.sortedWith(Card.gameComparator),
            selectedCards = selectedCards,
            opponentSummaries = match.seats
                .filterNot { it.seatId == HUMAN_SEAT_ID }
                .map { seat ->
                    OpponentSummary(
                        seatId = seat.seatId,
                        displayName = seat.displayName,
                        avatarResId = R.drawable.avatar,
                        remainingCards = seat.hand.size,
                        isCurrentActor = seat.seatId == match.activeSeatIndex,
                        hasPassed = seat.status == SeatStatus.PASSED,
                    )
                },
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
                seatIndex = HUMAN_SEAT_ID,
                selectedCardIds = selectedCardIds,
            ),
            canPass = engine.canPass(
                match = match,
                seatIndex = HUMAN_SEAT_ID,
            ),
            resultSummary = match.result?.let { result ->
                ResultSummary(
                    winnerName = match.seats.first { it.seatId == result.winnerSeatIndex }.displayName,
                    rankingLines = result.summaryLines,
                )
            },
            isHumanTurn = match.phase != MatchPhase.FINISHED && match.activeSeatIndex == HUMAN_SEAT_ID,
        )
    }

    companion object {
        const val HUMAN_SEAT_ID = 0
    }
}
