package com.example.chudadi.ai.onnx

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.nio.FloatBuffer

data class OnnxModelIoContract(
    val obsInputName: String,
    val actionsInputName: String,
    val outputName: String,
    val obsDim: Int,
    val actionDim: Int,
    val outputDim: Int,
)

/**
 * ONNX 推理引擎。
 *
 * 负责模型加载与推理执行，提供线程安全调用、超时控制与统一异常封装。
 */
class OnnxInferenceEngine(modelPath: String) {

    companion object {
        private const val TAG = "OnnxInferenceEngine"
        private const val INFERENCE_TIMEOUT_MS = 5000L
        private const val EXPECTED_OBS_INPUT_DIM = GameStateEncoder.INPUT_DIM
        private const val EXPECTED_ACTION_INPUT_DIM = ActionFeatureEncoder.ACTION_FEATURE_DIM
        private const val EXPECTED_OUTPUT_DIM = 1

        // OrtEnvironment is process-wide singleton; do not close it per engine instance.
        private val sharedEnvironment: OrtEnvironment by lazy { OrtEnvironment.getEnvironment() }
    }

    private val environment: OrtEnvironment = sharedEnvironment
    private val sessionLock = Mutex()
    private var session: OrtSession? = null

    @Volatile
    private var isInitialized = false

    var ioContract: OnnxModelIoContract = OnnxModelIoContract(
        obsInputName = "obs",
        actionsInputName = "actions",
        outputName = "Q",
        obsDim = EXPECTED_OBS_INPUT_DIM,
        actionDim = EXPECTED_ACTION_INPUT_DIM,
        outputDim = EXPECTED_OUTPUT_DIM,
    )
        private set

    init {
        try {
            val modelFile = File(modelPath)
            if (!modelFile.exists()) {
                throw IllegalStateException("Model file not found: $modelPath")
            }

            val sessionOptions = OrtSession.SessionOptions().apply {
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                setIntraOpNumThreads(2)
                setInterOpNumThreads(2)
            }

            session = environment.createSession(modelPath, sessionOptions)
            isInitialized = true

            val modelInputNames = session?.inputInfo?.keys?.toList()
                ?: throw IllegalStateException("Model input names are empty")
            val modelOutputNames = session?.outputInfo?.keys?.toList()
                ?: throw IllegalStateException("Model output names are empty")

            val obsInputName = modelInputNames.find { it.contains("obs", ignoreCase = true) }
                ?: throw IllegalStateException(
                    "Model input names $modelInputNames do not contain required 'obs' input",
                )
            val actionsInputName = modelInputNames.find { it.contains("action", ignoreCase = true) }
                ?: throw IllegalStateException(
                    "Model input names $modelInputNames do not contain required 'actions' input",
                )
            val outputName = modelOutputNames.first()

            val obsNodeInfo = session?.inputInfo?.get(obsInputName)
                ?: throw IllegalStateException("Missing node info for obs input '$obsInputName'")
            val obsTensorInfo = obsNodeInfo.info as? ai.onnxruntime.TensorInfo
                ?: throw IllegalStateException("Obs input '$obsInputName' is not a tensor")
            val obsShape = obsTensorInfo.shape
                ?: throw IllegalStateException("Obs input '$obsInputName' shape is null")
            if (obsShape.size < 2 || obsShape[1] <= 0) {
                throw IllegalStateException(
                    "Invalid obs input shape for '$obsInputName': ${obsShape.contentToString()}",
                )
            }
            val detectedObsDim = obsShape[1].toInt()
            if (detectedObsDim != EXPECTED_OBS_INPUT_DIM) {
                throw IllegalStateException(
                    "Obs input dim mismatch: expected=$EXPECTED_OBS_INPUT_DIM, " +
                        "detected=$detectedObsDim, shape=${obsShape.contentToString()}",
                )
            }

            val actionsNodeInfo = session?.inputInfo?.get(actionsInputName)
                ?: throw IllegalStateException("Missing node info for actions input '$actionsInputName'")
            val actionsTensorInfo = actionsNodeInfo.info as? ai.onnxruntime.TensorInfo
                ?: throw IllegalStateException("Actions input '$actionsInputName' is not a tensor")
            val actionsShape = actionsTensorInfo.shape
                ?: throw IllegalStateException("Actions input '$actionsInputName' shape is null")
            if (actionsShape.size < 2 || actionsShape[1] <= 0) {
                throw IllegalStateException(
                    "Invalid actions input shape for '$actionsInputName': ${actionsShape.contentToString()}",
                )
            }
            val detectedActionsDim = actionsShape[1].toInt()
            if (detectedActionsDim != EXPECTED_ACTION_INPUT_DIM) {
                throw IllegalStateException(
                    "Actions input dim mismatch: expected=$EXPECTED_ACTION_INPUT_DIM, " +
                        "detected=$detectedActionsDim, shape=${actionsShape.contentToString()}",
                )
            }

            val outputNodeInfo = session?.outputInfo?.get(outputName)
                ?: throw IllegalStateException("Missing node info for output '$outputName'")
            val outputTensorInfo = outputNodeInfo.info as? ai.onnxruntime.TensorInfo
                ?: throw IllegalStateException("Output '$outputName' is not a tensor")
            val outputShape = outputTensorInfo.shape
                ?: throw IllegalStateException("Output '$outputName' shape is null")
            val detectedOutputDim = when {
                outputShape.size >= 2 && outputShape[1] > 0 -> outputShape[1].toInt()
                outputShape.size == 1 && outputShape[0] > 0 -> outputShape[0].toInt()
                outputShape.size == 1 -> EXPECTED_OUTPUT_DIM
                else -> throw IllegalStateException(
                    "Invalid output shape for '$outputName': ${outputShape.contentToString()}",
                )
            }
            if (detectedOutputDim != EXPECTED_OUTPUT_DIM) {
                throw IllegalStateException(
                    "Output dim mismatch: expected=$EXPECTED_OUTPUT_DIM, " +
                        "detected=$detectedOutputDim, shape=${outputShape.contentToString()}",
                )
            }

            ioContract = OnnxModelIoContract(
                obsInputName = obsInputName,
                actionsInputName = actionsInputName,
                outputName = outputName,
                obsDim = detectedObsDim,
                actionDim = detectedActionsDim,
                outputDim = detectedOutputDim,
            )
            Log.i(TAG, "Validated model I/O contract: $ioContract")

            Log.i(TAG, "ONNX model loaded successfully from $modelPath")
            Log.i(TAG, "Input names: $modelInputNames")
            Log.i(TAG, "Output names: $modelOutputNames, using: ${ioContract.outputName}")
            logModelInfo()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load ONNX model", e)
            throw OnnxInitializationException("Failed to initialize ONNX model", e)
        }
    }

    /**
     * 执行推理。
     *
     * @param obsTensor 观测输入扁平数组
     * @param actionsTensor 动作特征输入扁平数组（可空）
     * @param batchSize 批大小
     * @param obsDim 单条观测向量维度
     */
    suspend fun infer(
        obsTensor: FloatArray,
        actionsTensor: FloatArray? = null,
        batchSize: Int = 1,
        obsDim: Int = if (batchSize > 0) (obsTensor.size / batchSize) else obsTensor.size,
    ): FloatArray = withContext(Dispatchers.IO) {
        sessionLock.withLock {
            if (!isInitialized || session == null) {
                throw OnnxInferenceException("ONNX session not initialized")
            }
            try {
                withTimeout(INFERENCE_TIMEOUT_MS) {
                    runInference(
                        obsTensor = obsTensor,
                        actionsTensor = actionsTensor,
                        batchSize = batchSize,
                        obsDim = obsDim,
                    )
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Inference timeout after ${INFERENCE_TIMEOUT_MS}ms")
                throw OnnxTimeoutException("ONNX inference timeout", e)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Inference failed", e)
                throw OnnxInferenceException("ONNX inference failed: ${e.message}", e)
            }
        }
    }

    private fun runInference(
        obsTensor: FloatArray,
        actionsTensor: FloatArray?,
        batchSize: Int,
        obsDim: Int,
    ): FloatArray {
        val normalizedBatchSize = batchSize.coerceAtLeast(1)
        val normalizedObsDim = obsDim.coerceAtLeast(1)
        val expectedObsSize = normalizedBatchSize * normalizedObsDim
        if (obsTensor.size != expectedObsSize) {
            throw OnnxInferenceException(
                "obs tensor size mismatch: got=${obsTensor.size}, expected=$expectedObsSize",
            )
        }

        val inputs = mutableMapOf<String, OnnxTensor>()
        try {
            val (obsInputName, obsInputTensor) = createObsInput(
                obsTensor = obsTensor,
                normalizedBatchSize = normalizedBatchSize,
                normalizedObsDim = normalizedObsDim,
            )
            inputs[obsInputName] = obsInputTensor

            val (actionsInputName, actionsInputTensor) = createActionsInput(
                actionsTensor = actionsTensor,
                normalizedBatchSize = normalizedBatchSize,
            )
            inputs[actionsInputName] = actionsInputTensor

            return runSession(inputs)
        } finally {
            inputs.values.forEach { it.close() }
        }
    }

    private fun createObsInput(
        obsTensor: FloatArray,
        normalizedBatchSize: Int,
        normalizedObsDim: Int,
    ): Pair<String, OnnxTensor> {
        val obsInputName = ioContract.obsInputName
        val obsShape = longArrayOf(normalizedBatchSize.toLong(), normalizedObsDim.toLong())
        val obsBuffer = FloatBuffer.wrap(obsTensor)
        val obsOnnxTensor = OnnxTensor.createTensor(environment, obsBuffer, obsShape)
        return obsInputName to obsOnnxTensor
    }

    private fun createActionsInput(
        actionsTensor: FloatArray?,
        normalizedBatchSize: Int,
    ): Pair<String, OnnxTensor> {
        val actionsInputName = ioContract.actionsInputName
        val expectedActionSize = normalizedBatchSize * ioContract.actionDim
        val actionsData = actionsTensor ?: FloatArray(expectedActionSize)
        val normalizedActionsData = when {
            actionsData.size < expectedActionSize ->
                FloatArray(expectedActionSize) { index -> actionsData.getOrElse(index) { 0f } }

            actionsData.size > expectedActionSize -> actionsData.copyOf(expectedActionSize)
            else -> actionsData
        }
        val actionsShape = longArrayOf(normalizedBatchSize.toLong(), ioContract.actionDim.toLong())
        val actionsBuffer = FloatBuffer.wrap(normalizedActionsData)
        val actionsOnnxTensor = OnnxTensor.createTensor(environment, actionsBuffer, actionsShape)
        return actionsInputName to actionsOnnxTensor
    }

    private fun runSession(inputs: Map<String, OnnxTensor>): FloatArray {
        val activeSession = session ?: throw OnnxInferenceException("ONNX session not initialized")
        val requestedOutputName = ioContract.outputName
        val results = activeSession.run(inputs, setOf(requestedOutputName))
        return results.use { result ->
            val outputValue = result.get(requestedOutputName).orElseThrow {
                val availableOutputs = result.map { it.key }
                OnnxInferenceException(
                    "Missing output '$requestedOutputName' in inference result, " +
                        "available=$availableOutputs",
                )
            }
            val outputTensor = outputValue as? OnnxTensor
                ?: throw OnnxInferenceException(
                    "Output '$requestedOutputName' is not a tensor: ${outputValue.javaClass.simpleName}",
                )

            val outputBuffer = outputTensor.floatBuffer
            val outputArray = FloatArray(outputBuffer.remaining())
            outputBuffer.get(outputArray)
            Log.d(TAG, "Inference completed, output size: ${outputArray.size}")
            outputArray
        }
    }

    /** 检查引擎是否可用。 */
    fun isAvailable(): Boolean = isInitialized && session != null

    /** 释放会话资源。 */
    fun close() {
        try {
            val oldSession = runBlocking {
                sessionLock.withLock {
                    if (!isInitialized && session == null) {
                        null
                    } else {
                        val s = session
                        session = null
                        isInitialized = false
                        s
                    }
                }
            }
            oldSession?.close()
            if (oldSession != null) {
                Log.i(TAG, "ONNX resources released")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error closing ONNX resources", e)
        }
    }

    private fun logModelInfo() {
        try {
            val inputInfo = session?.inputInfo
            val outputInfo = session?.outputInfo

            Log.i(TAG, "Model inputs: ${inputInfo?.keys}")
            Log.i(TAG, "Model outputs: ${outputInfo?.keys}")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to log model info", e)
        }
    }
}

class OnnxInitializationException(message: String, cause: Throwable? = null) : Exception(message, cause)

class OnnxInferenceException(message: String, cause: Throwable? = null) : Exception(message, cause)

class OnnxTimeoutException(message: String, cause: Throwable? = null) : Exception(message, cause)
