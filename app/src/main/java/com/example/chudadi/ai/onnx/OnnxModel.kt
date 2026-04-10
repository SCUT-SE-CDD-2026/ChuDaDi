package com.example.chudadi.ai.onnx

import android.util.Log
import com.example.chudadi.model.game.entity.Match
import com.example.chudadi.model.game.rule.GameRuleSet
import kotlinx.coroutines.CancellationException

/**
 * ONNX模型管理器
 *
 * 封装ONNX推理引擎，提供高级别的游戏状态推理接口。
 * 管理模型生命周期，提供错误处理和降级机制。
 */
class OnnxModel(modelPath: String) {

    companion object {
        private const val TAG = "OnnxModel"
    }

    private val encoder = GameStateEncoder()
    private var inferenceEngine: OnnxInferenceEngine? = null
    private var isLoaded = false

    init {
        try {
            inferenceEngine = OnnxInferenceEngine(modelPath)
            isLoaded = inferenceEngine?.isAvailable() == true

            if (isLoaded) {
                Log.i(TAG, "ONNX Model initialized successfully")
                Log.i(TAG, "Input dim: ${inferenceEngine?.getInputDim()}")
                Log.i(TAG, "Output dim: ${inferenceEngine?.getOutputDim()}")
            } else {
                Log.e(TAG, "ONNX Model failed to initialize")
            }
        } catch (e: OnnxInitializationException) {
            Log.e(TAG, "Failed to initialize ONNX model", e)
            isLoaded = false
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error initializing ONNX model", e)
            isLoaded = false
        }
    }

    /**
     * 执行推理
     *
     * @param match 当前游戏状态
     * @param seatIndex AI玩家座位索引
     * @return 模型输出的FloatArray，如果推理失败则返回null
     */
    suspend fun predict(match: Match, seatIndex: Int): FloatArray? {
        if (!isLoaded || inferenceEngine == null) {
            Log.w(TAG, "Model not loaded, cannot predict")
            return null
        }

        return try {
            // 编码游戏状态
            val obsTensor = encoder.encode(match, seatIndex)

            // 执行推理，传入obs和可选的actions
            val output = inferenceEngine?.infer(
                obsTensor = obsTensor,
                actionsTensor = null
            )

            Log.d(TAG, "Prediction completed for seat $seatIndex")
            output
        } catch (e: OnnxInferenceException) {
            Log.e(TAG, "Inference failed", e)
            null
        } catch (e: OnnxTimeoutException) {
            Log.e(TAG, "Inference timeout", e)
            null
        } catch (e: CancellationException) {
            throw e
        }
    }

    /**
     * 批量推理（用于性能测试或批量评估）
     *
     * @param matches 游戏状态列表
     * @param seatIndex AI玩家座位索引
     * @return 推理结果列表
     */
    suspend fun predictBatch(matches: List<Match>, seatIndex: Int): List<FloatArray?> {
        return matches.map { match ->
            predict(match, seatIndex)
        }
    }

    /**
     * 检查模型是否已加载并可用
     */
    fun isAvailable(): Boolean = isLoaded && inferenceEngine?.isAvailable() == true

    /**
     * 获取输入维度
     */
    fun getInputDim(): Int = inferenceEngine?.getInputDim() ?: encoder.getInputDim()

    /**
     * 获取输出维度
     */
    fun getOutputDim(): Int = inferenceEngine?.getOutputDim() ?: 0

    /**
     * 释放模型资源
     */
    fun release() {
        try {
            val engine = inferenceEngine
            inferenceEngine = null
            isLoaded = false
            engine?.close()
            Log.i(TAG, "Model released")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing model", e)
        }
    }

    /**
     * 获取模型状态信息
     */
    fun getStatus(): ModelStatus {
        return ModelStatus(
            isLoaded = isLoaded,
            isAvailable = inferenceEngine?.isAvailable() == true,
            inputDim = getInputDim(),
            outputDim = getOutputDim(),
        )
    }

    data class ModelStatus(
        val isLoaded: Boolean,
        val isAvailable: Boolean,
        val inputDim: Int,
        val outputDim: Int,
    )
}
