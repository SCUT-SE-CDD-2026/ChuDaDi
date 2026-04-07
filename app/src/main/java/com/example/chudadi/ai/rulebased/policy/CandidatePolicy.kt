package com.example.chudadi.ai.rulebased.policy

import com.example.chudadi.ai.rulebased.OPENING_CARD
import com.example.chudadi.ai.rulebased.RuleBasedAiContext
import com.example.chudadi.model.game.entity.PlayCombination
import com.example.chudadi.model.game.rule.CombinationType

internal interface CandidatePolicy {
    fun generateLeadCandidates(context: RuleBasedAiContext): List<PlayCombination>

    fun filterLegalResponses(context: RuleBasedAiContext): List<PlayCombination>
}

internal class DefaultCandidatePolicy(
    private val turnConstraintPolicy: TurnConstraintPolicy = DefaultTurnConstraintPolicy(),
) : CandidatePolicy {
    override fun generateLeadCandidates(context: RuleBasedAiContext): List<PlayCombination> {
        if (!turnConstraintPolicy.requiresOpeningThree(context)) {
            return context.allCombinations
        }

        return context.allCombinations.filter { combination ->
            combination.cards.any { it.id == OPENING_CARD.id }
        }
    }

    override fun filterLegalResponses(context: RuleBasedAiContext): List<PlayCombination> {
        val currentCombination = context.currentCombination ?: return emptyList()
        val sameTypeResponses =
            context.allCombinations.filter { combination ->
                !context.rules.isBomb(combination.type) &&
                    combination.type == currentCombination.type &&
                    combination.cardCount == currentCombination.cardCount &&
                    context.evaluator.canBeat(combination, currentCombination)
            }

        return context.allCombinations
            .filter { context.evaluator.canBeat(it, currentCombination) }
            .filter { combination ->
                if (!context.rules.isBomb(combination.type) || !context.rules.bombRequiresNoSameTypeResponse) {
                    return@filter true
                }
                sameTypeResponses.isEmpty() || currentCombination.type == CombinationType.FOUR_OF_A_KIND_BOMB
            }
    }
}
