package com.example.chudadi.ai.base

import android.util.Log
import com.example.chudadi.ai.base.config.ModelConfig
import com.example.chudadi.ai.base.config.VariantConfig
import com.example.chudadi.ai.base.variant.OnnxModelVariant
import com.example.chudadi.ai.onnx.variant.V1DqnVariant
import com.example.chudadi.ai.onnx.variant.V2GruDqnVariant
import com.example.chudadi.ai.onnx.variant.V3DqnVariant

/**
 * AI 模块全局配置
 *
 * 集中管理 ONNX 模型变体注册表。
 * 变体的数据属性（模型文件名、I/O 契约）从 [ModelConfig] 注入，
 * 代码部分（编码器、推理管道）仍由 Kotlin 侧提供。
 *
 * 所有对 [variants] 和 [defaultVariantName] 的读写都通过 [lock] 同步，
 * 保证并发安全。
 */
object AIConfig {
    private const val TAG = "AIConfig"

    /** 模型在 assets 中的目录 */
    const val MODEL_ASSETS_DIR = "models"

    private val lock = Any()
    private var variants = listOf<OnnxModelVariant>()
    @Volatile
    private var defaultVariantName: String? = null

    /** 注册模型变体。名称必须唯一，重复注册会抛 [IllegalArgumentException]。 */
    fun register(variant: OnnxModelVariant) {
        synchronized(lock) {
            require(variants.none { it.name == variant.name }) {
                "Variant '${variant.name}' already registered"
            }
            variants = variants + variant
        }
    }

    /** 获取默认变体 */
    fun getDefaultVariant(): OnnxModelVariant = synchronized(lock) {
        // 先尝试按配置指定的默认名称查找
        defaultVariantName?.let { name ->
            variants.find { it.name == name }?.let { return@synchronized it }
        }
        // 降级到第一个注册的变体
        variants.firstOrNull() ?: error("No ONNX model variant registered")
    }

    /** 按名称查找变体 */
    fun getVariant(name: String): OnnxModelVariant? = synchronized(lock) {
        variants.find { it.name == name }
    }

    /** 向后兼容：默认模型文件名 */
    val DEFAULT_MODEL_NAME: String get() = getDefaultVariant().modelFileName

    /**
     * 从 [ModelConfig] 初始化所有已知变体。
     *
     * 遍历配置中的变体列表，匹配到 Kotlin 侧已实现的变体类后实例化并注册。
     * 配置中未匹配的条目会被跳过（仅打印警告）。
     *
     * 初始化过程是原子的：先构建新列表，再一次性替换引用，
     * 避免并发读取时看到空列表。
     *
     * @param config 从 assets 解析的模型配置
     */
    fun initialize(config: ModelConfig) {
        // 先在锁外构建完整列表（避免持锁时间过长）
        val newVariants = mutableListOf<OnnxModelVariant>()
        val skippedNames = mutableListOf<String>()

        config.variants.forEach { vc ->
            val variant = resolveKotlinVariant(vc)
            if (variant != null) {
                newVariants += variant
                Log.i(TAG, "Registered variant: name=${variant.name}, " +
                    "modelFile=${variant.modelFileName}")
            } else {
                skippedNames += vc.name
            }
        }

        if (skippedNames.isNotEmpty()) {
            Log.w(TAG, "No Kotlin implementation for variants: $skippedNames, skipped")
        }

        // 原子替换
        synchronized(lock) {
            variants = newVariants.toList()
            defaultVariantName = config.defaultVariant
        }

        Log.i(TAG, "AIConfig initialized: defaultVariant=${config.defaultVariant}, " +
            "registered=${newVariants.map { it.name }}")
    }

    /**
     * 将配置中的变体名称映射到 Kotlin 侧的变体实例。
     *
     * 新增模型变体时，在此处添加分支即可。
     */
    private fun resolveKotlinVariant(config: VariantConfig): OnnxModelVariant? = when (config.name) {
        V1DqnVariant.COMPANION_NAME -> V1DqnVariant.fromConfig(config)
        V2GruDqnVariant.COMPANION_NAME -> V2GruDqnVariant.fromConfig(config)
        V3DqnVariant.COMPANION_NAME -> V3DqnVariant.fromConfig(config)
        else -> null
    }
}
