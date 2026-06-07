package com.example.chudadi.ai.base.variant

/**
 * ONNX 模型 I/O 契约。
 *
 * 描述模型期望的输入/输出张量名称与维度。
 * [actionsInputName] 为 null 表示单输入模型（无动作特征输入）。
 */
data class ModelIoContract(
    val obsInputName: String,
    val actionsInputName: String?,
    val outputName: String,
    val obsDim: Int,
    val actionDim: Int?,
    val outputDim: Int,
    val historyInputName: String? = null,
    val historyPlayers: Int? = null,
    val historyLen: Int? = null,
    val historyDim: Int? = null,
)
