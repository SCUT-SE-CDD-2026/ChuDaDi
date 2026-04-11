package com.example.chudadi.model.game.engine

import com.example.chudadi.model.game.entity.Match
import com.example.chudadi.model.game.entity.MatchPhase
import com.example.chudadi.model.game.entity.PlayCombination
import com.example.chudadi.model.game.entity.RoundResult
import com.example.chudadi.model.game.entity.ScoreSummary
import com.example.chudadi.model.game.entity.Seat
import com.example.chudadi.model.game.entity.SeatStatus
import com.example.chudadi.model.game.rule.GameRules

object TurnResolver {
    fun applyPlay(
        match: Match,
        seatIndex: Int,
        combination: PlayCombination,
    ): Match {
        val currentSeat = match.seats.first { it.seatId == seatIndex }
        val rules = GameRules.forRuleSet(match.ruleSet)
        val nextBombCount = match.totalBombCount + if (rules.isBomb(combination.type)) 1 else 0
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

                seat.status != SeatStatus.FINISHED -> seat.copy(status = SeatStatus.ACTIVE)
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
                ),
                playHistory = match.playHistory + "${currentSeat.displayName} played ${combination.displayName}",
                totalBombCount = nextBombCount,
                result = createResult(
                    match = match,
                    seats = rankedSeats,
                    winnerSeatId = seatIndex,
                    totalBombCount = nextBombCount,
                ),
            )
        }

        return match.copy(
            phase = MatchPhase.PLAYER_TURN,
            seats = updatedSeats,
            activeSeatIndex = nextActiveSeatIndex(updatedSeats, seatIndex),
            trickState = match.trickState.copy(
                leadSeatIndex = seatIndex,
                lastWinningSeatIndex = seatIndex,
                currentCombination = combination,
                passCount = 0,
                tablePlays = match.trickState.tablePlays + (seatIndex to combination),
                playedCardHistory = nextPlayedCardHistory,
            ),
            playHistory = match.playHistory + "${currentSeat.displayName} played ${combination.displayName}",
            totalBombCount = nextBombCount,
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
                ),
                playHistory = match.playHistory + "${currentSeat.displayName} passed",
            )
        } else {
            match.copy(
                phase = MatchPhase.PLAYER_TURN,
                seats = updatedSeats,
                activeSeatIndex = nextActiveSeatIndex(updatedSeats, seatIndex),
                trickState = match.trickState.copy(
                    passCount = nextPassCount,
                    tablePlays = tablePlaysAfterPass,
                ),
                playHistory = match.playHistory + "${currentSeat.displayName} passed",
            )
        }
    }

    private fun nextActiveSeatIndex(
        seats: List<Seat>,
        fromSeatId: Int,
    ): Int {
        val maxIndex = seats.maxOf { it.seatId }
        var cursor = fromSeatId
        repeat(maxIndex + 1) {
            cursor = (cursor + 1) % (maxIndex + 1)
            val nextSeat = seats.first { it.seatId == cursor }
            if (nextSeat.status != SeatStatus.FINISHED) {
                return cursor
            }
        }
        return fromSeatId
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
                status = if (seat.seatId == winnerSeatId) SeatStatus.FINISHED else seat.status,
                finishOrder = position + 1,
            )
        }
    }

    private fun createResult(
        match: Match,
        seats: List<Seat>,
        winnerSeatId: Int,
        totalBombCount: Int,
    ): RoundResult {
        val orderedSeats = seats.sortedBy { it.finishOrder ?: Int.MAX_VALUE }
        val roundScores = ScoreCalculator.calculateRoundScores(
            ruleSet = match.ruleSet,
            seats = orderedSeats,
            winnerSeatId = winnerSeatId,
            totalBombCount = totalBombCount,
        )
        return RoundResult(
            winnerSeatIndex = orderedSeats.first().seatId,
            ranking = orderedSeats.map(Seat::seatId),
            scoreSummary = ScoreSummary(
                summaryLines = orderedSeats.mapIndexed { index, seat ->
                    "${index + 1}. ${seat.displayName} (${seat.hand.size} cards left)"
                },
                bombCount = totalBombCount,
                roundScores = roundScores,
            ),
        )
    }
}
