package com.example.chudadi.ai.rulebased

import com.example.chudadi.model.game.entity.Match
import com.example.chudadi.model.game.entity.PlayCombination
import com.example.chudadi.model.game.rule.CombinationEvaluator
import com.example.chudadi.model.game.entity.Card
import com.example.chudadi.model.game.entity.CardRank
import com.example.chudadi.model.game.entity.CardSuit

sealed interface AiDecision {
    data class Play(
        val cardIds: Set<String>,
    ) : AiDecision

    data object Pass : AiDecision
}

class RuleBasedAiPlayer(
    private val evaluator: CombinationEvaluator = CombinationEvaluator(),
) {
    fun decideAction(
        match: Match,
        seatIndex: Int,
    ): AiDecision {
        val seat = match.seats.first { it.seatId == seatIndex }
        val currentCombination = match.trickState.currentCombination
        val allCombinations = evaluator.generateAllValidCombinations(seat.hand)

        val candidate =
            if (currentCombination == null) {
                chooseLeadCombination(allCombinations)
            } else {
                chooseResponseCombination(allCombinations, currentCombination)
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
    ): PlayCombination? {
        return combinations
            .filter { evaluator.canBeat(it, currentCombination) }
            .minWithOrNull(
                compareBy<PlayCombination> { it.type.typePower }
                    .thenBy { it.primaryRank }
                    .thenBy { it.primarySuit },
            )
    }
}
