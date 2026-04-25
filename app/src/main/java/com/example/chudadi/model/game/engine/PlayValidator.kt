@file:Suppress("ReturnCount")

package com.example.chudadi.model.game.engine

import com.example.chudadi.model.game.entity.Match
import com.example.chudadi.model.game.entity.MatchPhase
import com.example.chudadi.model.game.entity.PlayCombination
import com.example.chudadi.model.game.entity.Seat
import com.example.chudadi.model.game.rule.CombinationEvaluator
import com.example.chudadi.model.game.rule.GameRuleSet
import com.example.chudadi.model.game.rule.GameRules

class PlayValidator(
    private val evaluatorFactory: (GameRuleSet) -> CombinationEvaluator = { ruleSet ->
        CombinationEvaluator(GameRules.forRuleSet(ruleSet))
    },
) {
    fun validatePlay(
        match: Match,
        seatIndex: Int,
        selectedCardIds: Set<String>,
    ): PlayValidation {
        if (match.phase == MatchPhase.FINISHED) {
            return PlayValidation.Invalid(GameActionError.MATCH_FINISHED)
        }
        if (match.activeSeatIndex != seatIndex) {
            return PlayValidation.Invalid(GameActionError.NOT_CURRENT_TURN)
        }
        val evaluator = evaluatorFactory(match.ruleSet)
        val seat = match.seats.first { it.seatId == seatIndex }
        val selectedCards = seat.hand.filter { it.id in selectedCardIds }
        if (selectedCards.size != selectedCardIds.size) {
            return PlayValidation.Invalid(GameActionError.INVALID_PLAY_TYPE)
        }
        val candidate = evaluator.parse(selectedCards)
            ?: return PlayValidation.Invalid(GameActionError.INVALID_PLAY_TYPE)
        if (requiresOpeningThree(match, seatIndex) && candidate.cards.none { it.id == MatchFactory.OPENING_CARD.id }) {
            return PlayValidation.Invalid(GameActionError.OPENING_MOVE_MUST_CONTAIN_DIAMOND_THREE)
        }
        if (!evaluator.canBeat(candidate, match.trickState.currentCombination)) {
            return PlayValidation.Invalid(GameActionError.PLAY_TOO_SMALL)
        }
        if (!canUseBombForCurrentTrick(match, seat, candidate, evaluator)) {
            return PlayValidation.Invalid(GameActionError.BOMB_USAGE_RESTRICTED)
        }
        return PlayValidation.Valid(seat, candidate, evaluator)
    }

    fun getPassError(
        match: Match,
        seatIndex: Int,
    ): GameActionError? {
        if (match.phase == MatchPhase.FINISHED) {
            return GameActionError.MATCH_FINISHED
        }
        if (match.activeSeatIndex != seatIndex) {
            return GameActionError.NOT_CURRENT_TURN
        }

        val currentCombination = match.trickState.currentCombination
            ?: return GameActionError.CANNOT_PASS_LEAD_TURN

        val rules = GameRules.forRuleSet(match.ruleSet)
        if (!rules.mustBeatIfPossible) {
            return null
        }

        val seat = match.seats.first { it.seatId == seatIndex }
        val evaluator = evaluatorFactory(match.ruleSet)
        val hasMandatoryResponse = evaluator.hasMandatoryResponse(
            candidates = evaluator.generateAllValidCombinations(seat.hand),
            currentCombination = currentCombination,
        )

        return if (hasMandatoryResponse) GameActionError.MUST_BEAT_IF_POSSIBLE else null
    }

    private fun canUseBombForCurrentTrick(
        match: Match,
        seat: Seat,
        candidate: PlayCombination,
        evaluator: CombinationEvaluator,
    ): Boolean {
        val currentCombination = match.trickState.currentCombination ?: return true
        val rules = GameRules.forRuleSet(match.ruleSet)
        if (!rules.isBomb(candidate.type) || rules.isBomb(currentCombination.type)) {
            return true
        }
        if (!rules.bombRequiresNoSameTypeResponse) {
            return true
        }

        val hasSameTypeBeatOption = evaluator.hasSameTypeBeatOption(
            candidates = evaluator.generateAllValidCombinations(seat.hand),
            currentCombination = currentCombination,
        )

        return !hasSameTypeBeatOption
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
}
