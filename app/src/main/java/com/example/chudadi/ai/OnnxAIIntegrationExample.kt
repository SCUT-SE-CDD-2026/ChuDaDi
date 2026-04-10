package com.example.chudadi.ai

import android.content.Context
import android.util.Log
import com.example.chudadi.ai.base.AIDecision
import com.example.chudadi.ai.base.AIDifficulty
import com.example.chudadi.ai.onnx.OnnxAIPlayerController
import com.example.chudadi.model.game.entity.Match
import com.example.chudadi.model.game.rule.GameRuleSet

/**
 * ONNX AI集成示例
 *
 * 展示如何在游戏中使用ONNX AI玩家。
 */
object OnnxAIIntegrationExample {

    private const val TAG = "OnnxAIIntegration"

    /**
     * 创建ONNX AI玩家示例
     *
     * @param context 应用上下文
     * @param seatIndex AI座位索引
     * @param difficulty AI难度
     * @return OnnxAIPlayerController实例
     */
    fun createOnnxAIPlayer(
        context: Context,
        seatIndex: Int,
        difficulty: AIDifficulty = AIDifficulty.NORMAL,
    ): OnnxAIPlayerController? {
        // 确保模型文件已复制
        AIFactory.preloadModels(context)

        val modelPath = com.example.chudadi.utils.AssetCopier.getModelPath(context, "test.onnx")
            ?: run {
                Log.e(TAG, "Model file not found")
                return null
            }

        return try {
            OnnxAIPlayerController(
                seatIndex = seatIndex,
                difficulty = difficulty,
                modelPath = modelPath,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create ONNX AI player", e)
            null
        }
    }

    /**
     * 请求AI决策示例
     */
    suspend fun requestAIDecision(
        aiPlayer: OnnxAIPlayerController,
        match: Match,
        ruleSet: GameRuleSet,
    ): AIDecision {
        return aiPlayer.requestDecision(match, ruleSet)
    }

    /**
     * 批量创建AI玩家（用于4人对战中的3个AI）
     */
    fun createAIPlayersForMatch(
        context: Context,
        difficulties: List<AIDifficulty> = listOf(
            AIDifficulty.NORMAL,
            AIDifficulty.NORMAL,
            AIDifficulty.NORMAL,
        ),
    ): List<OnnxAIPlayerController> {
        return difficulties.mapIndexed { index, difficulty ->
            createOnnxAIPlayer(context, seatIndex = index + 1, difficulty = difficulty)
        }.filterNotNull()
    }
}
