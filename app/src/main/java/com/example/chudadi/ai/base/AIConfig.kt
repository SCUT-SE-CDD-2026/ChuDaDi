package com.example.chudadi.ai.base

import com.example.chudadi.ai.base.variant.OnnxModelVariant

/**
 * AI 模块全局配置
 *
 * 集中管理 ONNX 模型文件名、assets 目录等常量，
 * 同时充当模型变体注册表。
 */
object AIConfig {
    /** 模型在 assets 中的目录 */
    const val MODEL_ASSETS_DIR = "models"

    private val variants = mutableListOf<OnnxModelVariant>()

    /** 注册模型变体。名称必须唯一，重复注册会抛 [IllegalArgumentException]。 */
    fun register(variant: OnnxModelVariant) {
        require(variants.none { it.name == variant.name }) {
            "Variant '${variant.name}' already registered"
        }
        variants += variant
    }

    /** 获取默认变体（第一个注册的变体） */
    fun getDefaultVariant(): OnnxModelVariant =
        variants.firstOrNull()
            ?: error("No ONNX model variant registered")

    /** 按名称查找变体 */
    fun getVariant(name: String): OnnxModelVariant? = variants.find { it.name == name }

    /** 向后兼容：默认模型文件名 */
    val DEFAULT_MODEL_NAME: String get() = getDefaultVariant().modelFileName
}
