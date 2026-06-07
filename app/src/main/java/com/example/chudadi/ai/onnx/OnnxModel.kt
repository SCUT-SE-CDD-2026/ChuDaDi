package com.example.chudadi.ai.onnx

import android.util.Log
import com.example.chudadi.ai.base.variant.ModelIoContract
import kotlinx.coroutines.CancellationException

/**
 * ONNX model wrapper for match-state inference.
 */
class OnnxModel(
    modelPath: String,
    private val ioContract: ModelIoContract,
) {

    companion object {
        private const val TAG = "OnnxModel"
    }

    private var inferenceEngine: OnnxInferenceEngine? = null
    @Volatile
    private var isLoaded = false

    init {
        try {
            inferenceEngine = OnnxInferenceEngine(modelPath, ioContract)
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
        } catch (e: Error) {
            throw e
        }
    }

    /**
     * Batch predict Q-values with a pre-encoded observation.
     *
     * The caller (pipeline) provides a pre-encoded obs vector,
     * avoiding redundant re-encoding per inference call.
     */
    suspend fun predictActionValuesWithObs(
        obs: FloatArray,
        actionFeatures: List<FloatArray>,
    ): FloatArray? {
        if (!isLoaded || inferenceEngine == null || actionFeatures.isEmpty()) {
            return null
        }

        return try {
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

    suspend fun predictActionValuesWithObsAndHistory(
        obs: FloatArray,
        history: FloatArray,
        actionFeatures: List<FloatArray>,
    ): FloatArray? {
        if (!isLoaded || inferenceEngine == null || actionFeatures.isEmpty()) {
            return null
        }

        return try {
            val batchedInput = buildBatchedActionInput(
                obs = obs,
                actionFeatures = actionFeatures,
            )
            val batchedHistory = buildBatchedHistoryInput(
                history = history,
                batchSize = batchedInput.batchSize,
            )
            val rawValues = inferenceEngine?.infer(
                obsTensor = batchedInput.obsBatch,
                actionsTensor = batchedInput.actionsBatch,
                historyTensor = batchedHistory,
                batchSize = batchedInput.batchSize,
                obsDim = batchedInput.obsDim,
            )
            rawValues?.let { alignActionValues(it, batchedInput.batchSize) }
        } catch (e: OnnxInferenceException) {
            Log.e(TAG, "Batch inference with history failed", e)
            null
        } catch (e: OnnxTimeoutException) {
            Log.e(TAG, "Batch inference with history timeout", e)
            null
        } catch (e: CancellationException) {
            throw e
        }
    }
    fun isAvailable(): Boolean = isLoaded && inferenceEngine?.isAvailable() == true

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
}

internal data class BatchedActionInput(
    val obsBatch: FloatArray,
    val actionsBatch: FloatArray,
    val batchSize: Int,
    val obsDim: Int,
)

internal fun buildBatchedHistoryInput(history: FloatArray, batchSize: Int): FloatArray {
    val normalizedBatchSize = batchSize.coerceAtLeast(0)
    if (normalizedBatchSize == 0 || history.isEmpty()) return FloatArray(0)
    val historyDim = history.size
    val batchedHistory = FloatArray(normalizedBatchSize * historyDim)
    for (i in 0 until normalizedBatchSize) {
        System.arraycopy(history, 0, batchedHistory, i * historyDim, historyDim)
    }
    return batchedHistory
}

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
