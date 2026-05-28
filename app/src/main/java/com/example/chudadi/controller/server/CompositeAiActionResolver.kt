package com.example.chudadi.controller.server

import com.example.chudadi.ai.base.AIDecision
import com.example.chudadi.ai.base.AIPlayerController
import com.example.chudadi.model.game.engine.ActionResult
import com.example.chudadi.model.game.engine.GameEngine
import com.example.chudadi.model.game.entity.Match
import com.example.chudadi.model.game.rule.GameRuleSet
import com.example.chudadi.network.protocol.PassCommand
import com.example.chudadi.network.protocol.PlayCardCommand
import kotlinx.coroutines.CancellationException

/**
 * 按座位 ID 分发 AI 决策到不同的 resolver。
 *
 * 对于没有专用 resolver 的座位（人类超时代出、断连托管等），
 * 自动降级到 [RuleBasedFallbackResolver]。
 */
internal class CompositeAiActionResolver(
    private val resolvers: Map<Int, AiActionResolver>,
) : AiActionResolver {
    override suspend fun resolve(
        match: Match,
        seatId: Int,
        engine: GameEngine,
        ruleSet: GameRuleSet,
    ): ActionResult {
        val resolver = resolvers[seatId] ?: RuleBasedFallbackResolver
        return resolver.resolve(match, seatId, engine, ruleSet)
    }
}

/**
 * 将 [AIPlayerController]（本地模式接口）适配为 [AiActionResolver]（蓝牙模式接口）。
 *
 * ONNX 推理失败时降级到 [fallback]。
 */
internal class AiPlayerControllerAdapter(
    private val controller: AIPlayerController,
    private val fallback: AiActionResolver = RuleBasedFallbackResolver,
) : AiActionResolver {

    @Suppress("RethrowCaughtException", "TooGenericExceptionCaught")
    override suspend fun resolve(
        match: Match,
        seatId: Int,
        engine: GameEngine,
        ruleSet: GameRuleSet,
    ): ActionResult {
        val decision = try {
            controller.requestDecision(match, ruleSet)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w(TAG, "AI decision failed for seat $seatId, falling back", e)
            return fallback.resolve(match, seatId, engine, ruleSet)
        }
        return when (decision) {
            is AIDecision.PlayCards ->
                PlayCardCommand(decision.cards.map { it.id }.toSet())
                    .execute(match, seatId, engine)
            AIDecision.Pass ->
                PassCommand.execute(match, seatId, engine)
            is AIDecision.Error ->
                fallback.resolve(match, seatId, engine, ruleSet)
        }
    }

    companion object {
        private const val TAG = "AiPlayerCtrlAdapter"
    }
}

/**
 * 无状态的 RuleBased 降级 resolver。
 */
internal object RuleBasedFallbackResolver : AiActionResolver {
    private val fallbackPlayer = com.example.chudadi.ai.rulebased.RuleBasedAiPlayer()

    override suspend fun resolve(
        match: Match,
        seatId: Int,
        engine: GameEngine,
        @Suppress("UNUSED_PARAMETER") ruleSet: GameRuleSet,
    ): ActionResult {
        return when (val decision = fallbackPlayer.decideAction(match, seatId)) {
            is AIDecision.PlayCards ->
                PlayCardCommand(decision.cards.map { it.id }.toSet())
                    .execute(match, seatId, engine)
            AIDecision.Pass ->
                PassCommand.execute(match, seatId, engine)
            is AIDecision.Error -> {
                android.util.Log.e("RuleBasedFallback", "Rule-based AI error for seat $seatId: ${decision.reason}")
                PassCommand.execute(match, seatId, engine)
            }
        }
    }
}
