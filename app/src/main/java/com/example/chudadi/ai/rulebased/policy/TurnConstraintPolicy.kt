package com.example.chudadi.ai.rulebased.policy

import com.example.chudadi.ai.rulebased.RuleBasedAiContext
import com.example.chudadi.model.game.entity.PlayCombination

internal interface TurnConstraintPolicy {
    fun requiresOpeningThree(context: RuleBasedAiContext): Boolean

    fun mustBeatIfPossible(
        context: RuleBasedAiContext,
        legalResponses: List<PlayCombination>,
    ): Boolean

    fun canPass(
        context: RuleBasedAiContext,
        legalResponses: List<PlayCombination>,
    ): Boolean = !mustBeatIfPossible(context, legalResponses)
}

internal class DefaultTurnConstraintPolicy : TurnConstraintPolicy {
    override fun requiresOpeningThree(context: RuleBasedAiContext): Boolean {
        return context.match.trickState.currentCombination == null &&
            context.match.trickState.roundNumber == OPENING_ROUND_NUMBER &&
            context.match.trickState.passCount == ZERO_PASS_COUNT &&
            context.match.trickState.leadSeatIndex == context.seatIndex &&
            context.match.trickState.lastWinningSeatIndex == context.seatIndex
    }

    override fun mustBeatIfPossible(
        context: RuleBasedAiContext,
        legalResponses: List<PlayCombination>,
    ): Boolean {
        val currentCombination = context.currentCombination ?: return false
        if (!context.rules.mustBeatIfPossible) {
            return false
        }

        return legalResponses.any { candidate ->
            !context.rules.isBomb(candidate.type) &&
                !context.rules.isBomb(currentCombination.type) &&
                candidate.type == currentCombination.type &&
                candidate.cardCount == currentCombination.cardCount
        }
    }

    private companion object {
        const val OPENING_ROUND_NUMBER = 1
        const val ZERO_PASS_COUNT = 0
    }
}
