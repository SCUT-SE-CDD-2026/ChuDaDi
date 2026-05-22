package com.example.chudadi.ai.base

import android.util.Log
import com.example.chudadi.model.game.entity.Card
import com.example.chudadi.model.game.entity.Match
import com.example.chudadi.model.game.engine.MatchFactory
import com.example.chudadi.model.game.rule.CombinationComparator
import com.example.chudadi.model.game.rule.CombinationEvaluator
import com.example.chudadi.model.game.rule.CombinationGenerator
import com.example.chudadi.model.game.rule.CombinationParser
import com.example.chudadi.model.game.rule.GameRuleSet
import com.example.chudadi.model.game.rule.GameRules

/**
 * 合法出牌组合解析器
 *
 * 从 AI 控制器中提取的共享逻辑，用于计算当前合法的出牌组合列表。
 * 区别于 ONNX 的 action 概念（包含 pass），这里只返回合法的牌型组合。
 */
object ValidCombinationResolver {
    private const val TAG = "ValidCombinationResolver"

    /**
     * 获取当前合法的出牌组合列表
     *
     * @param handCards 当前手牌
     * @param match 当前游戏状态
     * @param ruleSet 游戏规则设置
     * @param seatIndex 当前决策的座位索引（用于开局♦3约束判断）
     * @return 所有合法的出牌组合列表（每个组合是 Card 列表）
     */
    fun resolve(
        handCards: List<Card>,
        match: Match,
        ruleSet: GameRuleSet,
        seatIndex: Int = match.activeSeatIndex,
    ): List<List<Card>> {
        return try {
            val rules = GameRules.forRuleSet(ruleSet)
            val parser = CombinationParser()
            val comparator = CombinationComparator(rules)
            val generator = CombinationGenerator(parser, comparator)
            val currentCombination = match.trickState.currentCombination

            val combinations = if (currentCombination == null) {
                generator.generateAllValidCombinations(handCards).map { it.cards }
            } else {
                generator.generateAllValidCombinations(handCards)
                    .filter { comparator.canBeat(it, currentCombination) }
                    .map { it.cards }
            }

            if (requiresOpeningThree(match, seatIndex)) {
                combinations.filter { cards -> cards.any { it.id == MatchFactory.OPENING_CARD.id } }
            } else {
                combinations
            }
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Failed to resolve valid combinations for ${handCards.size} cards", e)
            emptyList()
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Invalid arguments for valid combinations for ${handCards.size} cards", e)
            emptyList()
        }
    }

    /**
     * 判断是否为开局第一手，必须包含♦3。
     * 条件与 PlayValidator.requiresOpeningThree 一致。
     */
    private fun requiresOpeningThree(match: Match, seatIndex: Int): Boolean {
        return match.trickState.currentCombination == null &&
            match.trickState.roundNumber == 1 &&
            match.trickState.passedSeatIndices.isEmpty() &&
            match.trickState.leadSeatIndex == seatIndex &&
            match.trickState.lastWinningSeatIndex == seatIndex
    }

    /**
     * 判断北方规则下是否可以 pass。
     *
     * 与引擎侧 [PlayValidator.getPassError] 对齐：
     * 只要有同张数非炸弹组合能压过当前牌，就不能 pass。
     * 领出时（`currentCombination == null`）不能 pass。
     *
     * @param match 当前对局
     * @param seatIndex 视角座位
     * @param ruleSet 必须为 [GameRuleSet.NORTHERN]
     */
    fun canPassUnderNorthernRule(match: Match, seatIndex: Int, ruleSet: GameRuleSet): Boolean {
        val currentCombination = match.trickState.currentCombination
        val seat = match.seats.getOrNull(seatIndex)
        val rules = GameRules.forRuleSet(ruleSet)
        if (currentCombination == null || seat == null || !rules.mustBeatIfPossible) {
            return currentCombination != null
        }
        val evaluator = CombinationEvaluator(rules)
        val hasMandatoryResponse = evaluator
            .generateAllValidCombinations(seat.hand)
            .any { candidate ->
                !rules.isBomb(candidate.type) &&
                    !rules.isBomb(currentCombination.type) &&
                    candidate.cardCount == currentCombination.cardCount &&
                    evaluator.canBeat(candidate, currentCombination)
            }
        return !hasMandatoryResponse
    }
}
