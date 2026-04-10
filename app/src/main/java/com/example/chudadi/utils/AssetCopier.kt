package com.example.chudadi.utils

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import java.io.File
import java.io.IOException

/**
 * 资源文件复制工具
 */
object AssetCopier {
    private const val TAG = "AssetCopier"
    private const val MODEL_ASSETS_DIR = "models"

    /**
     * 复制 assets/models 下的 ONNX 模型到应用私有目录。
     *
     * 返回 true 的条件：
     * - 至少存在一个 .onnx 候选文件；
     * - 复制流程结束后，至少一个 .onnx 文件在私有目录可用；
     * - 且没有 .onnx 文件复制失败。
     */
    fun copyModelsToPrivateDir(context: Context): Boolean {
        return try {
            val modelsDir = File(context.filesDir, MODEL_ASSETS_DIR)
            if (!modelsDir.exists() && !modelsDir.mkdirs()) {
                Log.e(TAG, "Failed to create model directory: ${modelsDir.absolutePath}")
                return false
            }

            val assetManager = context.assets
            val modelFiles = assetManager.list(MODEL_ASSETS_DIR).orEmpty()
            val onnxFiles = modelFiles.filter { it.endsWith(".onnx", ignoreCase = true) }

            if (onnxFiles.isEmpty()) {
                Log.w(TAG, "No .onnx model files found in assets/$MODEL_ASSETS_DIR")
                return false
            }

            var copiedCount = 0
            var readyCount = 0
            var failedCount = 0

            onnxFiles.forEach { modelFile ->
                val destFile = File(modelsDir, modelFile)
                try {
                    val copied = copyAssetFile(
                        assetManager = assetManager,
                        assetPath = "$MODEL_ASSETS_DIR/$modelFile",
                        destFile = destFile,
                    )
                    if (copied) {
                        copiedCount++
                    }
                    if (destFile.exists()) {
                        readyCount++
                    }
                } catch (e: IOException) {
                    failedCount++
                    Log.e(TAG, "Failed to copy model file: $modelFile", e)
                }
            }

            val success = readyCount > 0 && failedCount == 0
            val summary =
                "Model copy summary: candidates=${onnxFiles.size}, copied=$copiedCount, " +
                    "ready=$readyCount, failed=$failedCount, success=$success"
            Log.i(
                TAG,
                summary,
            )
            success
        } catch (e: IOException) {
            Log.e(TAG, "Failed to list/copy model assets", e)
            false
        }
    }

    /**
     * 获取模型文件在私有目录中的路径。
     */
    fun getModelPath(context: Context, modelName: String): String? {
        val modelFile = File(context.filesDir, "$MODEL_ASSETS_DIR/$modelName")
        return if (modelFile.exists()) {
            modelFile.absolutePath
        } else {
            null
        }
    }

    /**
     * 检查模型文件是否已复制到私有目录。
     */
    fun isModelAvailable(context: Context, modelName: String): Boolean {
        return getModelPath(context, modelName) != null
    }

    @Throws(IOException::class)
    private fun copyAssetFile(
        assetManager: AssetManager,
        assetPath: String,
        destFile: File,
    ): Boolean {
        if (destFile.exists()) {
            Log.d(TAG, "Model file already exists: ${destFile.name}")
            return false
        }

        assetManager.open(assetPath).use { input ->
            destFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        Log.i(TAG, "Copied model: $assetPath -> ${destFile.absolutePath}")
        return true
    }
}
