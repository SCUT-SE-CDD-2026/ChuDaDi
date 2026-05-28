package com.example.chudadi.ai.onnx.variant

import android.util.Log
import com.example.chudadi.ai.base.config.VariantConfig
import com.example.chudadi.ai.base.config.toModelIoContract
import com.example.chudadi.ai.base.variant.InferencePipeline
import com.example.chudadi.ai.base.variant.ModelIoContract
import com.example.chudadi.ai.base.variant.ObservationEncoder
import com.example.chudadi.ai.base.variant.OnnxModelVariant
import com.example.chudadi.ai.onnx.ActionFeatureEncoder
import com.example.chudadi.ai.onnx.GameStateEncoder
import com.example.chudadi.ai.onnx.pipeline.DqnBatchPipeline

/**
 * V1 DQN 模型变体。
 *
 * 对应当前 RLCard ChuDaDi DMC 双输入模型（obs + actions → Q）。
 * 观测维度 334，动作特征维度 139，输出维度 1（Q 值）。
 *
 * [modelFileName] 和 [ioContract] 从外部配置注入，
 * [createObsEncoder] 和 [createPipeline] 仍由 Kotlin 代码提供。
 */
class V1DqnVariant(
    override val modelFileName: String,
    override val ioContract: ModelIoContract,
) : OnnxModelVariant {

    override val name: String = COMPANION_NAME

    override fun createObsEncoder(): ObservationEncoder = GameStateEncoder()

    override fun createPipeline(): InferencePipeline = DqnBatchPipeline()

    companion object {
        const val COMPANION_NAME = "v1_dqn"

        /** 硬编码默认值，配置缺失时降级使用 */
        private const val DEFAULT_MODEL_FILE_NAME = "test.onnx"

        private val DEFAULT_IO_CONTRACT = ModelIoContract(
            obsInputName = "obs",
            actionsInputName = "actions",
            outputName = "Q",
            obsDim = GameStateEncoder.INPUT_DIM,
            actionDim = ActionFeatureEncoder.ACTION_FEATURE_DIM,
            outputDim = 1,
        )

        /**
         * 从 [VariantConfig] 创建实例。
         *
         * @param config 变体配置；null 时使用硬编码默认值
         */
        fun fromConfig(config: VariantConfig?): V1DqnVariant {
            if (config != null) {
                validateDims(config)
            }
            return V1DqnVariant(
                modelFileName = config?.modelFileName ?: DEFAULT_MODEL_FILE_NAME,
                ioContract = config?.ioContract?.toModelIoContract() ?: DEFAULT_IO_CONTRACT,
            )
        }

        /**
         * 校验配置中的维度是否与编码器兼容。
         * 维度不匹配会导致推理静默产出垃圾结果，因此尽早暴露。
         */
        private fun validateDims(config: VariantConfig) {
            val contract = config.ioContract
            if (contract.obsDim != GameStateEncoder.INPUT_DIM) {
                Log.w(
                    COMPANION_NAME,
                    "obsDim mismatch: config=${contract.obsDim}, " +
                        "encoder=${GameStateEncoder.INPUT_DIM}. " +
                        "Inference may produce garbage results.",
                )
            }
            if (contract.actionDim != null &&
                contract.actionDim != ActionFeatureEncoder.ACTION_FEATURE_DIM
            ) {
                Log.w(
                    COMPANION_NAME,
                    "actionDim mismatch: config=${contract.actionDim}, " +
                        "encoder=${ActionFeatureEncoder.ACTION_FEATURE_DIM}. " +
                        "Inference may produce garbage results.",
                )
            }
        }

        /**
         * 使用硬编码默认值创建实例（向后兼容）。
         */
        fun createDefault(): V1DqnVariant = V1DqnVariant(
            modelFileName = DEFAULT_MODEL_FILE_NAME,
            ioContract = DEFAULT_IO_CONTRACT,
        )
    }
}
