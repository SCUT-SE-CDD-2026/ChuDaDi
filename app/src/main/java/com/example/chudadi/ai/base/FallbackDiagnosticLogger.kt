package com.example.chudadi.ai.base

import android.util.Log
import com.example.chudadi.model.game.engine.ActionResult
import com.example.chudadi.model.game.engine.GameActionError
import com.example.chudadi.model.game.entity.Match
import com.example.chudadi.model.game.rule.CombinationEvaluator
import com.example.chudadi.model.game.rule.GameRuleSet
import com.example.chudadi.model.game.rule.GameRules

/**
 * AI 决策 fallback 时的诊断日志工具。
 *
 * 只在 fallback 路径上调用，正常路径零开销。
 * 输出完整的决策上下文快照，用于定位 ONNX 侧与引擎侧判定不一致的根因。
 */
object FallbackDiagnosticLogger {
    private const val TAG = "AI-FALLBACK"

    /**
     * 记录 ONNX 决策被引擎拒绝时的完整诊断信息。
     *
     * @param seatIndex 决策座位
     * @param decision ONNX 返回的原始决策
     * @param validActions ONNX 侧计算的合法动作列表
     * @param result 引擎返回的动作结果（含 error）
     * @param match 当前对局状态
     * @param ruleSet 当前规则集
     */
    @Suppress("LongParameterList")
    fun log(
        seatIndex: Int,
        decision: AIDecision?,
        validActions: List<List<com.example.chudadi.model.game.entity.Card>>,
        result: ActionResult,
        match: Match,
        ruleSet: GameRuleSet,
    ) {
        val rules = GameRules.forRuleSet(ruleSet)
        val seat = match.seats.getOrNull(seatIndex)
        val currentCombination = match.trickState.currentCombination

        val sb = StringBuilder()
        sb.appendLine("═══════════════════════════════════════════")
        sb.appendLine("Seat $seatIndex, rule=$ruleSet, round=${match.trickState.roundNumber}")
        sb.appendLine("ONNX decision: ${decisionDescription(decision)}")
        sb.appendLine(
            "Current combination: ${currentCombination?.displayName ?: "null (lead turn)"}",
        )
        sb.appendLine(
            "Hand (${seat?.hand?.size ?: "?"}): ${seat?.hand?.joinToString(" ") { it.displayName } ?: "N/A"}",
        )
        sb.appendLine("Valid actions from ONNX: ${validActions.size} play combos")
        sb.appendLine("Engine error: ${result.error}")

        // 针对特定错误类型输出额外诊断
        when (result.error) {
            GameActionError.MUST_BEAT_IF_POSSIBLE -> {
                logMandatoryResponseDetail(sb, seat?.hand, currentCombination, rules)
            }

            GameActionError.BOMB_USAGE_RESTRICTED -> {
                logBombRestrictionDetail(sb, seat?.hand, currentCombination, rules)
            }

            GameActionError.PLAY_TOO_SMALL -> {
                logPlayTooSmallDetail(sb, decision, currentCombination)
            }

            GameActionError.INVALID_PLAY_TYPE -> {
                logInvalidPlayTypeDetail(sb, decision)
            }

            else -> { /* 通用信息已足够 */ }
        }

        sb.append("═══════════════════════════════════════════")

        Log.w(TAG, sb.toString())
    }

    /**
     * 记录 ONNX validateDecision 判定 invalid 时的诊断。
     */
    fun logInvalidDecision(
        seatIndex: Int,
        decision: AIDecision,
        validActions: List<List<com.example.chudadi.model.game.entity.Card>>,
        match: Match,
        ruleSet: GameRuleSet,
    ) {
        val sb = StringBuilder()
        sb.appendLine("═══════════════════════════════════════════")
        sb.appendLine("[validateDecision] Seat $seatIndex, rule=$ruleSet")
        sb.appendLine("ONNX raw decision: ${decisionDescription(decision)}")
        sb.appendLine("Valid actions count: ${validActions.size}")

        when (decision) {
            is AIDecision.PlayCards -> {
                val cardIds = decision.cards.map { it.id }.toSet()
                val handCardIds = match.seats.getOrNull(seatIndex)?.hand?.map { it.id }?.toSet().orEmpty()
                val inHand = handCardIds.containsAll(cardIds)
                val inValidActions = validActions.any { action ->
                    action.map { it.id }.toSet() == cardIds
                }
                sb.appendLine("Decision cards: ${decision.cards.joinToString(" ") { it.displayName }}")
                sb.appendLine("All cards in hand: $inHand")
                sb.appendLine("Cards found in validActions: $inValidActions")
            }

            AIDecision.Pass -> {
                val canPass = ValidCombinationResolver.canPassUnderNorthernRule(
                    match, seatIndex, ruleSet,
                )
                sb.appendLine("canPassUnderNorthernRule: $canPass")
                sb.appendLine("currentCombination: ${match.trickState.currentCombination?.displayName ?: "null"}")
            }

            is AIDecision.Error -> {
                sb.appendLine("Error reason: ${decision.reason}")
            }
        }

        sb.append("═══════════════════════════════════════════")

        Log.w(TAG, sb.toString())
    }

    // ──────────────────────────────────────────────
    // 私有辅助方法
    // ──────────────────────────────────────────────

    private fun decisionDescription(decision: AIDecision?): String = when (decision) {
        is AIDecision.PlayCards -> "PLAY ${decision.cards.joinToString(" ") { it.displayName }} " +
            "(${decision.cards.size} cards)"
        AIDecision.Pass -> "PASS"
        is AIDecision.Error -> "ERROR: ${decision.reason}"
        null -> "null (exception or unavailable)"
    }

    private fun logMandatoryResponseDetail(
        sb: StringBuilder,
        hand: List<com.example.chudadi.model.game.entity.Card>?,
        currentCombination: com.example.chudadi.model.game.entity.PlayCombination?,
        rules: GameRules,
    ) {
        if (hand == null || currentCombination == null) return
        val evaluator = CombinationEvaluator(rules)
        val candidates = evaluator.generateAllValidCombinations(hand)
        val mandatoryCandidates = candidates.filter { candidate ->
            !rules.isBomb(candidate.type) &&
                !rules.isBomb(currentCombination.type) &&
                candidate.cardCount == currentCombination.cardCount &&
                evaluator.canBeat(candidate, currentCombination)
        }
        sb.appendLine("Engine mandatory response candidates (${mandatoryCandidates.size}):")
        if (mandatoryCandidates.isEmpty()) {
            sb.appendLine("  (none)")
        } else {
            mandatoryCandidates.take(MAX_DETAIL_ITEMS).forEach { combo ->
                sb.appendLine("  → ${combo.displayName}")
            }
            if (mandatoryCandidates.size > MAX_DETAIL_ITEMS) {
                sb.appendLine("  ... and ${mandatoryCandidates.size - MAX_DETAIL_ITEMS} more")
            }
        }
    }

    private fun logBombRestrictionDetail(
        sb: StringBuilder,
        hand: List<com.example.chudadi.model.game.entity.Card>?,
        currentCombination: com.example.chudadi.model.game.entity.PlayCombination?,
        rules: GameRules,
    ) {
        if (hand == null || currentCombination == null) return
        val evaluator = CombinationEvaluator(rules)
        val candidates = evaluator.generateAllValidCombinations(hand)
        val sameTypeBeats = candidates.filter { candidate ->
            !rules.isBomb(candidate.type) &&
                candidate.type == currentCombination.type &&
                candidate.cardCount == currentCombination.cardCount &&
                evaluator.canBeat(candidate, currentCombination)
        }
        sb.appendLine("Same-type beat options (${sameTypeBeats.size}):")
        if (sameTypeBeats.isEmpty()) {
            sb.appendLine("  (none)")
        } else {
            sameTypeBeats.take(MAX_DETAIL_ITEMS).forEach { combo ->
                sb.appendLine("  → ${combo.displayName}")
            }
        }
    }

    private fun logPlayTooSmallDetail(
        sb: StringBuilder,
        decision: AIDecision?,
        currentCombination: com.example.chudadi.model.game.entity.PlayCombination?,
    ) {
        if (decision is AIDecision.PlayCards && currentCombination != null) {
            sb.appendLine("Attempted: ${decision.cards.joinToString(" ") { it.displayName }}")
            sb.appendLine("Must beat: ${currentCombination.displayName}")
        }
    }

    private fun logInvalidPlayTypeDetail(
        sb: StringBuilder,
        decision: AIDecision?,
    ) {
        if (decision is AIDecision.PlayCards) {
            sb.appendLine("Attempted cards: ${decision.cards.joinToString(" ") { it.displayName }}")
        }
    }

    private const val MAX_DETAIL_ITEMS = 5
}
