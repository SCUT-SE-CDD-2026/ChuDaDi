package com.example.chudadi.ai.base.variant

import com.example.chudadi.model.game.entity.Match
import com.example.chudadi.model.game.rule.GameRuleSet

/**
 * 观测编码器接口。
 *
 * 将游戏状态编码为模型期望的观测向量。
 * 每种模型结构对应一个实现。
 */
interface ObservationEncoder {
    /** 观测向量维度 */
    val inputDim: Int

    /**
     * 编码当前游戏状态为观测向量。
     *
     * @param match 当前对局状态
     * @param seatIndex 需要决策的座位索引
     * @param ruleSet 当前规则集（南方/北方）
     * @return 观测向量 FloatArray，长度等于 [inputDim]
     */
    fun encode(match: Match, seatIndex: Int, ruleSet: GameRuleSet): FloatArray
}
