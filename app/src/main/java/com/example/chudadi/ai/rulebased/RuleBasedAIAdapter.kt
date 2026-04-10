package com.example.chudadi.ai.rulebased

import com.example.chudadi.ai.base.AIDecision
import com.example.chudadi.ai.base.AIDifficulty
import com.example.chudadi.ai.base.AIPlayerController
import com.example.chudadi.model.game.entity.Card
import com.example.chudadi.model.game.entity.Match
import com.example.chudadi.model.game.entity.PlayCombination
import com.example.chudadi.model.game.rule.CombinationComparator
import com.example.chudadi.model.game.rule.CombinationGenerator
import com.example.chudadi.model.game.rule.CombinationParser
import com.example.chudadi.model.game.rule.GameRuleSet
import com.example.chudadi.model.game.rule.GameRules

/**
 * 规则型AI适配器
 *
 * 将RuleBasedAiPlayer适配为AIPlayerController接口
 */
class RuleBasedAIAdapter(
    override val seatIndex: Int,
    override val difficulty: AIDifficulty = AIDifficulty.NORMAL,
) : AIPlayerController {

    private val ruleBasedAi = RuleBasedAiPlayer()

    override suspend fun requestDecision(
        match: Match,
        ruleSet: GameRuleSet,
    ): AIDecision {
        val decision = ruleBasedAi.decideAction(match, seatIndex)
        return when (decision) {
            is AiDecision.Play -> {
                AIDecision.PlayCards(
                    match.seats.first { it.seatId == seatIndex }
                        .hand
                        .filter { it.id in decision.cardIds },
                )
            }

            AiDecision.Pass -> AIDecision.Pass
        }
    }

    override fun getValidActions(
        handCards: List<Card>,
        match: Match,
        ruleSet: GameRuleSet,
    ): List<List<Card>> {
        val rules = GameRules.forRuleSet(ruleSet)
        val parser = CombinationParser(rules)
        val comparator = CombinationComparator(rules)
        val generator = CombinationGenerator(parser, comparator)
        val currentCombination = match.trickState.currentCombination

        return if (currentCombination == null) {
            generateAllValidCombinations(handCards, generator)
        } else {
            generateValidResponses(
                handCards = handCards,
                currentCombination = currentCombination,
                generator = generator,
                comparator = comparator,
            )
        }
    }

    private fun generateAllValidCombinations(
        handCards: List<Card>,
        generator: CombinationGenerator,
    ): List<List<Card>> {
        return try {
            generator.generateAllValidCombinations(handCards).map { it.cards }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun generateValidResponses(
        handCards: List<Card>,
        currentCombination: PlayCombination,
        generator: CombinationGenerator,
        comparator: CombinationComparator,
    ): List<List<Card>> {
        return try {
            generator.generateAllValidCombinations(handCards)
                .filter { comparator.canBeat(it, currentCombination) }
                .map { it.cards }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
