package com.example.chudadi.ai.onnx.variant

import android.util.Log
import com.example.chudadi.BuildConfig
import com.example.chudadi.ai.base.config.VariantConfig
import com.example.chudadi.ai.base.config.toModelIoContract
import com.example.chudadi.ai.base.variant.InferencePipeline
import com.example.chudadi.ai.base.variant.ModelIoContract
import com.example.chudadi.ai.base.variant.ObservationEncoder
import com.example.chudadi.ai.base.variant.OnnxModelVariant
import com.example.chudadi.ai.onnx.ActionFeatureEncoder
import com.example.chudadi.ai.onnx.GameStateEncoder
import com.example.chudadi.ai.onnx.pipeline.V3DqnPipeline

/**
 * V3 DQN 模型变体。
 *
 * V3 使用 334 维累计历史 obs，与 V1 维度相同；通过独立变体名和模型文件名
 * 保持运行时路由清晰，避免误加载 V1 模型。
 */
class V3DqnVariant(
    override val modelFileName: String,
    override val ioContract: ModelIoContract,
) : OnnxModelVariant {
    override val name: String = COMPANION_NAME

    override fun createObsEncoder(): ObservationEncoder = GameStateEncoder()

    override fun createPipeline(): InferencePipeline = V3DqnPipeline()

    companion object {
        const val COMPANION_NAME = "v3_dqn"
        private const val DEFAULT_MODEL_FILE_NAME = "chudadi_v3_01.onnx"

        private val DEFAULT_IO_CONTRACT = ModelIoContract(
            obsInputName = "obs",
            actionsInputName = "actions",
            outputName = "Q",
            obsDim = GameStateEncoder.INPUT_DIM,
            actionDim = ActionFeatureEncoder.ACTION_FEATURE_DIM,
            outputDim = 1,
        )

        fun fromConfig(config: VariantConfig?): V3DqnVariant {
            if (config != null) validateDims(config)
            return V3DqnVariant(
                modelFileName = config?.modelFileName ?: DEFAULT_MODEL_FILE_NAME,
                ioContract = config?.ioContract?.toModelIoContract() ?: DEFAULT_IO_CONTRACT,
            )
        }

        fun createDefault(): V3DqnVariant = V3DqnVariant(
            modelFileName = DEFAULT_MODEL_FILE_NAME,
            ioContract = DEFAULT_IO_CONTRACT,
        )

        private fun validateDims(config: VariantConfig) {
            val contract = config.ioContract
            val msg = when {
                contract.obsDim != GameStateEncoder.INPUT_DIM ->
                    "obsDim mismatch: config=${contract.obsDim}, encoder=${GameStateEncoder.INPUT_DIM}"
                contract.actionDim != ActionFeatureEncoder.ACTION_FEATURE_DIM ->
                    "actionDim mismatch: config=${contract.actionDim}, " +
                        "encoder=${ActionFeatureEncoder.ACTION_FEATURE_DIM}"
                contract.historyInputName != null || contract.historyPlayers != null ||
                    contract.historyLen != null || contract.historyDim != null ->
                    "V3 is a two-input model and must not declare history input fields."
                else -> null
            }
            if (msg != null) {
                if (BuildConfig.DEBUG) error(msg) else Log.w(COMPANION_NAME, msg)
            }
        }
    }
}
