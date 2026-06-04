package com.example.chudadi.ai

import android.content.Context
import android.util.Log
import com.example.chudadi.ai.base.AIConfig
import com.example.chudadi.ai.base.AIDifficulty
import com.example.chudadi.ai.base.AIPlayerController
import com.example.chudadi.ai.base.config.ModelConfigLoader
import com.example.chudadi.ai.base.variant.OnnxModelVariant
import com.example.chudadi.ai.onnx.OnnxAIPlayerController
import com.example.chudadi.ai.onnx.variant.V1DqnVariant
import com.example.chudadi.ai.onnx.variant.V2GruDqnVariant
import com.example.chudadi.ai.onnx.variant.V3DqnVariant
import com.example.chudadi.ai.rulebased.RuleBasedAIAdapter
import com.example.chudadi.utils.AssetCopier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

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

private const val TAG = "AIFactory"

/**
 * AI实例工厂
 *
 * 统一管理AI玩家的创建，支持ONNX AI和规则基础AI两种实现。
 * 当ONNX模型加载失败时，自动回退到规则基础AI。
 */
object AIFactory {

    private val initLock = Any()
    private var initialized = false


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
    suspend fun createAIPlayer(
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
    suspend fun createAIPlayerWithStatus(
        context: Context,
        seatIndex: Int,
        difficulty: AIDifficulty = AIDifficulty.NORMAL,
        isOnnxAI: Boolean = true,
    ): AICreationResult {
        val variant = if (isOnnxAI) AIConfig.getDefaultVariant() else null
        return createAIPlayerWithStatus(context, seatIndex, difficulty, variant)
    }

    suspend fun createAIPlayerWithStatus(
        context: Context,
        seatIndex: Int,
        difficulty: AIDifficulty = AIDifficulty.NORMAL,
        variantName: String,
    ): AICreationResult {
        val variant = AIConfig.getVariant(variantName)
        if (variant == null) {
            val errorMsg = "ONNX模型变体不存在：$variantName，已降级到规则型AI"
            Log.w(TAG, "[$seatIndex] $errorMsg")
            return AICreationResult(
                controller = createRuleBasedAIPlayer(seatIndex, difficulty),
                isFallback = true,
                errorMessage = errorMsg,
            )
        }
        return createAIPlayerWithStatus(context, seatIndex, difficulty, variant)
    }

    /**
     * 创建AI玩家控制器（带变体和降级状态）。
     *
     * @param context 应用上下文
     * @param seatIndex AI玩家座位索引
     * @param difficulty AI难度级别
     * @param variant ONNX 模型变体；null 表示使用规则型 AI
     * @return AICreationResult 包含控制器和降级状态
     */
    suspend fun createAIPlayerWithStatus(
        context: Context,
        seatIndex: Int,
        difficulty: AIDifficulty = AIDifficulty.NORMAL,
        variant: OnnxModelVariant? = null,
    ): AICreationResult {
        Log.i(TAG, "Creating AI player for seat $seatIndex, difficulty=$difficulty, variant=${variant?.name}")

        return if (variant != null) {
            createOnnxAIPlayerWithStatus(context, seatIndex, difficulty, variant)
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
     * 在 IO 线程执行模型加载，避免阻塞主线程
     */
    private suspend fun createOnnxAIPlayerWithStatus(
        context: Context,
        seatIndex: Int,
        difficulty: AIDifficulty,
        variant: OnnxModelVariant,
    ): AICreationResult = withContext(Dispatchers.IO) {
        Log.i(TAG, "Creating ONNX AI player for seat $seatIndex with variant=${variant.name}")

        // 获取模型路径（preloadModels 已负责复制）
        val modelName = variant.modelFileName
        val modelPath = AssetCopier.getModelPath(context, modelName)
        Log.i(TAG, "Model path: $modelPath")

        // 检查模型文件是否真的存在
        val modelFileExists = modelPath?.let { java.io.File(it).exists() } ?: false

        if (!modelFileExists) {
            // 模型文件不存在，降级到规则型AI
            val errorMsg = "模型文件不存在，已降级到规则型AI"
            Log.w(TAG, "[$seatIndex] $errorMsg")
            return@withContext AICreationResult(
                controller = createRuleBasedAIPlayer(seatIndex, difficulty),
                isFallback = true,
                errorMessage = errorMsg,
            )
        }

        // 创建ONNX AI（在 IO 线程执行，避免阻塞主线程）
        val controller = OnnxAIPlayerController(
            seatIndex = seatIndex,
            difficulty = difficulty,
            modelPath = modelPath ?: "",
            variant = variant,
        )

        // 检查ONNX AI是否成功加载（OnnxAIPlayerController内部也有降级逻辑）
        val isOnnxLoaded = controller.isAvailable()
        if (!isOnnxLoaded) {
            val errorMsg = "ONNX模型加载失败，已降级到规则型AI"
            Log.w(TAG, "[$seatIndex] $errorMsg")
            return@withContext AICreationResult(
                controller = createRuleBasedAIPlayer(seatIndex, difficulty),
                isFallback = true,
                errorMessage = errorMsg,
            )
        }

        Log.i(TAG, "[$seatIndex] ONNX AI created successfully")
        AICreationResult(
            controller = controller,
            isFallback = false,
            errorMessage = null,
        )
    }

    /**
     * 创建规则型AI玩家
     */

    /**
     * 批量创建AI玩家（用于4人对战中的3个AI）
     *
     * @param context 应用上下文
     * @param onnxSeatIndices 使用ONNX AI的座位索引列表（例如 [2, 3] 表示座位2和3使用ONNX AI）
     * @param difficulties 每个AI的难度配置列表，长度应为3（座位1、2、3）
     * @return AI玩家控制器列表
     */
    suspend fun createAIPlayersForMatch(
        context: Context,
        onnxSeatIndices: Set<Int> = emptySet(),
        difficulties: List<AIDifficulty> = listOf(
            AIDifficulty.NORMAL,
            AIDifficulty.NORMAL,
            AIDifficulty.NORMAL,
        ),
    ): List<AIPlayerController> {
        return createAIPlayersForMatchWithStatus(
            context = context,
            onnxSeatIndices = onnxSeatIndices,
            difficulties = difficulties,
        ).map { it.controller }
    }

    /**
     * 批量创建AI玩家（带降级状态）
     *
     * @param context 应用上下文
     * @param onnxSeatIndices 使用ONNX AI的座位索引列表
     * @param difficulties 每个AI的难度配置列表，长度应为3
     * @return AICreationResult列表，包含控制器和降级状态
     */
    suspend fun createAIPlayersForMatchWithStatus(
        context: Context,
        seatIndices: List<Int> = listOf(1, 2, 3),
        onnxSeatIndices: Set<Int> = emptySet(),
        difficulties: List<AIDifficulty> = listOf(
            AIDifficulty.NORMAL,
            AIDifficulty.NORMAL,
            AIDifficulty.NORMAL,
        ),
    ): List<AICreationResult> {
        require(difficulties.size == seatIndices.size) {
            "Expected difficulties.size (${difficulties.size}) to match seatIndices.size (${seatIndices.size})"
        }

        return coroutineScope {
            seatIndices.mapIndexed { index, seatIndex ->
                async {
                    val diff = difficulties[index]
                    val isOnnxAI = seatIndex in onnxSeatIndices
                    createAIPlayerWithStatus(context, seatIndex = seatIndex, difficulty = diff, isOnnxAI = isOnnxAI)
                }
            }.awaitAll()
        }
    }
    /**
     * 预加载模型文件。
     *
     * 从 `assets/models/model_config.json` 读取配置并初始化变体注册表，
     * 然后只复制配置中引用的 ONNX 文件到私有目录，避免调试模型被一并加载。
     */
    fun preloadModels(context: Context) {
        synchronized(initLock) {
            if (initialized) {
                Log.d(TAG, "preloadModels already done, skipping")
                return
            }
            val config = ModelConfigLoader.load(context)
            val modelNames = config?.variants?.map { it.modelFileName }?.toSet()
            if (config != null) {
                AIConfig.initialize(config)
            } else {
                Log.w(TAG, "Model config not found, falling back to hardcoded defaults")
                registerDefaultVariant()
            }
            @Suppress("TooGenericExceptionCaught")
            try {
                AssetCopier.copyModelsToPrivateDir(context, modelNames)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to copy model assets to private dir", e)
            }
            initialized = true
        }
    }

    suspend fun preloadOnnxVariant(context: Context, variantName: String): Boolean = withContext(Dispatchers.IO) {
        preloadModels(context)
        val variant = AIConfig.getVariant(variantName)
        if (variant == null) {
            Log.w(TAG, "Cannot preload ONNX variant; unknown variant=$variantName")
            return@withContext false
        }
        val modelPath = AssetCopier.getModelPath(context, variant.modelFileName)
        val modelFileExists = modelPath?.let { java.io.File(it).exists() } ?: false
        if (!modelFileExists) {
            Log.w(TAG, "Cannot preload ONNX variant=${variant.name}; model file missing: $modelPath")
            return@withContext false
        }
        OnnxAIPlayerController.preloadModel(modelPath = modelPath.orEmpty(), variant = variant)
    }

    suspend fun preloadOnnxAiType(context: Context, aiTypeName: String): Boolean {
        val variantName = when (aiTypeName) {
            "ONNX_RL" -> V1DqnVariant.COMPANION_NAME
            "ONNX_RL_V2" -> V2GruDqnVariant.COMPANION_NAME
            "ONNX_RL_V3" -> V3DqnVariant.COMPANION_NAME
            else -> return true
        }
        return preloadOnnxVariant(context, variantName)
    }


    /**
     * 注册默认 V1 DQN 变体（配置文件不可用时的降级路径）。
     */
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
 * 注册默认 V1 DQN 变体（配置文件不可用时的降级路径）。
 */
private fun registerDefaultVariant() {
    if (AIConfig.getVariant(V1DqnVariant.COMPANION_NAME) == null) {
        AIConfig.register(V1DqnVariant.createDefault())
    }
}
