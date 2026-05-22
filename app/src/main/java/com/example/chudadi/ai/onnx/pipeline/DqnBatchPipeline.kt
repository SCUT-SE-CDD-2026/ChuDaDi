package com.example.chudadi.ai.onnx.pipeline

import com.example.chudadi.ai.base.AIDecision
import com.example.chudadi.ai.base.AIDifficulty
import com.example.chudadi.ai.base.ValidCombinationResolver
import com.example.chudadi.ai.base.variant.InferencePipeline
import com.example.chudadi.ai.base.variant.ObservationEncoder
import com.example.chudadi.ai.onnx.ActionDecoder
import com.example.chudadi.ai.onnx.ActionFeatureEncoder
import com.example.chudadi.ai.onnx.OnnxModel
import com.example.chudadi.model.game.entity.Card
import com.example.chudadi.model.game.entity.Match
import com.example.chudadi.model.game.rule.CombinationParser
import com.example.chudadi.model.game.rule.GameRuleSet

/**
 * DQN 批量推理管道。
 *
 * 封装当前 DQN 双输入模型的完整决策流程：
 * 1. 构建动作候选集（合法出牌 + 可选 pass）
 * 2. 编码观测向量
 * 3. 批量推理 Q 值
 * 4. 解码为 [AIDecision]
 */
class DqnBatchPipeline : InferencePipeline {

    private val actionFeatureEncoder = ActionFeatureEncoder()
    private val decoder = ActionDecoder()
    private val actionIdMapper = BitmaskActionIdMapper()

    internal data class ActionCandidate(
        val actionId: Long,
        val feature: FloatArray,
    )

    override suspend fun decide(
        model: OnnxModel,
        obsEncoder: ObservationEncoder,
        match: Match,
        seatIndex: Int,
        handCards: List<Card>,
        validActions: List<List<Card>>,
        ruleSet: GameRuleSet,
        difficulty: AIDifficulty,
    ): AIDecision {
        val candidates = buildActionCandidates(
            handCards = handCards,
            validActions = validActions,
            match = match,
            seatIndex = seatIndex,
            ruleSet = ruleSet,
        )
        val validActionMask = candidates.map { it.actionId }.toLongArray()

        val obs = obsEncoder.encode(match, seatIndex, ruleSet)
        val prediction = model.predictActionValuesWithObs(obs, candidates.map { it.feature })

        return if (prediction != null) {
            decoder.decode(
                modelOutput = prediction,
                handCards = handCards,
                validActionMask = validActionMask,
                difficulty = difficulty,
            )
        } else {
            AIDecision.Error("ONNX returned empty prediction")
        }
    }

    /**
     * 构建动作候选列表（公开供测试使用）。
     */
    internal fun buildActionCandidates(
        handCards: List<Card>,
        validActions: List<List<Card>>,
        match: Match,
        seatIndex: Int,
        ruleSet: GameRuleSet,
    ): List<ActionCandidate> {
        val candidates = mutableListOf<ActionCandidate>()
        val seenActionIds = mutableSetOf<Long>()
        val parser = CombinationParser()

        for (cards in validActions) {
            val actionId = actionIdMapper.cardsToId(cards)
            if (!seenActionIds.add(actionId)) continue
            val parsedType = parser.parse(cards)?.type
            val feature = actionFeatureEncoder.encodeActionFeature(
                handCards = handCards,
                actionCards = cards,
                actionType = parsedType,
            )
            candidates += ActionCandidate(actionId = actionId, feature = feature)
        }

        if (isPassLegal(match, seatIndex, ruleSet)) {
            val passId = BitmaskActionIdMapper.PASS_ACTION_ID
            if (seenActionIds.add(passId)) {
                val passFeature = actionFeatureEncoder.encodeActionFeature(
                    handCards = handCards,
                    actionCards = emptyList(),
                    actionType = null,
                )
                candidates += ActionCandidate(actionId = passId, feature = passFeature)
            }
        }

        // Guarantee at least one candidate to avoid empty-batch inference.
        if (candidates.isEmpty()) {
            val passFeature = actionFeatureEncoder.encodeActionFeature(
                handCards = handCards,
                actionCards = emptyList(),
                actionType = null,
            )
            candidates += ActionCandidate(actionId = 0L, feature = passFeature)
        }

        return candidates
    }

    private fun isPassLegal(match: Match, seatIndex: Int, ruleSet: GameRuleSet): Boolean {
        return when (ruleSet) {
            GameRuleSet.SOUTHERN -> match.trickState.currentCombination != null
            GameRuleSet.NORTHERN -> ValidCombinationResolver.canPassUnderNorthernRule(
                match, seatIndex, ruleSet,
            )
        }
    }
}
