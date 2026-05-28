# ONNX 模型扩展指南

本文档说明如何在 ChuDaDi 项目中添加新的 ONNX 模型变体。架构为**可插拔模块化**设计，添加新模型只需编写一个 `OnnxModelVariant` 实现类。

---

## 1. 架构总览

### 1.1 数据流

```
Match + seatIndex + validActions
       │
       ▼
  InferencePipeline.decide()          ← suspend，管道自行决定编码/推理/解码策略
       │
       ▼
  AIDecision (PlayCards / Pass / Error)
       │
       ▼
  OnnxAIPlayerController.validateDecision()  ← 二次合法性校验
       │
       ▼
  GameEngine 规则校验（AI 不绕过规则权威层）
```

### 1.2 关键类

| 类 | 位置 | 职责 |
|---|---|---|
| `ModelIoContract` | `ai/base/variant/` | 数据类：模型输入/输出张量名称与维度 |
| `ObservationEncoder` | `ai/base/variant/` | 接口：牌局状态 → 观测向量 |
| `InferencePipeline` | `ai/base/variant/` | 接口：完整的"编码→推理→解码"流程（suspend） |
| `OnnxModelVariant` | `ai/base/variant/` | 接口：一个模型变体的完整声明（名称+文件+契约+编码器+管道） |
| `V1DqnVariant` | `ai/onnx/variant/` | V1 DQN 变体（object） |
| `DqnBatchPipeline` | `ai/onnx/pipeline/` | DQN 双输入 batch 推理管道 |
| `BitmaskActionIdMapper` | `ai/onnx/pipeline/` | 52 位 bitmask ↔ Card 列表互转 |
| `GameStateEncoder` | `ai/onnx/` | 334 维观测编码（V1 的 ObservationEncoder 实现） |
| `ActionFeatureEncoder` | `ai/onnx/` | 139 维动作特征编码（DQN 管道内部） |
| `ActionDecoder` | `ai/onnx/` | Q 值 → AIDecision（DQN 管道内部） |
| `OnnxInferenceEngine` | `ai/onnx/` | ORT session + ModelIoContract 维度校验（校验后覆盖为实际检测值） |
| `OnnxModel` | `ai/onnx/` | 模型包装器，提供 `predictActionValuesWithObs` 供管道调用 |
| `OnnxAIPlayerController` | `ai/onnx/` | 控制器，委托 pipeline.decide() + validateDecision() 二次校验 |
| `AIConfig` | `ai/base/` | 变体注册表：register / getVariant / getDefaultVariant |
| `AIFactory` | `ai/` | 工厂入口，接受 OnnxModelVariant? 处理创建与降级 |
| `AssetCopier` | `utils/` | assets/models/*.onnx → 应用私有目录 |

> 接口签名详见源码 `ai/base/variant/`，本文档不再重复。

### 1.3 创建链路

```
AIFactory.createAIPlayerWithStatus(difficulty, variant)
  ├─ variant != null → OnnxAIPlayerController(seatIndex, difficulty, modelPath, variant)
  │                      ├─ variant.createObsEncoder()
  │                      ├─ variant.createPipeline()
  │                      └─ OnnxModel(modelPath, obsEncoder, ioContract)
  │                           └─ OnnxInferenceEngine(modelPath, ioContract)  ← 维度校验
  └─ variant == null / 加载失败 → 降级为 RuleBasedAIAdapter
```

### 1.4 变体注册

注册发生在 `AIFactory.preloadModels()` 中，同时将模型文件从 assets 复制到私有目录：

```kotlin
// AIFactory 内部（幂等）
AIFactory.registerDefaultVariant()      // → AIConfig.register(V1DqnVariant)

// 添加新变体时
AIConfig.register(V2DqnVariant)         // 在 preloadModels() 旁添加
val v = AIConfig.getVariant("v1_dqn")   // 按名获取
val d = AIConfig.getDefaultVariant()    // 默认（第一个注册的）
```

---

## 2. 添加新模型

### 场景 A：同架构不同维度（DQN 换代）

仍是双输入 DQN（obs + actions → Q），但维度或文件名不同。只需写一个 object：

```kotlin
// ai/onnx/variant/V2DqnVariant.kt
object V2DqnVariant : OnnxModelVariant {
    override val name = "v2_dqn"
    override val modelFileName = "v2_model.onnx"
    override val ioContract = ModelIoContract(
        obsInputName = "obs", actionsInputName = "actions", outputName = "Q",
        obsDim = V2ObsEncoder.INPUT_DIM, actionDim = V2ActionEncoder.ACTION_DIM, outputDim = 1,
    )
    override fun createObsEncoder() = V2ObsEncoder()
    override fun createPipeline() = DqnBatchPipeline()  // 复用现有管道
}
```

改动清单：新建变体 object + 新编码器（如 obs 布局变化）+ `AIFactory` 加一行注册 + 放入 `.onnx` 文件。

**不需要改**：OnnxInferenceEngine、OnnxModel、OnnxAIPlayerController。

### 场景 B：不同架构（如单输入策略网络）

obs → 动作概率分布，无 actions 输入。需写新管道：

```kotlin
// ai/onnx/variant/PolicyV1Variant.kt
object PolicyV1Variant : OnnxModelVariant {
    override val name = "policy_v1"
    override val modelFileName = "policy_model.onnx"
    override val ioContract = ModelIoContract(
        obsInputName = "obs", actionsInputName = null,  // null = 单输入
        outputName = "probs", obsDim = 334, actionDim = null, outputDim = 169,
    )
    override fun createObsEncoder() = GameStateEncoder()     // 可复用
    override fun createPipeline() = PolicySamplingPipeline()  // 新管道
}
```

> **单输入调用方式**：`ioContract.actionsInputName = null` 时引擎不创建 actions 张量。管道中调用 `model.predictActionValuesWithObs(obs, listOf(FloatArray(0)))` 即可。

改动清单：新建变体 object + 新管道 + `AIFactory` 注册 + 放入 `.onnx` 文件。如需 UI 区分，还需扩展 `SeatControllerType` 枚举和房间界面。

---

## 3. 文件结构

```
ai/
├── base/
│   ├── variant/                     ← 框架层：模型无关的接口
│   │   ├── ModelIoContract.kt
│   │   ├── ObservationEncoder.kt
│   │   ├── InferencePipeline.kt
│   │   └── OnnxModelVariant.kt
│   ├── AIConfig.kt                  ← 注册表
│   └── ValidCombinationResolver.kt  ← 合法出牌 + ♦3 + 北方 pass
├── onnx/
│   ├── variant/V1DqnVariant.kt      ← 变体声明
│   ├── pipeline/
│   │   ├── BitmaskActionIdMapper.kt
│   │   └── DqnBatchPipeline.kt
│   ├── GameStateEncoder.kt          (ObservationEncoder, 334d)
│   ├── ActionFeatureEncoder.kt      (139d, DQN 内部)
│   ├── ActionDecoder.kt             (Q→Decision, DQN 内部)
│   ├── OnnxInferenceEngine.kt       (ORT + 维度校验)
│   ├── OnnxModel.kt                 (包装器 + batch 工具)
│   └── OnnxAIPlayerController.kt    (变体驱动 + 二次校验)
└── AIFactory.kt                     ← 工厂入口
```

| 层 | 目录 | 职责 |
|---|---|---|
| 框架层 | `ai/base/variant/` | 模型无关接口，不引用 ONNX |
| 管道层 | `ai/onnx/pipeline/` | 推理流程实现，可被变体复用 |
| 变体层 | `ai/onnx/variant/` | 每种模型一个 object |

---

## 4. FAQ

**Q: 如何验证新模型能被正确加载？**
OnnxInferenceEngine 初始化时用 ModelIoContract 校验维度，不匹配抛 IllegalStateException。Logcat tag = `OnnxInferenceEngine`。

**Q: 模型文件放哪里？**
`app/src/main/assets/models/`。`AIFactory.preloadModels()` 通过 AssetCopier 复制到私有目录。变体的 `modelFileName` 对应此目录下的文件名。

**Q: 能否同一局用不同模型？**
可以。每个座位独立创建 OnnxAIPlayerController，传入不同 variant。

**Q: DifficultyConfig 和模型有关吗？**
无关。影响采样策略（温度/探索率/TopK/犯错率），与模型结构解耦。

**Q: 模型加载/出牌失败怎么办？**
两层保护：(1) AIFactory 加载失败 → 自动降级为 RuleBasedAIAdapter（isFallback=true）；(2) ONNX 出牌被 validateDecision() 拒绝 → 返回 Error → OnnxMatchViewModel 用独立 RuleBasedAIAdapter 重新决策。游戏不会中断。

**Q: ♦3 开局约束？**
ValidCombinationResolver 内置过滤，使用它的管道自动享受此约束。自行动作过滤的管道需自行处理。

**Q: OnnxModel 有哪些推理方法？**
- `predictActionValuesWithObs(obs, actionFeatures)` — 唯一公开方法，管道自行编码 obs 后传入
- internal 工具：`buildBatchedActionInput`（batch 拼接）、`alignActionValues`（输出对齐）

---

## 5. 相关文档

- `docs/架构文档.md` — 项目整体架构
- `docs/AI出牌策略文档.md` — 规则型 AI 策略说明
- `AGENTS.md` — 项目结构、技术栈、AI 修改边界
