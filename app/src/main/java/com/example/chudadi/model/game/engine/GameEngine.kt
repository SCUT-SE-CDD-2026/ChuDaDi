@file:Suppress("ReturnCount")

package com.example.chudadi.model.game.engine

import com.example.chudadi.model.game.entity.Card
import com.example.chudadi.model.game.entity.CardRank
import com.example.chudadi.model.game.entity.CardSuit
import com.example.chudadi.model.game.entity.Match
import com.example.chudadi.model.game.entity.MatchPhase
import com.example.chudadi.model.game.entity.Seat
import com.example.chudadi.model.game.entity.SeatControllerType
import com.example.chudadi.model.game.entity.SeatStatus
import com.example.chudadi.model.game.entity.TrickState
import com.example.chudadi.model.game.rule.CombinationEvaluator
import java.util.UUID
import kotlin.random.Random

data class ActionResult(
    val match: Match,
    val success: Boolean,
    val message: String? = null,
    val error: GameActionError? = null,
)

open class GameEngine(
    private val random: Random = Random.Default,
    private val evaluator: CombinationEvaluator = CombinationEvaluator(),
) {
    open fun startLocalMatch(): Match {
        val shuffledDeck = Card.standardDeck().shuffled(random)
        val seats = listOf(
            createSeat(0, "You", SeatControllerType.HUMAN, shuffledDeck.subList(0, 13)),
            createSeat(1, "AI 1", SeatControllerType.RULE_BASED_AI, shuffledDeck.subList(13, 26)),
            createSeat(2, "AI 2", SeatControllerType.RULE_BASED_AI, shuffledDeck.subList(26, 39)),
            createSeat(3, "AI 3", SeatControllerType.RULE_BASED_AI, shuffledDeck.subList(39, 52)),
        )

        val openingCard = Card(suit = CardSuit.DIAMONDS, rank = CardRank.THREE)
        val openingSeat = seats.first { seat -> seat.hand.any { it.id == openingCard.id } }

        return Match(
            matchId = UUID.randomUUID().toString(),
            phase = MatchPhase.PLAYER_TURN,
            seats = seats,
            activeSeatIndex = openingSeat.seatId,
            trickState = TrickState(
                leadSeatIndex = openingSeat.seatId,
                lastWinningSeatIndex = openingSeat.seatId,
                currentCombination = null,
                passCount = 0,
                roundNumber = 1,
            ),
            playHistory = listOf("${openingSeat.displayName} leads the first round"),
            result = null,
        )
    }

    open fun submitSelectedCards(
        match: Match,
        seatIndex: Int,
        selectedCardIds: Set<String>,
    ): ActionResult {
        if (match.phase == MatchPhase.FINISHED) {
            return ActionResult(
                match = match,
                success = false,
                error = GameActionError.MATCH_FINISHED,
            )
        }
        if (match.activeSeatIndex != seatIndex) {
            return ActionResult(
                match = match,
                success = false,
                error = GameActionError.NOT_CURRENT_TURN,
            )
        }

        val seat = match.seats.first { it.seatId == seatIndex }
        val selectedCards = seat.hand.filter { it.id in selectedCardIds }
        val candidate = evaluator.parse(selectedCards)
            ?: return ActionResult(
                match = match,
                success = false,
                error = GameActionError.INVALID_PLAY_TYPE,
            )

        if (requiresOpeningThree(match, seatIndex) && candidate.cards.none { it.id == OPENING_CARD.id }) {
            return ActionResult(
                match = match,
                success = false,
                error = GameActionError.OPENING_MOVE_MUST_CONTAIN_DIAMOND_THREE,
            )
        }

        if (!evaluator.canBeat(candidate, match.trickState.currentCombination)) {
            return ActionResult(
                match = match,
                success = false,
                error = GameActionError.PLAY_DOES_NOT_BEAT_CURRENT,
            )
        }

        val updatedMatch = TurnResolver.applyPlay(
            match = match,
            seatIndex = seatIndex,
            combination = candidate,
        )
        val message = if (updatedMatch.phase == MatchPhase.FINISHED) {
            "${seat.displayName} wins the match."
        } else {
            "${seat.displayName} played ${candidate.displayName}"
        }
        return ActionResult(match = updatedMatch, success = true, message = message)
    }

    open fun passTurn(
        match: Match,
        seatIndex: Int,
    ): ActionResult {
        if (match.phase == MatchPhase.FINISHED) {
            return ActionResult(
                match = match,
                success = false,
                error = GameActionError.MATCH_FINISHED,
            )
        }
        if (match.activeSeatIndex != seatIndex) {
            return ActionResult(
                match = match,
                success = false,
                error = GameActionError.NOT_CURRENT_TURN,
            )
        }
        if (!canPass(match, seatIndex)) {
            return ActionResult(
                match = match,
                success = false,
                error = GameActionError.CANNOT_PASS_LEAD_TURN,
            )
        }

        val seat = match.seats.first { it.seatId == seatIndex }
        return ActionResult(
            match = TurnResolver.applyPass(match, seatIndex),
            success = true,
            message = "${seat.displayName} 要不起",
        )
    }

    open fun canSubmitSelectedCards(
        match: Match?,
        seatIndex: Int,
        selectedCardIds: Set<String>,
    ): Boolean {
        val activeMatch = match ?: return false
        if (activeMatch.phase == MatchPhase.FINISHED || activeMatch.activeSeatIndex != seatIndex) {
            return false
        }
        val seat = activeMatch.seats.first { it.seatId == seatIndex }
        val selectedCards = seat.hand.filter { it.id in selectedCardIds }
        val candidate = evaluator.parse(selectedCards) ?: return false
        if (requiresOpeningThree(activeMatch, seatIndex) && candidate.cards.none { it.id == OPENING_CARD.id }) {
            return false
        }
        return evaluator.canBeat(candidate, activeMatch.trickState.currentCombination)
    }

    open fun canPass(
        match: Match?,
        seatIndex: Int,
    ): Boolean {
        val activeMatch = match ?: return false
        if (activeMatch.phase == MatchPhase.FINISHED || activeMatch.activeSeatIndex != seatIndex) {
            return false
        }
        return activeMatch.trickState.currentCombination != null
    }

    open fun evaluator(): CombinationEvaluator = evaluator

    private fun createSeat(
        seatId: Int,
        displayName: String,
        controllerType: SeatControllerType,
        cards: List<Card>,
    ): Seat {
        return Seat(
            seatId = seatId,
            displayName = displayName,
            controllerType = controllerType,
            hand = cards.sortedWith(Card.gameComparator),
            status = SeatStatus.ACTIVE,
        )
    }

    private fun requiresOpeningThree(
        match: Match,
        seatIndex: Int,
    ): Boolean {
        return match.trickState.currentCombination == null &&
            match.trickState.roundNumber == 1 &&
            match.trickState.passCount == 0 &&
            match.trickState.leadSeatIndex == seatIndex &&
            match.trickState.lastWinningSeatIndex == seatIndex
    }

    companion object {
        private val OPENING_CARD = Card(
            suit = CardSuit.DIAMONDS,
            rank = CardRank.THREE,
        )
    }
}
