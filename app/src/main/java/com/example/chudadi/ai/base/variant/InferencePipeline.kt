package com.example.chudadi.ai.base.variant

import com.example.chudadi.ai.base.AIDecision
import com.example.chudadi.ai.base.AIDifficulty
import com.example.chudadi.ai.onnx.OnnxModel
import com.example.chudadi.model.game.entity.Card
import com.example.chudadi.model.game.entity.Match
import com.example.chudadi.model.game.rule.GameRuleSet

/**
 * 推理管道接口。
 *
 * 封装"编码 → 推理 → 解码"的完整决策流程。
 * 不同模型架构（双输入 DQN、单输入策略网络等）对应不同实现。
 */
interface InferencePipeline {

    /**
     * 执行完整决策流程。
     *
     * @param model 已加载的 ONNX 模型
     * @param obsEncoder 观测编码器
     * @param match 当前对局状态
     * @param seatIndex 需要决策的座位索引
     * @param handCards 当前手牌
     * @param validActions 合法出牌组合列表
     * @param ruleSet 当前规则集
     * @param difficulty AI 难度
     * @return AI 决策结果
     */
    @Suppress("LongParameterList")
    suspend fun decide(
        model: OnnxModel,
        obsEncoder: ObservationEncoder,
        match: Match,
        seatIndex: Int,
        handCards: List<Card>,
        validActions: List<List<Card>>,
        ruleSet: GameRuleSet,
        difficulty: AIDifficulty,
    ): AIDecision
}
