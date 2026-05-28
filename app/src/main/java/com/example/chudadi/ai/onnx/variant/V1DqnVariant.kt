package com.example.chudadi.ai.onnx.variant

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
 */
object V1DqnVariant : OnnxModelVariant {
    override val name = "v1_dqn"
    override val modelFileName = "test.onnx"
    override val ioContract = ModelIoContract(
        obsInputName = "obs",
        actionsInputName = "actions",
        outputName = "Q",
        obsDim = GameStateEncoder.INPUT_DIM,
        actionDim = ActionFeatureEncoder.ACTION_FEATURE_DIM,
        outputDim = 1,
    )

    override fun createObsEncoder(): ObservationEncoder = GameStateEncoder()

    override fun createPipeline(): InferencePipeline = DqnBatchPipeline()
}
