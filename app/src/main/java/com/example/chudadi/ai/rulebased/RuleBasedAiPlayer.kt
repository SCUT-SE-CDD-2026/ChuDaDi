package com.example.chudadi.ai.rulebased

import com.example.chudadi.model.game.entity.Match
import com.example.chudadi.model.game.entity.PlayCombination
import com.example.chudadi.model.game.rule.CombinationEvaluator
import com.example.chudadi.model.game.entity.Card
import com.example.chudadi.model.game.entity.CardRank
import com.example.chudadi.model.game.entity.CardSuit
import com.example.chudadi.model.game.rule.CombinationType

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
        val cardsWithThreeOfDiamonds = combinations.filter { combination ->
            combination.cards.contains(Card(CardSuit.DIAMONDS, CardRank.THREE)) &&
                combination.cards.size > 1
        }

        if (cardsWithThreeOfDiamonds.isNotEmpty()) {
            return cardsWithThreeOfDiamonds.random()
        }

        val singleThreeOfDiamonds = combinations.filter { combination ->
            combination.cards.contains(Card(CardSuit.DIAMONDS, CardRank.THREE)) &&
                combination.cards.size == 1
        }

        if (singleThreeOfDiamonds.isNotEmpty()) {
            return singleThreeOfDiamonds.first()
        }
        
        return if ((0..1).random() == 0) {
            combinations
                .sortedWith(
                    compareByDescending<PlayCombination> { it.type.typePower }
                        .thenBy { it.primaryRank }
                )
                .firstOrNull()
        } else {
            combinations.randomOrNull()
        }
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
