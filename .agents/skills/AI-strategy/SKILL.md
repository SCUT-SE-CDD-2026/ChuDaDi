---
title: AI-strategy
description: Guidance for modifying, explaining, and extending this project's Big Two AI (Rule-based + ONNX RL) while preserving the southern/northern rule system, engine authority, and current controller abstraction.
---

## 目标

在不破坏现有规则体系与对局主循环的前提下，安全修改 AI 行为、扩展 AI 类型，或解释 AI 决策链路。

## 当前实现状态（以代码为准）

当前项目不是单一规则型 AI，而是“双轨 AI 架构”:

- 规则型 AI：`app/src/main/java/com/example/chudadi/ai/rulebased/`
- ONNX RL AI：`app/src/main/java/com/example/chudadi/ai/onnx/`
- 统一抽象：
  - `app/src/main/java/com/example/chudadi/ai/base/AIPlayerController.kt`
  - `app/src/main/java/com/example/chudadi/ai/base/AIDecision.kt`
  - `app/src/main/java/com/example/chudadi/ai/base/AIDifficulty.kt`
- 工厂与降级入口：`app/src/main/java/com/example/chudadi/ai/AIFactory.kt`
- 模型拷贝与路径管理：`app/src/main/java/com/example/chudadi/utils/AssetCopier.kt`
- ONNX 对局调度：`app/src/main/java/com/example/chudadi/controller/game/OnnxMatchViewModel.kt`

补充：`SeatControllerType` 已包含 `ONNX_RL_AI`，房间页可选择 `RULE_*` 与 `ONNX_*` 难度。

## 规则权威与边界

AI 修改必须遵守以下边界：

- 规则权威层是 `GameEngine` + `GameRules` + `CombinationEvaluator`，AI 不能绕过它们自定义一套判定。
- AI 只负责“提出动作”，最终合法性由服务端权威流（本地房主控制器）裁决。
- 任何 ONNX 输出都必须经过合法性验证；不合法时必须可降级到规则型 AI。
- 不要让 UI 或 ViewModel 直接改 `Match` 结构体字段，必须通过 command -> engine 流程。

关键规则文件：

- `app/src/main/java/com/example/chudadi/model/game/rule/GameRuleSet.kt`
- `app/src/main/java/com/example/chudadi/model/game/rule/GameRules.kt`
- `app/src/main/java/com/example/chudadi/model/game/rule/CombinationParser.kt`
- `app/src/main/java/com/example/chudadi/model/game/rule/CombinationComparator.kt`
- `app/src/main/java/com/example/chudadi/model/game/rule/CombinationEvaluator.kt`
- `app/src/main/java/com/example/chudadi/model/game/engine/GameEngine.kt`

## 调用链路（房间到对局）

1. 房间配置 AI 类型/难度：`ui/room/*`
2. 导航组装 `SeatConfig`：`navigation/ChuDaDiNavGraph.kt`
3. 对局分流：
   - 无 ONNX 座位 -> `LocalMatchViewModel`
   - 有 ONNX 座位 -> `OnnxMatchViewModel`
4. ONNX 分支由 `AIFactory.createAIPlayerWithStatus` 创建控制器并处理模型不可用时降级。
5. AI 决策最终通过 `PlayCardCommand/PassCommand` 走 `GameEngine` 判定。

## 规则型 AI 结构约束

规则型 AI 仍采用“策略对象 + 评分对象”结构，不应随意打平到单大类：

- 主流程与上下文：
  - `RuleBasedAiPlayer.kt`
  - `RuleBasedAiContext.kt`
  - `RuleBasedAiPolicies.kt`
- policy 层：`policy/`（候选生成、约束、过牌、选择）
- scoring 层：`scoring/`（领出/响应/惩罚/概率/调度）

修改建议：

- 规则判定变化优先改 rule/engine，不在 policy/scoring 里硬编码补丁。
- 评分参数调优优先改常量和 scorer，不改 turn constraint 的硬约束逻辑。

## ONNX AI 结构约束

ONNX 关键链路：

- 编码：`GameStateEncoder`
- 推理：`OnnxInferenceEngine` / `OnnxModel`
- 解码：`ActionDecoder`
- 控制器：`OnnxAIPlayerController`

修改 ONNX 逻辑时必须同时检查：

- 输入维度与模型输入名是否匹配。
- 解码动作是否与 `validActions` 一致。
- `Pass` 行为是否满足南北方规则。
- 推理失败、超时、非法输出时是否有可靠 fallback。
- 资源释放是否安全（session 生命周期）。

## 常见任务指南

### 1) 调整 AI 难度体验

- 规则型：优先改 `RuleBasedAiScoringConstants.kt` / scorer 权重。
- ONNX：优先改 `DifficultyConfig.kt`（temperature、explorationRate、topK、mistakeProbability）。
- 不要直接在 ViewModel 里塞随机行为逻辑。

### 2) 新增 AI 类型

建议复用现有抽象：

- 新增 `AIPlayerController` 实现。
- 在 `AIFactory` 增加创建与状态返回逻辑。
- 扩展 `SeatControllerType` 与房间选择 UI。
- 在导航层保证 `SeatConfig` 能正确映射到控制器类型。

### 3) 修复 AI 卡局/异常

优先排查：

- AI 动作被 `GameEngine` 拒绝后的处理是否继续推进。
- fallback 后是否还能提交合法 command。
- `MAX_AI_CHAIN` 是否导致提前退出。

## 提交前检查清单

至少执行：

- `./gradlew.bat :app:compileDebugKotlin --no-daemon --console=plain`
- `./gradlew.bat test --no-daemon --console=plain`

若改了 AI 核心逻辑，建议补测试：

- 规则型：policy/scoring 行为测试。
- ONNX：解码合法性、fallback、错误恢复测试。
- 对局：AI 回合推进与结束条件回归测试。

## 文档维护要求

每次改动以下任一内容时，应同步更新本技能文档：

- AI 架构分层（base/rulebased/onnx）
- AI 创建与降级机制（`AIFactory`）
- 房间到对局的控制器映射路径
- 关键约束（规则权威、fallback、资源释放）
