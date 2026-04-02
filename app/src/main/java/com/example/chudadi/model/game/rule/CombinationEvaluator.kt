package com.example.chudadi.model.game.rule

import com.example.chudadi.model.game.entity.Card
import com.example.chudadi.model.game.entity.PlayCombination

class CombinationEvaluator(
    rules: GameRules = GameRules.forRuleSet(GameRuleSet.SOUTHERN),
) {
    private val parser = CombinationParser(rules)
    private val comparator = CombinationComparator(rules)
    private val generator = CombinationGenerator(parser, comparator)

    fun parse(cards: List<Card>): PlayCombination? = parser.parse(cards)

    fun canBeat(
        candidate: PlayCombination,
        current: PlayCombination?,
    ): Boolean = comparator.canBeat(candidate, current)

    fun generateAllValidCombinations(cards: List<Card>): List<PlayCombination> =
        generator.generateAllValidCombinations(cards)
}
