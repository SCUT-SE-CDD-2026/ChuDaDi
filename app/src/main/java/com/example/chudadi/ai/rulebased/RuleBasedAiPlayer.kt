package com.example.chudadi.ai.rulebased

import com.example.chudadi.model.game.entity.Match
import com.example.chudadi.model.game.entity.PlayCombination
import com.example.chudadi.model.game.rule.CombinationEvaluator
import com.example.chudadi.model.game.entity.Card
import com.example.chudadi.model.game.entity.CardRank
import com.example.chudadi.model.game.entity.CardSuit
import com.example.chudadi.model.game.rule.CombinationType
import com.example.chudadi.model.game.rule.GameRules

sealed interface AiDecision {
    data class Play(
        val cardIds: Set<String>,
    ) : AiDecision

    data object Pass : AiDecision
}

class RuleBasedAiPlayer(
    private val evaluatorFactory: (Match) -> CombinationEvaluator = { match ->
        CombinationEvaluator(GameRules.forRuleSet(match.ruleSet))
    },
) {
    fun decideAction(
        match: Match,
        seatIndex: Int,
    ): AiDecision {
        val evaluator = evaluatorFactory(match)
        val seat = match.seats.first { it.seatId == seatIndex }
        val currentCombination = match.trickState.currentCombination
        val allCombinations = evaluator.generateAllValidCombinations(seat.hand)

        val candidate =
            if (currentCombination == null) {
                chooseLeadCombination(allCombinations)
            } else {
                chooseResponseCombination(
                    combinations = allCombinations,
                    currentCombination = currentCombination,
                    evaluator = evaluator,
                    rules = GameRules.forRuleSet(match.ruleSet),
                )
            }

        return if (candidate == null) {
            AiDecision.Pass
        } else {
            AiDecision.Play(candidate.cards.map { it.id }.toSet())
        }
    }

    private fun chooseLeadCombination(combinations: List<PlayCombination>): PlayCombination? {
        val openingCard = Card(suit = CardSuit.DIAMONDS, rank = CardRank.THREE)
        val openingCandidates = combinations.filter { combination ->
            combination.cards.any { it.id == openingCard.id }
        }
        val candidatePool = if (openingCandidates.isNotEmpty()) openingCandidates else combinations

        return candidatePool
            .filter { it.cardCount == 1 }
            .minWithOrNull(compareBy<PlayCombination> { it.primaryRank }.thenBy { it.primarySuit })
            ?: candidatePool.minWithOrNull(
                compareBy<PlayCombination> { it.cardCount }
                    .thenBy { it.type.typePower }
                    .thenBy { it.primaryRank }
                    .thenBy { it.primarySuit },
            )
    }

    private fun chooseResponseCombination(
        combinations: List<PlayCombination>,
        currentCombination: PlayCombination,
        evaluator: CombinationEvaluator,
        rules: GameRules,
    ): PlayCombination? {
        val sameTypeResponses = combinations.filter { combination ->
            !rules.isBomb(combination.type) &&
                combination.type == currentCombination.type &&
                combination.cardCount == currentCombination.cardCount &&
                evaluator.canBeat(combination, currentCombination)
        }

        return combinations
            .filter { evaluator.canBeat(it, currentCombination) }
            .filter { combination ->
                if (!rules.isBomb(combination.type) || !rules.bombRequiresNoSameTypeResponse) {
                    return@filter true
                }
                sameTypeResponses.isEmpty() || currentCombination.type == CombinationType.FOUR_OF_A_KIND_BOMB
            }
            .minWithOrNull(
                compareBy<PlayCombination> { it.type.typePower }
                    .thenBy { it.primaryRank }
                    .thenBy { it.primarySuit },
            )
    }
}
