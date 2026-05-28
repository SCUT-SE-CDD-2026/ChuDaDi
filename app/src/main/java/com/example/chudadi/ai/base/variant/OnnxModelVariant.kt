package com.example.chudadi.ai.base.variant

/**
 * ONNX 模型变体描述。
 *
 * 每种模型结构对应一个实现。添加新模型只需：
 * 1. 编写观测编码器（实现 [ObservationEncoder]）
 * 2. 选择或编写推理管道（实现 [InferencePipeline]）
 * 3. 声明一个 [OnnxModelVariant] 实现类
 * 4. 注册到 [com.example.chudadi.ai.base.AIConfig]
 *
 * 框架（工厂、控制器、引擎）不包含任何模型特定的硬编码。
 */
interface OnnxModelVariant {
    /** 变体唯一标识，用于注册表查找 */
    val name: String

    /** ONNX 模型文件名（位于 assets/models/ 下） */
    val modelFileName: String

    /** 模型 I/O 契约（输入/输出张量名称与维度） */
    val ioContract: ModelIoContract

    /** 创建观测编码器实例 */
    fun createObsEncoder(): ObservationEncoder

    /** 创建推理管道实例 */
    fun createPipeline(): InferencePipeline
}
