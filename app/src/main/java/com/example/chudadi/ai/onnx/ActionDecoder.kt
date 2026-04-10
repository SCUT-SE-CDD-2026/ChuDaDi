package com.example.chudadi.ai.onnx

import android.util.Log
import com.example.chudadi.ai.DifficultyConfig
import com.example.chudadi.ai.base.AIDecision
import com.example.chudadi.ai.base.AIDifficulty
import com.example.chudadi.model.game.entity.Card
import kotlin.math.exp
import kotlin.random.Random

/**
 * 动作解码器
 *
 * 将ONNX模型的输出张量解码为AI决策（出牌或跳过）。
 * 遵循RLCard ChuDaDi环境的52位掩码动作空间规范。
 *
 * 动作空间说明：
 * - 0 表示 pass（跳过）
 * - 非零值是52位掩码，表示要出的牌（bit i = 1 表示出第i张牌）
 */
class ActionDecoder {

    companion object {
        private const val TAG = "ActionDecoder"
        private const val PASS_ACTION_ID = 0L
        private const val TOTAL_CARDS = 52
    }

    private val random = Random.Default

    /**
     * 解码模型输出为AI决策
     *
     * @param modelOutput 模型输出的FloatArray（Q值或策略概率）
     * @param handCards 当前手牌
     * @param validActionMask 有效动作掩码（哪些动作是合法的）
     * @param difficulty AI难度级别
     * @param ruleSet 游戏规则设置
     * @return AI决策结果
     */
    fun decode(
        modelOutput: FloatArray,
        handCards: List<Card>,
        validActionMask: LongArray?,
        difficulty: AIDifficulty,
    ): AIDecision {
        val config = DifficultyConfig.forDifficulty(difficulty)

        val validActions = getValidActions(validActionMask, handCards)
        val canDecide = handCards.isNotEmpty() &&
            validActions.isNotEmpty() &&
            !(validActions.size == 1 && validActions[0] == PASS_ACTION_ID)

        val decision = if (!canDecide) {
            AIDecision.Pass
        } else {
            val qValues = modelOutput.map { it / config.temperature }.toFloatArray()
            when (difficulty) {
                AIDifficulty.HARD -> selectTopAction(qValues, validActions, handCards)
                AIDifficulty.NORMAL -> selectTopKWithExploration(
                    qValues = qValues,
                    validActions = validActions,
                    handCards = handCards,
                    k = config.topK,
                    explorationRate = config.explorationRate,
                )
                AIDifficulty.EASY -> selectWithMistake(
                    qValues = qValues,
                    validActions = validActions,
                    handCards = handCards,
                    config = config,
                )
            }
        }
        return decision
    }

    /**
     * 从有效动作掩码解析有效动作
     *
     * @param validActionMask 有效动作ID列表（Long类型，52位掩码）
     * @param handCards 当前手牌（用于验证动作）
     * @return 有效动作ID列表
     */
    private fun getValidActions(validActionMask: LongArray?, handCards: List<Card>): List<Long> {
        if (validActionMask == null) {
            // 如果没有提供掩码，生成所有可能的动作
            return generateAllPossibleActions(handCards)
        }
        return validActionMask.toList()
    }

    /**
     * 生成所有可能的出牌动作
     * 简化实现：返回单张、对子等基本动作
     */
    private fun generateAllPossibleActions(handCards: List<Card>): List<Long> {
        val actions = mutableListOf<Long>(PASS_ACTION_ID)

        // 生成单张动作
        for (card in handCards) {
            val actionId = 1L shl GameStateEncoder.cardToIndex(card)
            actions.add(actionId)
        }

        // 当前仅生成单张动作，组合动作由上层 validActionMask 提供。

        return actions.distinct()
    }

    /**
     * 将动作ID（52位掩码）转换为卡牌列表
     */
    private fun actionIdToCards(actionId: Long, handCards: List<Card>): List<Card> {
        if (actionId == PASS_ACTION_ID) return emptyList()

        val result = mutableListOf<Card>()
        val handIndexMap = handCards.associateBy { GameStateEncoder.cardToIndex(it) }

        for (i in 0 until TOTAL_CARDS) {
            if ((actionId shr i) and 1L == 1L) {
                handIndexMap[i]?.let { result.add(it) }
            }
        }

        return result
    }

    /**
     * 困难难度：选择Q值最高的动作
     */
    private fun selectTopAction(
        qValues: FloatArray,
        validActions: List<Long>,
        handCards: List<Card>,
    ): AIDecision {
        var bestAction = PASS_ACTION_ID
        var bestQ = Float.NEGATIVE_INFINITY

        for (actionId in validActions) {
            val q = getQValueForAction(qValues, actionId, validActions)
            if (q > bestQ) {
                bestQ = q
                bestAction = actionId
            }
        }

        return if (bestAction == PASS_ACTION_ID) {
            AIDecision.Pass
        } else {
            val cards = actionIdToCards(bestAction, handCards)
            if (cards.isNotEmpty()) {
                AIDecision.PlayCards(cards)
            } else {
                AIDecision.Pass
            }
        }
    }

    /**
     * 普通难度：Top-K加权随机选择，有一定探索率
     */
    private fun selectTopKWithExploration(
        qValues: FloatArray,
        validActions: List<Long>,
        handCards: List<Card>,
        k: Int,
        explorationRate: Float,
    ): AIDecision {
        val selectedActionId = if (random.nextFloat() < explorationRate) {
            Log.d(TAG, "Exploration: random action selected")
            validActions.random(random)
        } else {
            pickTopKActionBySoftmax(qValues, validActions, k) ?: PASS_ACTION_ID
        }
        return actionToDecision(selectedActionId, handCards)
    }

    private fun pickTopKActionBySoftmax(
        qValues: FloatArray,
        validActions: List<Long>,
        k: Int,
    ): Long? {
        val actionQValues = validActions
            .map { actionId -> actionId to getQValueForAction(qValues, actionId, validActions) }
            .sortedByDescending { it.second }
            .take(k)
        if (actionQValues.isEmpty()) {
            return null
        }

        val expQs = actionQValues.map { exp(it.second.toDouble()) }
        val sumExp = expQs.sum()
        val probabilities = expQs.map { it / sumExp }
        var randomPoint = random.nextDouble()
        var candidate = actionQValues.first().first
        for ((index, prob) in probabilities.withIndex()) {
            randomPoint -= prob
            if (randomPoint <= 0) {
                candidate = actionQValues[index].first
                break
            }
        }
        return candidate
    }

    /**
     * 简单难度：随机选择，可能犯错
     */
    private fun selectWithMistake(
        qValues: FloatArray,
        validActions: List<Long>,
        handCards: List<Card>,
        config: DifficultyConfig,
    ): AIDecision {
        // 探索：随机选择
        if (random.nextFloat() < config.explorationRate) {
            val randomAction = validActions.random(random)
            Log.d(TAG, "Easy mode: random action (exploration)")
            return actionToDecision(randomAction, handCards)
        }

        // 犯错：选择较差的选项
        if (random.nextFloat() < config.mistakeProbability) {
            // 从后50%中选择
            val sortedActions = validActions.sortedBy { getQValueForAction(qValues, it, validActions) }
            val startIndex = sortedActions.size / 2
            val mistakeCandidates = sortedActions.subList(
                startIndex.coerceAtMost(sortedActions.size),
                sortedActions.size,
            )
            val mistakeAction = mistakeCandidates.random(random)
            Log.d(TAG, "Easy mode: mistake action selected")
            return actionToDecision(mistakeAction, handCards)
        }

        // 正常选择
        return selectTopKWithExploration(
            qValues,
            validActions,
            handCards,
            k = config.topK,
            explorationRate = 0f
        )
    }

    /**
     * 获取动作的Q值
     *
     * 注意：RLCard DMC 模型的输出是动作特征对应的Q值。
     * 动作空间的大小通常很大（2^52），但模型只输出有效动作的Q值。
     * 这里假设模型输出的顺序与 validActions 的顺序一致。
     *
     * @param qValues 模型输出的Q值数组
     * @param actionId 动作ID（52位掩码）
     * @param validActions 有效动作列表（用于确定索引）
     * @return 该动作的Q值
     */
    private fun getQValueForAction(
        qValues: FloatArray,
        actionId: Long,
        validActions: List<Long>
    ): Float {
        // 在有效动作列表中查找该动作的索引
        val index = validActions.indexOf(actionId)
        if (index != -1 && index < qValues.size) {
            return qValues[index]
        }
        // 如果找不到，返回一个默认低值
        return Float.NEGATIVE_INFINITY
    }

    /**
     * 将动作ID转换为决策
     */
    private fun actionToDecision(actionId: Long, handCards: List<Card>): AIDecision {
        return if (actionId == PASS_ACTION_ID) {
            AIDecision.Pass
        } else {
            val cards = actionIdToCards(actionId, handCards)
            if (cards.isNotEmpty()) {
                AIDecision.PlayCards(cards)
            } else {
                AIDecision.Pass
            }
        }
    }
}
