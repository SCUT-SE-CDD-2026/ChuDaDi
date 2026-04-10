package com.example.chudadi.ai.base

import com.example.chudadi.model.game.entity.Card
import com.example.chudadi.model.game.entity.Match
import com.example.chudadi.model.game.rule.GameRuleSet

/**
 * AI玩家控制器接口
 *
 * 定义AI玩家的标准接口，使AI对于游戏引擎而言与真人玩家无区别。
 * 采用适配器模式，不同的AI实现（ONNX、规则基础等）都实现此接口。
 */
interface AIPlayerController {
    /**
     * AI玩家所在的座位索引
     */
    val seatIndex: Int

    /**
     * AI难度级别
     */
    val difficulty: AIDifficulty

    /**
     * 是否为AI控制器
     */
    val isAI: Boolean get() = true

    /**
     * 请求AI做出决策
     *
     * @param match 当前游戏状态
     * @param ruleSet 当前游戏规则设置（南方/北方）
     * @return AI决策结果
     */
    suspend fun requestDecision(
        match: Match,
        ruleSet: GameRuleSet,
    ): AIDecision

    /**
     * 获取当前有效的出牌动作列表
     *
     * @param handCards AI手牌
     * @param match 当前游戏状态
     * @param ruleSet 游戏规则设置
     * @return 所有合法的出牌组合列表
     */
    fun getValidActions(
        handCards: List<Card>,
        match: Match,
        ruleSet: GameRuleSet,
    ): List<List<Card>>
}
