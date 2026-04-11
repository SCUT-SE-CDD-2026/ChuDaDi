package com.example.chudadi.ai.onnx

import android.util.Log
import com.example.chudadi.model.game.entity.Match
import kotlinx.coroutines.CancellationException

/**
 * ONNX model wrapper for match-state inference.
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
                Log.i(TAG, "I/O contract: ${inferenceEngine?.ioContract}")
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
            val obsTensor = encoder.encode(match, seatIndex)
            val output = inferenceEngine?.infer(
                obsTensor = obsTensor,
                actionsTensor = null,
                batchSize = 1,
                obsDim = obsTensor.size,
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
     * Batch predict Q-values for legal actions.
     * Output order aligns with [actionFeatures].
     */
    suspend fun predictActionValues(
        match: Match,
        seatIndex: Int,
        actionFeatures: List<FloatArray>,
    ): FloatArray? {
        if (!isLoaded || inferenceEngine == null || actionFeatures.isEmpty()) {
            return null
        }

        return try {
            val obs = encoder.encode(match, seatIndex)
            val batchedInput = buildBatchedActionInput(obs = obs, actionFeatures = actionFeatures)
            val rawValues = inferenceEngine?.infer(
                obsTensor = batchedInput.obsBatch,
                actionsTensor = batchedInput.actionsBatch,
                batchSize = batchedInput.batchSize,
                obsDim = batchedInput.obsDim,
            )
            rawValues?.let { alignActionValues(it, batchedInput.batchSize) }
        } catch (e: OnnxInferenceException) {
            Log.e(TAG, "Batch inference failed", e)
            null
        } catch (e: OnnxTimeoutException) {
            Log.e(TAG, "Batch inference timeout", e)
            null
        } catch (e: CancellationException) {
            throw e
        }
    }

    suspend fun predictBatch(matches: List<Match>, seatIndex: Int): List<FloatArray?> {
        return matches.map { match ->
            predict(match, seatIndex)
        }
    }

    fun isAvailable(): Boolean = isLoaded && inferenceEngine?.isAvailable() == true

    fun getInputDim(): Int = inferenceEngine?.ioContract?.obsDim ?: encoder.getInputDim()

    fun getOutputDim(): Int = inferenceEngine?.ioContract?.outputDim ?: 0

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

    fun getStatus(): ModelStatus {
        val contract = inferenceEngine?.ioContract
        return ModelStatus(
            isLoaded = isLoaded,
            isAvailable = inferenceEngine?.isAvailable() == true,
            ioContract = contract,
            inputDim = getInputDim(),
            actionDim = contract?.actionDim ?: ActionFeatureEncoder.ACTION_FEATURE_DIM,
            outputDim = getOutputDim(),
            outputName = contract?.outputName.orEmpty(),
        )
    }

    data class ModelStatus(
        val isLoaded: Boolean,
        val isAvailable: Boolean,
        val ioContract: OnnxModelIoContract?,
        val inputDim: Int,
        val actionDim: Int,
        val outputDim: Int,
        val outputName: String,
    )
}

internal data class BatchedActionInput(
    val obsBatch: FloatArray,
    val actionsBatch: FloatArray,
    val batchSize: Int,
    val obsDim: Int,
)

internal fun buildBatchedActionInput(
    obs: FloatArray,
    actionFeatures: List<FloatArray>,
): BatchedActionInput {
    require(actionFeatures.isNotEmpty()) { "actionFeatures must not be empty" }

    val obsDim = obs.size
    val batchSize = actionFeatures.size
    val obsBatch = FloatArray(batchSize * obsDim)
    for (i in 0 until batchSize) {
        System.arraycopy(obs, 0, obsBatch, i * obsDim, obsDim)
    }

    val actionDim = actionFeatures.first().size
    val actionsBatch = FloatArray(batchSize * actionDim)
    for ((i, feature) in actionFeatures.withIndex()) {
        val src = if (feature.size == actionDim) {
            feature
        } else {
            FloatArray(actionDim) { idx -> feature.getOrElse(idx) { 0f } }
        }
        System.arraycopy(src, 0, actionsBatch, i * actionDim, actionDim)
    }

    return BatchedActionInput(
        obsBatch = obsBatch,
        actionsBatch = actionsBatch,
        batchSize = batchSize,
        obsDim = obsDim,
    )
}

internal fun alignActionValues(values: FloatArray, expectedSize: Int): FloatArray {
    val normalizedExpectedSize = expectedSize.coerceAtLeast(0)
    val alignedValues = when {
        normalizedExpectedSize == 0 -> FloatArray(0)
        values.size == normalizedExpectedSize -> values
        values.size > normalizedExpectedSize -> values.copyOf(normalizedExpectedSize)
        else -> FloatArray(normalizedExpectedSize) { Float.NEGATIVE_INFINITY }.also { aligned ->
            System.arraycopy(values, 0, aligned, 0, values.size)
        }
    }
    return alignedValues
}
