package com.example.chudadi.ai.base.config

import android.content.Context
import android.util.Log
import com.example.chudadi.ai.base.variant.ModelIoContract
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * ONNX 模型配置文件数据模型。
 *
 * 对应 `assets/models/model_config.json` 的结构。
 * 通过 [load] 从 assets 中读取并解析。
 */
@Serializable
data class ModelConfig(
    /** 默认变体名称，对应 [VariantConfig.name] */
    val defaultVariant: String,
    /** 所有已注册变体的配置列表 */
    val variants: List<VariantConfig>,
)

/**
 * 单个模型变体的配置。
 *
 * 包含模型文件名和 I/O 契约——这些是适合外部配置的数据。
 * 编码器（[com.example.chudadi.ai.base.variant.ObservationEncoder]）和推理管道
 * （[com.example.chudadi.ai.base.variant.InferencePipeline]）属于代码，仍在 Kotlin 中实现。
 */
@Serializable
data class VariantConfig(
    /** 变体唯一标识，需与 Kotlin 侧注册的变体名称对应 */
    val name: String,
    /** ONNX 模型文件名（位于 assets/models/ 下） */
    val modelFileName: String,
    /** 模型 I/O 契约 */
    val ioContract: IoContractConfig,
)

/**
 * ONNX 模型 I/O 契约配置。
 *
 * 对应 [ModelIoContract] 的可序列化表示。
 * [actionsInputName] 和 [actionDim] 为 null 时表示单输入模型。
 */
@Serializable
data class IoContractConfig(
    val obsInputName: String,
    val actionsInputName: String? = null,
    val outputName: String,
    val obsDim: Int,
    val actionDim: Int? = null,
    val outputDim: Int,
)

/**
 * 将 [IoContractConfig] 转换为 [ModelIoContract]。
 */
fun IoContractConfig.toModelIoContract(): ModelIoContract = ModelIoContract(
    obsInputName = obsInputName,
    actionsInputName = actionsInputName,
    outputName = outputName,
    obsDim = obsDim,
    actionDim = actionDim,
    outputDim = outputDim,
)

/**
 * 模型配置加载器。
 *
 * 从 `assets/models/model_config.json` 读取并反序列化。
 * 解析失败时返回 null，调用方应使用硬编码默认值降级。
 */
object ModelConfigLoader {

    private const val TAG = "ModelConfigLoader"
    private const val CONFIG_FILE = "models/model_config.json"

    private val json = Json {
        ignoreUnknownKeys = true
    }

    /**
     * 从 assets 加载模型配置。
     *
     * @return 解析成功返回 [ModelConfig]；文件不存在或解析失败返回 null
     */
    @Suppress("SwallowedException")
    fun load(context: Context): ModelConfig? {
        return try {
            val assets = context.assets
                ?: return null.also { Log.e(TAG, "Context.assets is null") }
            val text = assets.open(CONFIG_FILE)
                .bufferedReader()
                .use { it.readText() }
            val config = json.decodeFromString<ModelConfig>(text)
            Log.i(TAG, "Model config loaded: defaultVariant=${config.defaultVariant}, " +
                "variants=${config.variants.map { it.name }}")
            config
        } catch (e: java.io.FileNotFoundException) {
            Log.w(TAG, "Model config file not found: $CONFIG_FILE")
            null
        } catch (e: SerializationException) {
            Log.e(TAG, "Failed to parse model config from $CONFIG_FILE", e)
            null
        } catch (e: java.io.IOException) {
            Log.e(TAG, "Failed to read model config from $CONFIG_FILE", e)
            null
        }
    }
}
