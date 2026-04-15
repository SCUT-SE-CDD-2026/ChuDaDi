@file:Suppress("ReturnCount", "TooManyFunctions")

package com.example.chudadi.model.game.engine

import com.example.chudadi.model.game.entity.Card
import com.example.chudadi.model.game.entity.CardRank
import com.example.chudadi.model.game.entity.CardSuit
import com.example.chudadi.model.game.entity.Match
import com.example.chudadi.model.game.entity.MatchPhase
import com.example.chudadi.model.game.entity.PlayCombination
import com.example.chudadi.model.game.entity.Seat
import com.example.chudadi.model.game.entity.SeatControllerType
import com.example.chudadi.model.game.entity.SeatStatus
import com.example.chudadi.model.game.entity.TrickState
import com.example.chudadi.model.game.rule.CombinationEvaluator
import com.example.chudadi.model.game.rule.CombinationType
import com.example.chudadi.model.game.rule.GameRuleSet
import com.example.chudadi.model.game.rule.GameRules
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
    private val defaultRuleSet: GameRuleSet = GameRuleSet.SOUTHERN,
) {
    open fun startLocalMatch(ruleSet: GameRuleSet = defaultRuleSet): Match {
        val shuffledDeck = Card.standardDeck().shuffled(random)
        val seats = listOf(
            createSeat(0, "You", SeatControllerType.HUMAN, shuffledDeck.subList(0, 13)),
            createSeat(1, "AI 1", SeatControllerType.RULE_BASED_AI, shuffledDeck.subList(13, 26)),
            createSeat(2, "AI 2", SeatControllerType.RULE_BASED_AI, shuffledDeck.subList(26, 39)),
            createSeat(3, "AI 3", SeatControllerType.RULE_BASED_AI, shuffledDeck.subList(39, 52)),
        )
        return buildMatch(ruleSet, seats)
    }

    open fun startLocalMatch(
        ruleSet: GameRuleSet = defaultRuleSet,
        seatConfigs: List<Triple<Int, String, SeatControllerType>>,
    ): Match {
        require(seatConfigs.size == 4) { "seatConfigs must have exactly 4 entries" }
        val shuffledDeck = Card.standardDeck().shuffled(random)
        val sorted = seatConfigs.sortedBy { it.first }
        val seats = sorted.mapIndexed { listIndex, (seatId, name, controllerType) ->
            createSeat(seatId, name, controllerType, shuffledDeck.subList(listIndex * 13, (listIndex + 1) * 13))
        }
        return buildMatch(ruleSet, seats)
    }

    private fun buildMatch(ruleSet: GameRuleSet, seats: List<Seat>): Match {
        val openingCard = Card(suit = CardSuit.DIAMONDS, rank = CardRank.THREE)
        val openingSeat = seats.first { seat -> seat.hand.any { it.id == openingCard.id } }
        return Match(
            matchId = UUID.randomUUID().toString(),
            ruleSet = ruleSet,
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
        val evaluator = evaluator(match.ruleSet)
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

        val pendingBaoPay = detectBaoPayViolation(
            match = match,
            seat = seat,
            candidate = candidate,
            evaluator = evaluator,
        )

        val updatedMatch = TurnResolver.applyPlay(
            match = match,
            seatIndex = seatIndex,
            combination = candidate,
        ).let { playedMatch ->
            playedMatch.copy(
                trickState = playedMatch.trickState.copy(
                    pendingBaoPaySeatId = pendingBaoPay?.first,
                    pendingBaoPayProtectedSeatId = pendingBaoPay?.second,
                ),
            )
        }

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
        val passError = getPassError(match, seatIndex)
        val evaluator = evaluator(match.ruleSet)
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
        if (passError != null) {
            return ActionResult(
                match = match,
                success = false,
                error = passError,
            )
        }

        val seat = match.seats.first { it.seatId == seatIndex }
        val pendingBaoPay = detectBaoPayViolationOnPass(match, seat, evaluator)
        val updatedMatch = TurnResolver.applyPass(match, seatIndex).let { nextMatch ->
            when {
                nextMatch.phase == MatchPhase.ROUND_RESET ->
                    nextMatch.copy(
                        trickState = nextMatch.trickState.copy(
                            pendingBaoPaySeatId = null,
                            pendingBaoPayProtectedSeatId = null,
                        ),
                    )

                match.trickState.pendingBaoPayProtectedSeatId == seatIndex ->
                    nextMatch.copy(
                        trickState = nextMatch.trickState.copy(
                            pendingBaoPaySeatId = null,
                            pendingBaoPayProtectedSeatId = null,
                        ),
                    )

                pendingBaoPay != null ->
                    nextMatch.copy(
                        trickState = nextMatch.trickState.copy(
                            pendingBaoPaySeatId = pendingBaoPay.first,
                            pendingBaoPayProtectedSeatId = pendingBaoPay.second,
                        ),
                    )

                else -> nextMatch
            }
        }

        return ActionResult(
            match = updatedMatch,
            success = true,
            message = "${seat.displayName} passed",
        )
    }

    open fun canSubmitSelectedCards(
        match: Match?,
        seatIndex: Int,
        selectedCardIds: Set<String>,
    ): Boolean {
        val activeMatch = match ?: return false
        val evaluator = evaluator(activeMatch.ruleSet)
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
        return getPassError(activeMatch, seatIndex) == null
    }

    open fun evaluator(ruleSet: GameRuleSet = defaultRuleSet): CombinationEvaluator {
        return CombinationEvaluator(GameRules.forRuleSet(ruleSet))
    }

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

    private fun getPassError(
        match: Match,
        seatIndex: Int,
    ): GameActionError? {
        if (match.phase == MatchPhase.FINISHED || match.activeSeatIndex != seatIndex) {
            return GameActionError.NOT_CURRENT_TURN
        }

        val currentCombination = match.trickState.currentCombination
            ?: return GameActionError.CANNOT_PASS_LEAD_TURN

        val rules = GameRules.forRuleSet(match.ruleSet)
        if (!rules.mustBeatIfPossible) {
            return null
        }

        val seat = match.seats.first { it.seatId == seatIndex }
        val evaluator = evaluator(match.ruleSet)
        val hasMandatoryResponse = evaluator
            .generateAllValidCombinations(seat.hand)
            .any { candidate ->
                candidate.type == currentCombination.type &&
                    candidate.cardCount == currentCombination.cardCount &&
                    evaluator.canBeat(candidate, currentCombination)
            }

        return if (hasMandatoryResponse) GameActionError.MUST_BEAT_IF_POSSIBLE else null
    }

    private fun detectBaoPayViolation(
        match: Match,
        seat: Seat,
        candidate: PlayCombination,
        evaluator: CombinationEvaluator,
    ): Pair<Int, Int>? {
        val currentCombination = match.trickState.currentCombination ?: return null
        if (candidate.type != CombinationType.SINGLE) {
            return null
        }
        val baoPayContext = getBaoPayContext(match, seat.seatId) ?: return null
        val maxSingle = findMaxLegalSingle(seat, evaluator, currentCombination) ?: return null

        val playedIsMax =
            candidate.primaryRank == maxSingle.primaryRank &&
                candidate.primarySuit == maxSingle.primarySuit

        return if (playedIsMax) {
            null
        } else {
            seat.seatId to baoPayContext.protectedSeatId
        }
    }

    private fun detectBaoPayViolationOnPass(
        match: Match,
        seat: Seat,
        evaluator: CombinationEvaluator,
    ): Pair<Int, Int>? {
        val baoPayContext = getBaoPayContext(match, seat.seatId) ?: return null
        val currentCombination = match.trickState.currentCombination ?: return null
        val maxSingle = findMaxLegalSingle(seat, evaluator, currentCombination) ?: return null
        return if (maxSingle.cards.isNotEmpty()) {
            seat.seatId to baoPayContext.protectedSeatId
        } else {
            null
        }
    }

    private fun getBaoPayContext(
        match: Match,
        seatId: Int,
    ): BaoPayContext? {
        val currentCombination = match.trickState.currentCombination ?: return null
        if (currentCombination.type != CombinationType.SINGLE) {
            return null
        }

        val nextSeat = nextActiveSeat(match.seats, seatId) ?: return null
        return if (nextSeat.hand.size == 1) {
            BaoPayContext(protectedSeatId = nextSeat.seatId)
        } else {
            null
        }
    }

    private fun findMaxLegalSingle(
        seat: Seat,
        evaluator: CombinationEvaluator,
        currentCombination: PlayCombination,
    ): PlayCombination? {
        val legalSingles = evaluator
            .generateAllValidCombinations(seat.hand)
            .filter { it.type == CombinationType.SINGLE && evaluator.canBeat(it, currentCombination) }
        return legalSingles.maxWithOrNull(
            compareBy<PlayCombination> { it.primaryRank }.thenBy { it.primarySuit },
        )
    }

    private fun nextActiveSeat(
        seats: List<Seat>,
        fromSeatId: Int,
    ): Seat? {
        val maxIndex = seats.maxOf { it.seatId }
        var cursor = fromSeatId
        repeat(maxIndex + 1) {
            cursor = (cursor + 1) % (maxIndex + 1)
            val nextSeat = seats.first { it.seatId == cursor }
            if (nextSeat.status != SeatStatus.FINISHED) {
                return nextSeat
            }
        }
        return null
    }

    companion object {
        private val OPENING_CARD = Card(
            suit = CardSuit.DIAMONDS,
            rank = CardRank.THREE,
        )
    }

    private data class BaoPayContext(
        val protectedSeatId: Int,
    )
}
