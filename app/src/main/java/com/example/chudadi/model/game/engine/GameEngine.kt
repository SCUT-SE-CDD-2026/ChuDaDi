package com.example.chudadi.model.game.engine

import com.example.chudadi.model.game.entity.Match
import com.example.chudadi.model.game.entity.MatchPhase
import com.example.chudadi.model.game.entity.SeatControllerType
import com.example.chudadi.model.game.rule.CombinationEvaluator
import com.example.chudadi.model.game.rule.GameRuleSet
import com.example.chudadi.model.game.rule.GameRules
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
    private val playValidator = PlayValidator()
    private val baopeiChecker = BaopeiChecker()

    open fun startLocalMatch(ruleSet: GameRuleSet = defaultRuleSet): Match {
        return MatchFactory.startLocalMatch(random, ruleSet)
    }

    open fun startLocalMatch(
        ruleSet: GameRuleSet = defaultRuleSet,
        seatConfigs: List<Triple<Int, String, SeatControllerType>>,
    ): Match {
        return MatchFactory.startLocalMatch(random, ruleSet, seatConfigs)
    }

    open fun submitSelectedCards(
        match: Match,
        seatIndex: Int,
        selectedCardIds: Set<String>,
    ): ActionResult {
        return try {
            when (val validation = playValidator.validatePlay(match, seatIndex, selectedCardIds)) {
                is PlayValidation.Invalid ->
                    ActionResult(match = match, success = false, error = validation.error)

                is PlayValidation.Valid -> {
                    val matchWithBaopei = baopeiChecker.checkAndApplyBaopei(
                        match = match,
                        seatIndex = seatIndex,
                        candidate = validation.candidate,
                    )
                    val updatedMatch = TurnResolver.applyPlay(
                        match = matchWithBaopei,
                        seatIndex = seatIndex,
                        combination = validation.candidate,
                    )
                    val message = if (updatedMatch.phase == MatchPhase.FINISHED) {
                        "${validation.seat.displayName} wins the match."
                    } else {
                        "${validation.seat.displayName} played ${validation.candidate.displayName}"
                    }
                    ActionResult(match = updatedMatch, success = true, message = message)
                }
            }
        } catch (e: TurnResolutionException) {
            ActionResult(match = match, success = false, message = e.message)
        }
    }

    open fun passTurn(
        match: Match,
        seatIndex: Int,
    ): ActionResult {
        val passError = playValidator.getPassError(match, seatIndex)
        if (passError != null) {
            return ActionResult(
                match = match,
                success = false,
                error = passError,
            )
        }

        return try {
            val seat = match.seats.first { it.seatId == seatIndex }
            ActionResult(
                match = TurnResolver.applyPass(match, seatIndex),
                success = true,
                message = "${seat.displayName} passed",
            )
        } catch (e: TurnResolutionException) {
            ActionResult(match = match, success = false, message = e.message)
        }
    }

    open fun canSubmitSelectedCards(
        match: Match?,
        seatIndex: Int,
        selectedCardIds: Set<String>,
    ): Boolean {
        val activeMatch = match ?: return false
        return playValidator.validatePlay(activeMatch, seatIndex, selectedCardIds) is PlayValidation.Valid
    }

    open fun canPass(
        match: Match?,
        seatIndex: Int,
    ): Boolean {
        val activeMatch = match ?: return false
        return playValidator.getPassError(activeMatch, seatIndex) == null
    }

    open fun evaluator(ruleSet: GameRuleSet = defaultRuleSet): CombinationEvaluator {
        return CombinationEvaluator(GameRules.forRuleSet(ruleSet))
    }
}
