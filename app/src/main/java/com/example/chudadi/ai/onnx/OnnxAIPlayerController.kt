package com.example.chudadi.ai.onnx

import android.util.Log
import com.example.chudadi.ai.base.AIDecision
import com.example.chudadi.ai.base.AIDifficulty
import com.example.chudadi.ai.base.AIPlayerController
import com.example.chudadi.ai.base.FallbackDiagnosticLogger
import com.example.chudadi.ai.base.ValidCombinationResolver
import com.example.chudadi.ai.base.variant.OnnxModelVariant
import com.example.chudadi.model.game.entity.Card
import com.example.chudadi.model.game.entity.Match
import com.example.chudadi.model.game.rule.GameRuleSet
import java.io.File

/**
 * ONNX AI玩家控制器
 *
 * 实现AIPlayerController接口，将ONNX模型包装成游戏可用的AI玩家。
 * 通过 [OnnxModelVariant] 支持可插拔的模型变体架构，
 * 实际的编码、推理、解码由 variant 提供的 pipeline 处理。
 *
 * @property seatIndex AI玩家座位索引
 * @property difficulty AI难度级别
 * @property modelPath ONNX模型文件路径
 * @property variant 模型变体描述（编码器、管道、I/O契约）
 */
class OnnxAIPlayerController(
    override val seatIndex: Int,
    override val difficulty: AIDifficulty,
    private val modelPath: String,
    private val variant: OnnxModelVariant,
) : AIPlayerController {

    /**
     * 向后兼容构造函数（无 variant 参数）。
     * 使用 V1 DQN 默认变体。
     */
    constructor(
        seatIndex: Int,
        difficulty: AIDifficulty,
        modelPath: String,
    ) : this(seatIndex, difficulty, modelPath, com.example.chudadi.ai.onnx.variant.V1DqnVariant.createDefault())

    companion object {
        private const val TAG = "OnnxAIPlayerController"
    }

    private val pipeline = variant.createPipeline()
    private val obsEncoder = variant.createObsEncoder()

    private val model: OnnxModel? = if (modelPath.isNotBlank() && File(modelPath).exists()) {
        Log.i(TAG, "[AI-$seatIndex] Loading ONNX model from: $modelPath")
        try {
            val m = OnnxModel(modelPath, variant.ioContract)
            Log.i(TAG, "[AI-$seatIndex] ONNX model loaded successfully, available=${m.isAvailable()}")
            m
        } catch (e: Exception) {
            Log.e(TAG, "[AI-$seatIndex] Failed to load ONNX model", e)
            null
        }
    } else {
        Log.w(
            TAG,
            "[AI-$seatIndex] Model path empty or file not found (path='$modelPath')",
        )
        null
    }

    init {
        if (model?.isAvailable() != true) {
            Log.w(TAG, "ONNX model not available")
        }
    }

    /**
     * 请求AI做出决策
     *
     * @param match 当前游戏状态
     * @param ruleSet 当前游戏规则设置
     * @return AI决策结果
     */
    override suspend fun requestDecision(match: Match, ruleSet: GameRuleSet): AIDecision {
        val seat = match.seats.getOrNull(seatIndex)
            ?: return AIDecision.Error("Seat $seatIndex not found in match")

        Log.i(
            TAG, "[AI-$seatIndex] Requesting decision: hand=${seat.hand.size} cards, " +
                "rule=$ruleSet, difficulty=$difficulty, " +
                "firstPlay=${match.trickState.currentCombination == null}"
        )

        val validActions = getValidActions(seat.hand, match, ruleSet)
        Log.d(TAG, "[AI-$seatIndex] Valid actions count: ${validActions.size}")

        val rawDecision = if (model?.isAvailable() != true) {
            Log.w(TAG, "[AI-$seatIndex] ONNX model not available")
            AIDecision.Error("ONNX model unavailable")
        } else {
            pipeline.decide(
                model = model,
                obsEncoder = obsEncoder,
                match = match,
                seatIndex = seatIndex,
                handCards = seat.hand,
                validActions = validActions,
                ruleSet = ruleSet,
                difficulty = difficulty,
            )
        }
        return validateDecision(rawDecision, match, ruleSet, validActions)
    }

    private fun validateDecision(
        decision: AIDecision,
        match: Match,
        ruleSet: GameRuleSet,
        validActions: List<List<Card>>,
    ): AIDecision = when {
        decision is AIDecision.Error -> decision
        isValidDecision(decision, match, ruleSet, validActions) -> {
            logDecision(decision, validActions, ruleSet, "ONNX")
            decision
        }
        else -> {
            FallbackDiagnosticLogger.logInvalidDecision(
                seatIndex = seatIndex,
                decision = decision,
                validActions = validActions,
                match = match,
                ruleSet = ruleSet,
            )
            Log.w(TAG, "[AI-$seatIndex] ONNX decision invalid")
            AIDecision.Error("ONNX decision invalid")
        }
    }

    /**
     * 与 RLCard 北方规则对齐：只有当有同类型可压时才不能 pass。
     */
    private fun canPassUnderNorthernRule(match: Match, ruleSet: GameRuleSet): Boolean =
        ValidCombinationResolver.canPassUnderNorthernRule(match, seatIndex, ruleSet)

    /**
     * 获取当前有效的出牌动作列表
     */
    override fun getValidActions(
        handCards: List<Card>,
        match: Match,
        ruleSet: GameRuleSet
    ): List<List<Card>> = ValidCombinationResolver.resolve(handCards, match, ruleSet, seatIndex)

    private fun isValidDecision(
        decision: AIDecision,
        match: Match,
        ruleSet: GameRuleSet,
        validActions: List<List<Card>>
    ): Boolean {
        return when (decision) {
            is AIDecision.PlayCards -> {
                val decisionCardIds = decision.cards.map { it.id }.toSet()
                if (decisionCardIds.isEmpty()) {
                    false
                } else {
                    val handCardIds = match.seats.getOrNull(seatIndex)?.hand?.map { it.id }?.toSet().orEmpty()
                    handCardIds.containsAll(decisionCardIds) &&
                        validActions.any { action -> action.map { it.id }.toSet() == decisionCardIds }
                }
            }

            AIDecision.Pass -> {
                when (ruleSet) {
                    GameRuleSet.SOUTHERN -> match.trickState.currentCombination != null
                    GameRuleSet.NORTHERN -> canPassUnderNorthernRule(match, ruleSet)
                }
            }

            is AIDecision.Error -> false
        }
    }

    private fun logDecision(
        decision: AIDecision,
        validActions: List<List<Card>>,
        ruleSet: GameRuleSet,
        source: String
    ) {
        when (decision) {
            is AIDecision.PlayCards -> {
                val cardDesc = decision.cards.joinToString(" ") { it.displayName }
                Log.i(
                    TAG,
                    "[AI-$seatIndex][$source] PLAY $cardDesc (${decision.cards.size} cards)"
                )
            }

            AIDecision.Pass -> {
                val forced = ruleSet == GameRuleSet.NORTHERN && validActions.isEmpty()
                Log.i(TAG, "[AI-$seatIndex][$source] PASS${if (forced) " (forced)" else ""}")
            }

            is AIDecision.Error -> {
                Log.e(TAG, "[AI-$seatIndex][$source] ERROR: ${decision.reason}")
            }
        }
    }

    fun release() {
        model?.release()
    }

    override fun close() {
        release()
    }

    fun isAvailable(): Boolean = model?.isAvailable() == true
}
