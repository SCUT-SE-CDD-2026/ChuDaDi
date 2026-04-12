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
import com.example.chudadi.model.game.snapshot.ViewSeat

class MatchUiStateMapper(
    private val engine: GameEngine,
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

        val humanSeat = match.seats.first { it.seatId == localSeatId }
        val selectedCards = humanSeat.hand.filter { it.id in selectedCardIds }.map(Card::id).toSet()
        val tablePlays = match.trickState.tablePlays.entries
            .mapIndexed { index, (seatId, combination) ->
                val owner = match.seats.first { it.seatId == seatId }
                TablePlaySummary(
                    ownerViewSeat = owner.seatId.toViewSeat(localSeatId),
                    ownerName = owner.displayName,
                    combinationLabel = combination.type.displayName,
                    cardLabels = combination.cards.sortedWith(Card.gameComparator).map(Card::displayName),
                    stackOrder = index,
                )
            }

        return MatchUiState(
            matchId = match.matchId,
            phase = match.phase,
            playerHand = humanSeat.hand.sortedWith(Card.gameComparator),
            selectedCards = selectedCards,
            opponentSummaries = match.seats
                .filterNot { it.seatId == localSeatId }
                .map { seat ->
                    OpponentSummary(
                        viewSeat = seat.seatId.toViewSeat(localSeatId),
                        authoritySeatId = seat.seatId,
                        displayName = seat.displayName,
                        avatarResId = R.drawable.avatar,
                        remainingCards = seat.hand.size,
                        isCurrentActor = seat.seatId == match.activeSeatIndex,
                        hasPassed = seat.status == SeatStatus.PASSED,
                        isDisconnected = false,
                    )
                },
            currentActorName = match.seats.first { it.seatId == match.activeSeatIndex }.displayName,
            tablePlays = tablePlays,
            currentTablePlay = match.trickState.currentCombination?.let { combination ->
                val owner = match.seats.first { it.seatId == match.trickState.lastWinningSeatIndex }
                TablePlaySummary(
                    ownerViewSeat = owner.seatId.toViewSeat(localSeatId),
                    ownerName = owner.displayName,
                    combinationLabel = combination.type.displayName,
                    cardLabels = combination.cards.sortedWith(Card.gameComparator).map(Card::displayName),
                    stackOrder = tablePlays.size,
                )
            },
            lastActionMessage = lastActionMessage,
            canSubmitPlay = engine.canSubmitSelectedCards(
                match = match,
                seatIndex = localSeatId,
                selectedCardIds = selectedCardIds,
            ),
            canPass = engine.canPass(
                match = match,
                seatIndex = localSeatId,
            ),
            remainingTurnSeconds = null,
            resultSummary = match.result?.let { result ->
                ResultSummary(
                    winnerName = match.seats.first { it.seatId == result.winnerSeatIndex }.displayName,
                    rankingLines = result.scoreSummary.summaryLines,
                    roundScores = result.scoreSummary.roundScores,
                )
            },
            isHumanTurn = match.phase != MatchPhase.FINISHED && match.activeSeatIndex == localSeatId,
            isLocalDisconnected = false,
        )
    }

    companion object {
        const val DEFAULT_LOCAL_SEAT_ID = 0
        private const val VIEW_SEAT_COUNT = 4
    }

    private fun Int.toViewSeat(localSeatId: Int): ViewSeat {
        return when ((this - localSeatId).mod(VIEW_SEAT_COUNT)) {
            0 -> ViewSeat.SELF
            1 -> ViewSeat.LEFT
            2 -> ViewSeat.TOP
            3 -> ViewSeat.RIGHT
            else -> error("Unexpected relative seat offset")
        }
    }
}
