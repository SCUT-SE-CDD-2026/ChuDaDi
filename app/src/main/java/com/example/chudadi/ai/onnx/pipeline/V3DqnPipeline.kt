package com.example.chudadi.ai.onnx.pipeline

import com.example.chudadi.ai.base.variant.InferencePipeline

/**
 * V3 DQN 推理管道。
 *
 * V3 与 V1 同为两输入模型（obs + actions -> Q），但模型变体、文件名与
 * UI 入口独立，避免把 V1/V3 的 334 维模型误认为同一个策略。
 */
class V3DqnPipeline(
    private val delegate: DqnBatchPipeline = DqnBatchPipeline(),
) : InferencePipeline by delegate
