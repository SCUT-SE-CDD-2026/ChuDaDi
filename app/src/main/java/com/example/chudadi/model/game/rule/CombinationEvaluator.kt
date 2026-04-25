package com.example.chudadi.model.game.rule

import com.example.chudadi.model.game.entity.Card
import com.example.chudadi.model.game.entity.PlayCombination

class CombinationEvaluator(
    private val rules: GameRules = GameRules.forRuleSet(GameRuleSet.SOUTHERN),
) {
    private val parser = CombinationParser()
    private val comparator = CombinationComparator(rules)
    private val generator = CombinationGenerator(parser, comparator)

    fun parse(cards: List<Card>): PlayCombination? = parser.parse(cards)

    fun canBeat(
        candidate: PlayCombination,
        current: PlayCombination?,
    ): Boolean = comparator.canBeat(candidate, current)

    fun hasMandatoryResponse(
        candidates: List<PlayCombination>,
        currentCombination: PlayCombination,
    ): Boolean {
        return candidates.any { candidate ->
            !rules.isBomb(candidate.type) &&
                !rules.isBomb(currentCombination.type) &&
                candidate.cardCount == currentCombination.cardCount &&
                canBeat(candidate, currentCombination)
        }
    }

    fun hasSameTypeBeatOption(
        candidates: List<PlayCombination>,
        currentCombination: PlayCombination,
    ): Boolean {
        return candidates.any { candidate ->
            !rules.isBomb(candidate.type) &&
                candidate.type == currentCombination.type &&
                candidate.cardCount == currentCombination.cardCount &&
                canBeat(candidate, currentCombination)
        }
    }

    fun generateAllValidCombinations(cards: List<Card>): List<PlayCombination> =
        generator.generateAllValidCombinations(cards)
}
