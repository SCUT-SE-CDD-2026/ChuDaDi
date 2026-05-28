package com.example.chudadi.ai.rulebased

import com.example.chudadi.ai.base.AIDecision
import com.example.chudadi.ai.base.AIDifficulty
import com.example.chudadi.ai.base.AIPlayerController
import com.example.chudadi.ai.base.ValidCombinationResolver
import com.example.chudadi.model.game.entity.Card
import com.example.chudadi.model.game.entity.Match
import com.example.chudadi.model.game.rule.GameRuleSet

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
        return ruleBasedAi.decideAction(match, seatIndex)
    }

    override fun getValidActions(
        handCards: List<Card>,
        match: Match,
        ruleSet: GameRuleSet,
    ): List<List<Card>> = ValidCombinationResolver.resolve(handCards, match, ruleSet, seatIndex)
}
