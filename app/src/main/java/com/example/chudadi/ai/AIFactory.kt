package com.example.chudadi.ai

import android.content.Context
import android.util.Log
import com.example.chudadi.ai.base.AIDifficulty
import com.example.chudadi.ai.base.AIPlayerController
import com.example.chudadi.ai.onnx.OnnxAIPlayerController
import com.example.chudadi.ai.rulebased.RuleBasedAIAdapter
import com.example.chudadi.utils.AssetCopier

/**
 * AI创建结果
 *
 * @param controller AI控制器实例
 * @param isFallback 是否为降级创建（ONNX模型加载失败时降级到规则型AI）
 * @param errorMessage 错误信息（如果有）
 */
data class AICreationResult(
    val controller: AIPlayerController,
    val isFallback: Boolean = false,
    val errorMessage: String? = null,
)

/**
 * AI实例工厂
 *
 * 统一管理AI玩家的创建，支持ONNX AI和规则基础AI两种实现。
 * 当ONNX模型加载失败时，自动回退到规则基础AI。
 */
object AIFactory {
    private const val TAG = "AIFactory"
    private const val MODEL_NAME = "test.onnx"

    /**
     * 创建AI玩家控制器（兼容旧接口，不暴露降级状态）
     *
     * 根据AI类型创建对应的AI控制器：
     * - 规则型AI：直接创建RuleBasedAiPlayer
     * - ONNX AI：创建OnnxAIPlayerController，如果模型不可用会自动降级到规则基础AI
     *
     * @param context 应用上下文
     * @param seatIndex AI玩家座位索引
     * @param difficulty AI难度级别
     * @param isOnnxAI 是否为ONNX AI（true=ONNX AI, false=规则型AI）
     * @return AI玩家控制器实例
     */
    fun createAIPlayer(
        context: Context,
        seatIndex: Int,
        difficulty: AIDifficulty = AIDifficulty.NORMAL,
        isOnnxAI: Boolean = true,
    ): AIPlayerController {
        return createAIPlayerWithStatus(context, seatIndex, difficulty, isOnnxAI).controller
    }

    /**
     * 创建AI玩家控制器（带降级状态）
     *
     * 根据AI类型创建对应的AI控制器：
     * - 规则型AI：直接创建RuleBasedAiPlayer
     * - ONNX AI：创建OnnxAIPlayerController，如果模型不可用会自动降级到规则基础AI
     *
     * @param context 应用上下文
     * @param seatIndex AI玩家座位索引
     * @param difficulty AI难度级别
     * @param isOnnxAI 是否为ONNX AI（true=ONNX AI, false=规则型AI）
     * @return AICreationResult 包含控制器和降级状态
     */
    fun createAIPlayerWithStatus(
        context: Context,
        seatIndex: Int,
        difficulty: AIDifficulty = AIDifficulty.NORMAL,
        isOnnxAI: Boolean = true,
    ): AICreationResult {
        Log.i(TAG, "Creating AI player for seat $seatIndex, difficulty=$difficulty, isOnnxAI=$isOnnxAI")

        return if (isOnnxAI) {
            createOnnxAIPlayerWithStatus(context, seatIndex, difficulty)
        } else {
            AICreationResult(
                controller = createRuleBasedAIPlayer(seatIndex, difficulty),
                isFallback = false,
                errorMessage = null,
            )
        }
    }

    /**
     * 创建ONNX AI玩家（带降级状态）
     */
    private fun createOnnxAIPlayerWithStatus(
        context: Context,
        seatIndex: Int,
        difficulty: AIDifficulty,
    ): AICreationResult {
        Log.i(TAG, "Creating ONNX AI player for seat $seatIndex")

        // 检查模型是否可用
        val isModelAvailable = AssetCopier.isModelAvailable(context, MODEL_NAME)
        Log.d(TAG, "Model available in private dir: $isModelAvailable")

        // 尝试复制模型文件到私有目录（如果存在）
        if (!isModelAvailable) {
            Log.i(TAG, "Model not found in private dir, attempting to copy from assets...")
            val copySuccess = AssetCopier.copyModelsToPrivateDir(context)
            Log.i(TAG, "Model copy result: $copySuccess")
        }

        // 获取模型路径
        val modelPath = AssetCopier.getModelPath(context, MODEL_NAME)
        Log.i(TAG, "Model path: $modelPath")

        // 检查模型文件是否真的存在
        val modelFileExists = modelPath?.let { java.io.File(it).exists() } ?: false

        return if (!modelFileExists) {
            // 模型文件不存在，降级到规则型AI
            val errorMsg = "模型文件不存在，已降级到规则型AI"
            Log.w(TAG, "[$seatIndex] $errorMsg")
            AICreationResult(
                controller = createRuleBasedAIPlayer(seatIndex, difficulty),
                isFallback = true,
                errorMessage = errorMsg,
            )
        } else {
            // 创建ONNX AI
            val controller = OnnxAIPlayerController(
                seatIndex = seatIndex,
                difficulty = difficulty,
                modelPath = modelPath ?: "",
            )

            // 检查ONNX AI是否成功加载（OnnxAIPlayerController内部也有降级逻辑）
            val isOnnxLoaded = controller.isAvailable()
            if (!isOnnxLoaded) {
                val errorMsg = "ONNX模型加载失败，已降级到规则型AI"
                Log.w(TAG, "[$seatIndex] $errorMsg")
                AICreationResult(
                    controller = controller,
                    isFallback = true,
                    errorMessage = errorMsg,
                )
            } else {
                Log.i(TAG, "[$seatIndex] ONNX AI created successfully")
                AICreationResult(
                    controller = controller,
                    isFallback = false,
                    errorMessage = null,
                )
            }
        }
    }

    /**
     * 创建规则型AI玩家
     */
    private fun createRuleBasedAIPlayer(
        seatIndex: Int,
        difficulty: AIDifficulty = AIDifficulty.NORMAL,
    ): AIPlayerController {
        Log.i(TAG, "Creating rule-based AI player for seat $seatIndex with difficulty=$difficulty")
        return RuleBasedAIAdapter(
            seatIndex = seatIndex,
            difficulty = difficulty,
        )
    }

    /**
     * 批量创建AI玩家（用于4人对战中的3个AI）
     *
     * @param context 应用上下文
     * @param onnxSeatIndices 使用ONNX AI的座位索引列表（例如 [2, 3] 表示座位2和3使用ONNX AI）
     * @param difficulties 每个AI的难度配置列表，长度应为3（座位1、2、3）
     * @return AI玩家控制器列表
     */
    fun createAIPlayersForMatch(
        context: Context,
        onnxSeatIndices: Set<Int> = emptySet(),
        difficulties: List<AIDifficulty> = listOf(
            AIDifficulty.NORMAL,
            AIDifficulty.NORMAL,
            AIDifficulty.NORMAL,
        ),
    ): List<AIPlayerController> {
        return createAIPlayersForMatchWithStatus(context, onnxSeatIndices, difficulties)
            .map { it.controller }
    }

    /**
     * 批量创建AI玩家（带降级状态）
     *
     * @param context 应用上下文
     * @param onnxSeatIndices 使用ONNX AI的座位索引列表
     * @param difficulties 每个AI的难度配置列表，长度应为3
     * @return AICreationResult列表，包含控制器和降级状态
     */
    fun createAIPlayersForMatchWithStatus(
        context: Context,
        onnxSeatIndices: Set<Int> = emptySet(),
        difficulties: List<AIDifficulty> = listOf(
            AIDifficulty.NORMAL,
            AIDifficulty.NORMAL,
            AIDifficulty.NORMAL,
        ),
    ): List<AICreationResult> {
        require(difficulties.size == 3) { "Expected exactly 3 difficulty settings for 3 AI players" }

        return difficulties.mapIndexed { index, difficulty ->
            val seatIndex = index + 1
            val isOnnxAI = seatIndex in onnxSeatIndices
            createAIPlayerWithStatus(context, seatIndex = seatIndex, difficulty = difficulty, isOnnxAI = isOnnxAI)
        }
    }

    /**
     * 预加载模型文件
     *
     * 在应用启动时调用，确保模型文件已复制到私有目录。
     *
     * @param context 应用上下文
     */
    fun preloadModels(context: Context) {
        if (!AssetCopier.isModelAvailable(context, MODEL_NAME)) {
            AssetCopier.copyModelsToPrivateDir(context)
        }
    }

    /**
     * 检查模型是否可用
     *
     * @param context 应用上下文
     * @return 模型是否可用
     */
    fun isModelAvailable(context: Context): Boolean {
        return AssetCopier.isModelAvailable(context, MODEL_NAME)
    }
}
