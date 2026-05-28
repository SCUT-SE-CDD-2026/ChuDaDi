package com.example.chudadi.ai.base

import com.example.chudadi.model.game.entity.Card

/**
 * AI决策结果密封类
 *
 * 表示AI在一次决策中的选择：
 * - PlayCards: 出指定的牌
 * - Pass: 跳过不出牌
 * - Error: 决策过程中发生错误
 */
sealed class AIDecision {
    /**
     * 出牌决策
     *
     * @property cards 要出的牌列表，必须是一个合法的牌型组合
     */
    data class PlayCards(val cards: List<Card>) : AIDecision()

    /**
     * 跳过决策
     *
     * 表示AI选择不出牌
     */
    data object Pass : AIDecision()

    /**
     * 错误决策
     *
     * @property reason 错误原因描述
     * @property exception 可选的异常对象
     */
    data class Error(
        val reason: String,
        val exception: Throwable? = null,
    ) : AIDecision()
}
