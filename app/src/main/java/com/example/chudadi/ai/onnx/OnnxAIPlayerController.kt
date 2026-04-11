package com.example.chudadi.ai.onnx

import android.util.Log
import com.example.chudadi.ai.base.AIDecision
import com.example.chudadi.ai.base.AIDifficulty
import com.example.chudadi.ai.base.AIPlayerController
import com.example.chudadi.ai.rulebased.RuleBasedAiPlayer
import com.example.chudadi.model.game.entity.Card
import com.example.chudadi.ai.rulebased.AiDecision as RuleBasedAiDecision
import java.io.File
import com.example.chudadi.model.game.entity.Match
import com.example.chudadi.model.game.entity.PlayCombination
import com.example.chudadi.model.game.rule.CombinationComparator
import com.example.chudadi.model.game.rule.CombinationEvaluator
import com.example.chudadi.model.game.rule.CombinationGenerator
import com.example.chudadi.model.game.rule.CombinationParser
import com.example.chudadi.model.game.rule.CombinationType
import com.example.chudadi.model.game.rule.GameRuleSet
import com.example.chudadi.model.game.rule.GameRules
import kotlinx.coroutines.CancellationException

/**
 * ONNX AI玩家控制器
 *
 * 实现AIPlayerController接口，将ONNX模型包装成游戏可用的AI玩家。
 * 采用适配器模式，使ONNX AI与规则基础AI对游戏引擎而言是多态的。
 *
 * @property seatIndex AI玩家座位索引
 * @property difficulty AI难度级别
 * @property modelPath ONNX模型文件路径
 */
class OnnxAIPlayerController(
    override val seatIndex: Int,
    override val difficulty: AIDifficulty,
    private val modelPath: String,
) : AIPlayerController, AutoCloseable {

    companion object {
        private const val TAG = "OnnxAIPlayerController"
    }

    internal data class ActionCandidate(
        val actionId: Long,
        val feature: FloatArray,
    )

    private val model: OnnxModel? = if (modelPath.isNotBlank() && File(modelPath).exists()) {
        Log.i(TAG, "[AI-$seatIndex] Loading ONNX model from: $modelPath")
        try {
            val m = OnnxModel(modelPath)
            Log.i(TAG, "[AI-$seatIndex] ONNX model loaded successfully, available=${m.isAvailable()}")
            m
        } catch (e: Exception) {
            Log.e(TAG, "[AI-$seatIndex] Failed to load ONNX model, will use fallback", e)
            null
        }
    } else {
        Log.w(
            TAG,
            "[AI-$seatIndex] Model path empty or file not found (path='$modelPath'), will use fallback rule-based AI",
        )
        null
    }
    private val decoder: ActionDecoder = ActionDecoder()
    private val actionFeatureEncoder: ActionFeatureEncoder = ActionFeatureEncoder()

    // 降级用的规则基础AI
    private val fallbackAI: RuleBasedAiPlayer = RuleBasedAiPlayer()

    init {
        if (model?.isAvailable() != true) {
            Log.w(TAG, "ONNX model not available, will use fallback rule-based AI")
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

        val handSize = seat.hand.size
        val isFirstPlay = match.trickState.currentCombination == null

        Log.i(
            TAG, "[AI-$seatIndex] Requesting decision: hand=$handSize cards, " +
                "rule=$ruleSet, difficulty=$difficulty, firstPlay=$isFirstPlay"
        )

        // 获取有效动作列表（用于验证和降级）
        val validActions = getValidActions(seat.hand, match, ruleSet)
        Log.d(TAG, "[AI-$seatIndex] Valid actions count: ${validActions.size}")
        val actionCandidates = buildActionCandidates(
            handCards = seat.hand,
            validActions = validActions,
            match = match,
            ruleSet = ruleSet,
        )
        val validActionMask = actionCandidates.map { it.actionId }.toLongArray()

        if (model?.isAvailable() == true) {
            try {
                val startTime = System.currentTimeMillis()
                val prediction = model.predictActionValues(
                    match = match,
                    seatIndex = seatIndex,
                    actionFeatures = actionCandidates.map { it.feature },
                )
                val inferenceTime = System.currentTimeMillis() - startTime

                if (prediction != null) {
                    Log.d(TAG, "[AI-$seatIndex] ONNX inference completed in ${inferenceTime}ms")

                    val decision = decoder.decode(
                        modelOutput = prediction,
                        handCards = seat.hand,
                        validActionMask = validActionMask,
                        difficulty = difficulty,
                    )

                    // 验证决策合法性
                    if (isValidDecision(decision, match, ruleSet, validActions)) {
                        logDecision(decision, validActions, ruleSet, "ONNX")
                        return decision
                    } else {
                        Log.w(TAG, "[AI-$seatIndex] ONNX decision invalid, using fallback")
                    }
                }
            } catch (e: OnnxTimeoutException) {
                Log.e(TAG, "[AI-$seatIndex] ONNX inference timeout, using fallback", e)
            } catch (e: OnnxInferenceException) {
                Log.e(TAG, "[AI-$seatIndex] ONNX inference failed, using fallback", e)
            }
        } else {
            Log.w(TAG, "[AI-$seatIndex] ONNX model not available, using fallback")
        }

        // 降级到规则基础AI
        return fallbackToRuleBasedAI(match, validActions)
    }

    internal fun buildActionCandidates(
        handCards: List<Card>,
        validActions: List<List<Card>>,
        match: Match,
        ruleSet: GameRuleSet,
    ): List<ActionCandidate> {
        val candidates = mutableListOf<ActionCandidate>()
        val seenActionIds = mutableSetOf<Long>()
        val rules = GameRules.forRuleSet(ruleSet)
        val parser = CombinationParser(rules)

        for (cards in validActions) {
            val actionId = actionIdFromCards(cards)
            if (!seenActionIds.add(actionId)) {
                continue
            }
            val parsedType = parser.parse(cards)?.type
            val feature = actionFeatureEncoder.encodeActionFeature(
                handCards = handCards,
                actionCards = cards,
                actionType = parsedType,
            )
            candidates += ActionCandidate(actionId = actionId, feature = feature)
        }

        if (isPassLegal(match, ruleSet, validActions)) {
            val passId = 0L
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

    private fun isPassLegal(
        match: Match,
        ruleSet: GameRuleSet,
        validActions: List<List<Card>>,
    ): Boolean {
        return when (ruleSet) {
            GameRuleSet.SOUTHERN -> match.trickState.currentCombination != null
            GameRuleSet.NORTHERN -> validActions.isEmpty()
        }
    }

    private fun actionIdFromCards(cards: List<Card>): Long {
        var mask = 0L
        for (card in cards) {
            val index = GameStateEncoder.cardToIndex(card)
            if (index in 0 until Long.SIZE_BITS) {
                mask = mask or (1L shl index)
            }
        }
        return mask
    }

    /**
     * 获取当前有效的出牌动作列表
     */
    override fun getValidActions(
        handCards: List<Card>,
        match: Match,
        ruleSet: GameRuleSet
    ): List<List<Card>> {
        val rules = GameRules.forRuleSet(ruleSet)
        val parser = CombinationParser(rules)
        val comparator = CombinationComparator(rules)
        val generator = CombinationGenerator(parser, comparator)

        val currentCombination = match.trickState.currentCombination

        return if (currentCombination == null) {
            // 本轮首出
            generateAllValidCombinations(handCards, generator)
        } else {
            // 需要压牌
            generateValidResponses(handCards, currentCombination, generator, comparator)
        }
    }

    private fun generateAllValidCombinations(
        handCards: List<Card>,
        generator: CombinationGenerator
    ): List<List<Card>> {
        return try {
            val combinations = generator.generateAllValidCombinations(handCards)
            combinations.map { it.cards }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to generate combinations", e)
            emptyList()
        }
    }

    private fun generateValidResponses(
        handCards: List<Card>,
        currentCombination: PlayCombination,
        generator: CombinationGenerator,
        comparator: CombinationComparator
    ): List<List<Card>> {
        return try {
            val allCombinations = generator.generateAllValidCombinations(handCards)
            allCombinations
                .filter { comparator.canBeat(it, currentCombination) }
                .map { it.cards }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to generate responses", e)
            emptyList()
        }
    }

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
                    GameRuleSet.NORTHERN -> validActions.isEmpty()
                }
            }

            is AIDecision.Error -> false
        }
    }

    private fun fallbackToRuleBasedAI(
        match: Match,
        validActions: List<List<Card>>
    ): AIDecision {
        Log.i(TAG, "[AI-$seatIndex] Using fallback rule-based AI")

        return try {
            val ruleDecision = fallbackAI.decideAction(match, seatIndex)

            val decision = when (ruleDecision) {
                is RuleBasedAiDecision.Play -> {
                    val cards = ruleDecision.cardIds.mapNotNull { cardId ->
                        findCardById(match, seatIndex, cardId)
                    }
                    if (cards.isNotEmpty()) {
                        AIDecision.PlayCards(cards)
                    } else {
                        AIDecision.Pass
                    }
                }

                RuleBasedAiDecision.Pass -> AIDecision.Pass
            }

            logDecision(decision, validActions, match.ruleSet, "FALLBACK")
            decision

        } catch (e: Exception) {
            Log.e(TAG, "[AI-$seatIndex] Fallback AI also failed", e)
            AIDecision.Error("Both ONNX and fallback AI failed: ${e.message}", e)
        }
    }

    private fun findCardById(match: Match, seatIndex: Int, cardId: String): Card? {
        val seat = match.seats.getOrNull(seatIndex) ?: return null
        return seat.hand.find { it.id == cardId }
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
