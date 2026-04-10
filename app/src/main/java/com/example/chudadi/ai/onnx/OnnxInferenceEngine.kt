package com.example.chudadi.ai.onnx

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.nio.FloatBuffer

/**
 * ONNX推理引擎
 *
 * 负责ONNX模型的加载和推理执行。
 * 提供线程安全的推理接口，支持超时控制和异常处理。
 */
class OnnxInferenceEngine(modelPath: String) {

    companion object {
        private const val TAG = "OnnxInferenceEngine"
        private const val INFERENCE_TIMEOUT_MS = 5000L
        // OrtEnvironment is process-wide singleton; do not close it per engine instance.
        private val sharedEnvironment: OrtEnvironment by lazy { OrtEnvironment.getEnvironment() }
    }

    private val environment: OrtEnvironment = sharedEnvironment
    private val sessionLock = Mutex()
    private var session: OrtSession? = null
    @Volatile
    private var isInitialized = false
    private var inputNames: List<String> = listOf("obs")
    private var outputName: String = "Q"
    private var actionsInputDim: Int = 138

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

            // 动态读取输入输出名称
            val modelInputNames = session?.inputInfo?.keys?.toList()
            val outputNames = session?.outputInfo?.keys?.toList()

            inputNames = modelInputNames ?: listOf("obs")
            outputName = outputNames?.firstOrNull() ?: "Q"

            try {
                val actionsInputName = inputNames.find { it.contains("action", ignoreCase = true) }
                if (actionsInputName != null) {
                    val nodeInfo = session?.inputInfo?.get(actionsInputName)
                    val tensorInfo = nodeInfo?.info as? ai.onnxruntime.TensorInfo
                    val shape = tensorInfo?.shape
                    if (shape != null && shape.size >= 2 && shape[1] > 0) {
                        actionsInputDim = shape[1].toInt()
                        Log.i(TAG, "Dynamic actions input dim detected: $actionsInputDim")
                    } else {
                        Log.w(TAG, "Could not detect actions input dim, using default: $actionsInputDim")
                    }
                }
            } catch (e: Exception) {
                Log.w(
                    TAG,
                    "Failed to read actions input dim dynamically: ${e.message}, using default: $actionsInputDim",
                )
            }

            Log.i(TAG, "ONNX model loaded successfully from $modelPath")
            Log.i(TAG, "Input names: $inputNames")
            Log.i(TAG, "Output names: $outputNames, using: $outputName")
            logModelInfo()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load ONNX model", e)
            throw OnnxInitializationException("Failed to initialize ONNX model", e)
        }
    }

    /**
     * 执行推理
     *
     * @param obsTensor 观察值输入张量（FloatArray）- 对应 "obs" 输入
     * @param actionsTensor 动作输入张量（FloatArray）- 对应 "actions" 输入（可选）
     * @return 模型输出（FloatArray）
     * @throws OnnxInferenceException 推理失败时抛出
     * @throws OnnxTimeoutException 推理超时时抛出
     */
    suspend fun infer(
        obsTensor: FloatArray,
        actionsTensor: FloatArray? = null,
    ): FloatArray = withContext(Dispatchers.IO) {
        sessionLock.withLock {
            if (!isInitialized || session == null) {
                throw OnnxInferenceException("ONNX session not initialized")
            }

            try {
                withTimeout(INFERENCE_TIMEOUT_MS) {
                    val inputs = mutableMapOf<String, OnnxTensor>()

                    val obsShape = longArrayOf(1, obsTensor.size.toLong())
                    val obsBuffer = FloatBuffer.wrap(obsTensor)
                    val obsOnnxTensor = OnnxTensor.createTensor(environment, obsBuffer, obsShape)

                    val obsInputName =
                        inputNames.find { it.contains("obs", ignoreCase = true) }
                            ?: inputNames.firstOrNull()
                            ?: "obs"
                    inputs[obsInputName] = obsOnnxTensor

                    val actionsInputName = inputNames.find { it.contains("action", ignoreCase = true) }
                    if (actionsInputName != null) {
                        val actionsData = actionsTensor ?: FloatArray(actionsInputDim)
                        val paddedActionsData = if (actionsData.size < actionsInputDim) {
                            FloatArray(actionsInputDim) { i -> actionsData.getOrElse(i) { 0f } }
                        } else {
                            actionsData.take(actionsInputDim).toFloatArray()
                        }
                        val actionsShape = longArrayOf(1, actionsInputDim.toLong())
                        val actionsBuffer = FloatBuffer.wrap(paddedActionsData)
                        val actionsOnnxTensor = OnnxTensor.createTensor(environment, actionsBuffer, actionsShape)
                        inputs[actionsInputName] = actionsOnnxTensor
                    }

                    try {
                        val results = session!!.run(inputs)
                        results.use { result ->
                            val outputTensor = result.get(0) as? OnnxTensor
                                ?: throw OnnxInferenceException("Failed to get output tensor")

                            val outputBuffer = outputTensor.floatBuffer
                            val outputArray = FloatArray(outputBuffer.remaining())
                            outputBuffer.get(outputArray)
                            Log.d(TAG, "Inference completed, output size: ${outputArray.size}")

                            outputArray
                        }
                    } finally {
                        inputs.values.forEach { it.close() }
                    }
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

    /**
     * 检查引擎是否可用
     */
    fun isAvailable(): Boolean = isInitialized && session != null

    /**
     * 释放资源
     */
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

    /**
     * 获取模型输入维度
     * 注意：ONNX Runtime Java API 限制了直接访问 shape 信息
     * 暂时返回默认值，实际维度由模型运行时决定
     */
    fun getInputDim(): Int {
        return GameStateEncoder.INPUT_DIM
    }

    fun getOutputDim(): Int {
        return 0
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
