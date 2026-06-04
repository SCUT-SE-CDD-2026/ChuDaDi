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
import com.example.chudadi.ai.onnx.GameStateEncoderV2
import com.example.chudadi.ai.onnx.PlayedHistorySequenceEncoder
import com.example.chudadi.ai.onnx.pipeline.V2GruDqnPipeline

class V2GruDqnVariant(
    override val modelFileName: String,
    override val ioContract: ModelIoContract,
) : OnnxModelVariant {
    override val name: String = COMPANION_NAME

    override fun createObsEncoder(): ObservationEncoder = GameStateEncoderV2()

    override fun createPipeline(): InferencePipeline = V2GruDqnPipeline()

    companion object {
        const val COMPANION_NAME = "v2_gru_dqn"
        private const val DEFAULT_MODEL_FILE_NAME = "chudadi_v2_01.onnx"

        private val DEFAULT_IO_CONTRACT = ModelIoContract(
            obsInputName = "obs",
            actionsInputName = "actions",
            outputName = "Q",
            obsDim = GameStateEncoderV2.INPUT_DIM,
            actionDim = ActionFeatureEncoder.ACTION_FEATURE_DIM,
            outputDim = 1,
            historyInputName = "history",
            historyPlayers = PlayedHistorySequenceEncoder.RELATIVE_PLAYER_COUNT,
            historyLen = PlayedHistorySequenceEncoder.DEFAULT_HISTORY_LEN,
            historyDim = 52,
        )

        fun fromConfig(config: VariantConfig?): V2GruDqnVariant {
            if (config != null) validateDims(config)
            return V2GruDqnVariant(
                modelFileName = config?.modelFileName ?: DEFAULT_MODEL_FILE_NAME,
                ioContract = config?.ioContract?.toModelIoContract() ?: DEFAULT_IO_CONTRACT,
            )
        }

        fun createDefault(): V2GruDqnVariant = V2GruDqnVariant(
            modelFileName = DEFAULT_MODEL_FILE_NAME,
            ioContract = DEFAULT_IO_CONTRACT,
        )

        private fun validateDims(config: VariantConfig) {
            val contract = config.ioContract
            val msg = when {
                contract.obsDim != GameStateEncoderV2.INPUT_DIM ->
                    "obsDim mismatch: config=${contract.obsDim}, encoder=${GameStateEncoderV2.INPUT_DIM}"
                contract.actionDim != ActionFeatureEncoder.ACTION_FEATURE_DIM ->
                    "actionDim mismatch: config=${contract.actionDim}, " +
                        "encoder=${ActionFeatureEncoder.ACTION_FEATURE_DIM}"
                contract.historyPlayers != PlayedHistorySequenceEncoder.RELATIVE_PLAYER_COUNT ->
                    "historyPlayers mismatch: config=${contract.historyPlayers}"
                contract.historyLen != PlayedHistorySequenceEncoder.DEFAULT_HISTORY_LEN ->
                    "historyLen mismatch: config=${contract.historyLen}"
                contract.historyDim != 52 ->
                    "historyDim mismatch: config=${contract.historyDim}"
                else -> null
            }
            if (msg != null) {
                if (BuildConfig.DEBUG) error(msg) else Log.w(COMPANION_NAME, msg)
            }
        }
    }
}
