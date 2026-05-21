package com.example.chudadi.controller.game

import com.example.chudadi.BuildConfig
import com.example.chudadi.R
import com.example.chudadi.model.game.engine.GameEngine
import com.example.chudadi.model.game.entity.Card
import com.example.chudadi.model.game.entity.Match
import com.example.chudadi.model.game.entity.MatchPhase
import com.example.chudadi.model.game.entity.PlayCombination
import com.example.chudadi.model.game.entity.Seat
import com.example.chudadi.model.game.entity.SeatStatus
import com.example.chudadi.model.game.snapshot.DebugHandSummary
import com.example.chudadi.model.game.snapshot.MatchUiState
import com.example.chudadi.model.game.snapshot.OpponentSummary
import com.example.chudadi.model.game.snapshot.ResultSummary
import com.example.chudadi.model.game.snapshot.TablePlaySummary
import com.example.chudadi.model.game.snapshot.ViewSeat

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
        val selectedCards = localSeat?.hand?.filter { it.id in selectedCardIds }?.map(Card::id)?.toSet() ?: emptySet()
        val tablePlays = buildTablePlays(match = match, localSeatId = localSeatId)
        val currentTablePlay = buildCurrentTablePlay(
            match = match,
            localSeatId = localSeatId,
            stackOrder = (tablePlays.maxOfOrNull { it.stackOrder } ?: -1) + 1,
        )

        return MatchUiState(
            matchId = match.matchId,
            phase = match.phase,
            playerHand = localSeat?.hand?.sortedWith(Card.gameComparator).orEmpty(),
            selectedCards = selectedCards,
            opponentSummaries = buildOpponentSummaries(match, localSeatId, localSeat != null),
            currentActorName = match.seats.first { it.seatId == match.activeSeatIndex }.displayName,
            tablePlays = tablePlays,
            currentTablePlay = currentTablePlay,
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
            remainingTurnSeconds = null,
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
            localSeatId = localSeatId,
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
                    viewSeat = seat.seatId.toViewSeat(localSeatId),
                    authoritySeatId = seat.seatId,
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
        private const val VIEW_SEAT_COUNT = 4
    }

    private fun buildTablePlays(
        match: Match,
        localSeatId: Int,
    ): List<TablePlaySummary> {
        return match.trickState.tablePlays.entries.mapIndexed { index, (seatId, combination) ->
            val owner = match.seats.first { it.seatId == seatId }
            buildTablePlaySummary(
                match = match,
                localSeatId = localSeatId,
                owner = owner,
                combination = combination,
                stackOrder = match.trickState.tablePlayOrders[seatId] ?: index,
            )
        }
    }

    private fun buildCurrentTablePlay(
        match: Match,
        localSeatId: Int,
        stackOrder: Int,
    ): TablePlaySummary? {
        val combination = match.trickState.currentCombination ?: return null
        val owner = match.seats.first { it.seatId == match.trickState.lastWinningSeatIndex }
        return buildTablePlaySummary(
            match = match,
            localSeatId = localSeatId,
            owner = owner,
            combination = combination,
            stackOrder = stackOrder,
        )
    }

    private fun buildTablePlaySummary(
        match: Match,
        localSeatId: Int,
        owner: Seat,
        combination: PlayCombination,
        stackOrder: Int,
    ): TablePlaySummary {
        val cardLabels = combination.cards
            .sortedWith(Card.gameComparator)
            .map(Card::displayName)

        return TablePlaySummary(
            playId = buildTablePlayId(
                roundNumber = match.trickState.roundNumber,
                seatId = owner.seatId,
                cardLabels = cardLabels,
            ),
            ownerViewSeat = owner.seatId.toViewSeat(localSeatId),
            ownerName = owner.displayName,
            combinationLabel = combination.type.displayName,
            cardLabels = cardLabels,
            stackOrder = stackOrder,
        )
    }

    private fun buildTablePlayId(
        roundNumber: Int,
        seatId: Int,
        cardLabels: List<String>,
    ): String {
        return listOf(
            roundNumber.toString(),
            seatId.toString(),
            cardLabels.joinToString(separator = "|"),
        ).joinToString(separator = ":")
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
