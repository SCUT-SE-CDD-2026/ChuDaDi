package com.example.chudadi.model.game.engine

import com.example.chudadi.model.game.entity.Match
import com.example.chudadi.model.game.entity.MatchPhase
import com.example.chudadi.model.game.entity.PlayCombination
import com.example.chudadi.model.game.entity.RoundResult
import com.example.chudadi.model.game.entity.ScoreSummary
import com.example.chudadi.model.game.entity.Seat
import com.example.chudadi.model.game.entity.SeatStatus

object TurnResolver {
    fun applyPlay(
        match: Match,
        seatIndex: Int,
        combination: PlayCombination,
    ): Match {
        val currentSeat = match.seats.first { it.seatId == seatIndex }
        val handIds = currentSeat.hand.map { it.id }.toSet()
        val playedIds = combination.cards.map { it.id }.toSet()
        require(playedIds.all { it in handIds }) {
            "Played cards $playedIds are not all present in hand $handIds for seat $seatIndex"
        }
        val remainingHand = currentSeat.hand.filterNot { card ->
            combination.cards.any { selected -> selected.id == card.id }
        }

        val updatedSeats = match.seats.map { seat ->
            when {
                seat.seatId == currentSeat.seatId -> {
                    seat.copy(
                        hand = remainingHand,
                        status = if (remainingHand.isEmpty()) SeatStatus.FINISHED else SeatStatus.ACTIVE,
                    )
                }

                else -> seat
            }
        }
        val nextPlayedCardHistory = match.trickState.playedCardHistory + (
            seatIndex to (match.trickState.playedCardHistory[seatIndex].orEmpty() + combination.cards)
        )

        if (remainingHand.isEmpty()) {
            val rankedSeats = assignFinishOrder(updatedSeats, winnerSeatId = seatIndex)
            return match.copy(
                phase = MatchPhase.FINISHED,
                seats = rankedSeats,
                trickState = match.trickState.copy(
                    leadSeatIndex = seatIndex,
                    lastWinningSeatIndex = seatIndex,
                    currentCombination = combination,
                    passCount = 0,
                    tablePlays = match.trickState.tablePlays + (seatIndex to combination),
                    playedCardHistory = nextPlayedCardHistory,
                    passedSeatIndices = emptySet(),
                    tablePlayOrders = match.trickState.tablePlayOrders + (
                        seatIndex to match.trickState.nextTablePlayOrder
                    ),
                    nextTablePlayOrder = match.trickState.nextTablePlayOrder + 1,
                ),
                playHistory = match.playHistory + "${currentSeat.displayName} played ${combination.displayName}",
                result = createResult(
                    match = match,
                    seats = rankedSeats,
                    winnerSeatId = seatIndex,
                    winningCombination = combination,
                    baopeiSeatId = match.trickState.baopeiSeatId,
                ),
            )
        }

        return match.copy(
            phase = MatchPhase.PLAYER_TURN,
            seats = updatedSeats,
            activeSeatIndex = nextActiveSeatIndex(updatedSeats, seatIndex)
                ?: throw TurnResolutionException("No active seat found from $seatIndex"),
            trickState = match.trickState.copy(
                leadSeatIndex = seatIndex,
                lastWinningSeatIndex = seatIndex,
                currentCombination = combination,
                passCount = 0,
                tablePlays = match.trickState.tablePlays + (seatIndex to combination),
                playedCardHistory = nextPlayedCardHistory,
                passedSeatIndices = emptySet(),
                tablePlayOrders = match.trickState.tablePlayOrders + (
                    seatIndex to match.trickState.nextTablePlayOrder
                ),
                nextTablePlayOrder = match.trickState.nextTablePlayOrder + 1,
            ),
            playHistory = match.playHistory + "${currentSeat.displayName} played ${combination.displayName}",
        )
    }

    fun applyPass(
        match: Match,
        seatIndex: Int,
    ): Match {
        val currentSeat = match.seats.first { it.seatId == seatIndex }
        val updatedSeats = match.seats.map { seat ->
            if (seat.seatId == seatIndex) {
                seat.copy(status = SeatStatus.PASSED)
            } else {
                seat
            }
        }

        val unfinishedSeats = updatedSeats.filter { it.status != SeatStatus.FINISHED }
        val nextPassCount = match.trickState.passCount + 1
        val shouldResetRound = nextPassCount >= unfinishedSeats.size - 1
        // 防御性清理：PASS 玩家理论上不会出现在 tablePlays 中（只有 applyPlay 才会写入），
        // 但此处显式移除可避免异常场景下 tablePlays 中残留旧数据。
        val tablePlaysAfterPass = match.trickState.tablePlays - seatIndex

        return if (shouldResetRound) {
            val resetSeats = updatedSeats.map { seat ->
                if (seat.status != SeatStatus.FINISHED) {
                    seat.copy(status = SeatStatus.ACTIVE)
                } else {
                    seat
                }
            }
            match.copy(
                phase = MatchPhase.ROUND_RESET,
                seats = resetSeats,
                activeSeatIndex = match.trickState.lastWinningSeatIndex,
                trickState = match.trickState.copy(
                    leadSeatIndex = match.trickState.lastWinningSeatIndex,
                    currentCombination = null,
                    passCount = 0,
                    roundNumber = match.trickState.roundNumber + 1,
                    tablePlays = emptyMap(),
                    passedSeatIndices = emptySet(),
                    // 一轮结束重置包赔标记。若同一轮内有多个玩家触发包赔（极端情况），
                    // 后触发的玩家会覆盖前者；当前规则未定义多触发情形，以最后触发者为准。
                    baopeiSeatId = null,
                    tablePlayOrders = emptyMap(),
                    nextTablePlayOrder = 0,
                ),
                playHistory = match.playHistory + "${currentSeat.displayName} passed",
            )
        } else {
            match.copy(
                phase = MatchPhase.PLAYER_TURN,
                seats = updatedSeats,
                activeSeatIndex = nextActiveSeatIndex(updatedSeats, seatIndex)
                    ?: throw TurnResolutionException("No active seat found from $seatIndex"),
                trickState = match.trickState.copy(
                    passCount = nextPassCount,
                    tablePlays = tablePlaysAfterPass,
                    passedSeatIndices = match.trickState.passedSeatIndices + seatIndex,
                    tablePlayOrders = match.trickState.tablePlayOrders - seatIndex,
                ),
                playHistory = match.playHistory + "${currentSeat.displayName} passed",
            )
        }
    }


    private fun assignFinishOrder(
        seats: List<Seat>,
        winnerSeatId: Int,
    ): List<Seat> {
        val winner = seats.first { it.seatId == winnerSeatId }
        val others = seats
            .filterNot { it.seatId == winnerSeatId }
            .sortedWith(compareBy<Seat> { it.hand.size }.thenBy { it.seatId })

        val ranking = listOf(winner) + others
        return seats.map { seat ->
            val position = ranking.indexOfFirst { it.seatId == seat.seatId }
            seat.copy(
                status = SeatStatus.FINISHED,
                finishOrder = position + 1,
            )
        }
    }

    private fun createResult(
        match: Match,
        seats: List<Seat>,
        winnerSeatId: Int,
        winningCombination: PlayCombination,
        baopeiSeatId: Int? = null,
    ): RoundResult {
        val orderedSeats = seats.sortedBy { it.finishOrder ?: Int.MAX_VALUE }
        val roundScores = ScoreCalculator.calculateRoundScores(
            ruleSet = match.ruleSet,
            seats = orderedSeats,
            winnerSeatId = winnerSeatId,
            winningCombination = winningCombination,
            baopeiSeatId = baopeiSeatId?.takeIf { it != winnerSeatId },
        )
        return RoundResult(
            winnerSeatIndex = orderedSeats.first().seatId,
            ranking = orderedSeats.map(Seat::seatId),
            scoreSummary = ScoreSummary(
                summaryLines = orderedSeats.mapIndexed { index, seat ->
                    "${index + 1}. ${seat.displayName} (${seat.hand.size} cards left)"
                },
                roundScores = roundScores,
            ),
        )
    }
}
