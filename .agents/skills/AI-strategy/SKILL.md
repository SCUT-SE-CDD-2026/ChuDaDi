---
title: ai:strategy
description: Guidance for modifying, explaining, and extending this project's rule-based Big Two AI while preserving
    the existing southern/northern rule system and current strategy-object structure.
---

# AI Strategy

适用于本项目中与本地人机对战 AI 相关的任务，包括：

- `app/src/main/java/com/example/chudadi/ai/rulebased/` 下的代码修改
- AI 结构重构
- AI 决策策略说明整理
- AI 与南北规则关系梳理
- AI 测试与静态检查验证

不适用于：

- 纯 UI 设计与视觉改造
- 蓝牙联机逻辑
- 与 AI 无关的通用规则引擎重构
- ONNX 模型训练或外部模型推理实现

## 规则文档入口

涉及 AI 行为前，优先阅读以下规则文档与实现代码：

规则文档：

- `docs/游戏规则（中文版）.md`
- `docs/GameRule（English）.md`

规则实现：

- `app/src/main/java/com/example/chudadi/model/game/rule/GameRuleSet.kt`
- `app/src/main/java/com/example/chudadi/model/game/rule/GameRules.kt`
- `app/src/main/java/com/example/chudadi/model/game/rule/CombinationParser.kt`
- `app/src/main/java/com/example/chudadi/model/game/rule/CombinationComparator.kt`
- `app/src/main/java/com/example/chudadi/model/game/rule/CombinationEvaluator.kt`
- `app/src/main/java/com/example/chudadi/model/game/engine/GameEngine.kt`

说明：

- 本项目当前已实现南方规则（`SOUTHERN`）与北方规则（`NORTHERN`）。
- AI 必须依赖现有规则系统进行决策，不应绕开 `GameRules`、`CombinationEvaluator` 或 `GameEngine` 自行定义规则。
- 若涉及炸弹、过牌、首轮带方块 3、有牌必出、四带一、四带二等行为，必须先核对规则文档与规则实现。

## 当前 AI 实现结构

AI 相关代码位于：

- `app/src/main/java/com/example/chudadi/ai/rulebased/`

当前 AI 使用的是**规则型启发式 AI（Rule-Based Heuristic AI）**，不是 ONNX 推理 AI 或强化学习 AI。

核心结构包括：

- `RuleBasedAiPlayer.kt`
    - AI 主入口与流程调度层
- `RuleBasedAiContext.kt`
    - AI 决策上下文
- `RuleBasedAiPolicies.kt`
    - AI 策略对象聚合
- `HandProfile.kt`
    - 手牌结构分析
- `RuleBasedAiScoringConstants.kt`
    - 启发式评分常量

策略层：

- `policy/CandidatePolicy.kt`
    - 候选生成与合法响应过滤
- `policy/TurnConstraintPolicy.kt`
    - 规则硬约束判断
- `policy/PassPolicy.kt`
    - 是否过牌的策略判断
- `policy/SelectionPolicy.kt`
    - 高分候选的加权随机选择

评分层：

- `scoring/LeadScorer.kt`
    - 领出评分
- `scoring/ResponseScorer.kt`
    - 响应评分
- `scoring/PenaltyEvaluator.kt`
    - 拆牌惩罚、控牌损失、残局奖励等评估
- `scoring/PassProbabilityEvaluator.kt`
    - 过牌概率评估
- `scoring/ScoringPolicy.kt`
    - 评分调度入口

## 修改原则

1. 保持“规则约束”和“策略决策”分离。
2. 不要将候选生成、评分、过牌、随机选择重新堆回单一大类。
3. 优先复用现有策略对象边界，而不是重复实现相同逻辑。
4. 结构重构时尽量保持行为等价，尤其不要误伤南北规则差异。
5. 若修改 AI 行为，优先从策略层调整，而不是直接改规则层。
6. 若扩展不同难度 AI，应优先考虑替换策略对象，而不是复制整套 AI。

## 推荐工作流程

1. 先确认任务是否属于 AI 范畴，若涉及 AI 决策、过牌、评分、炸弹策略、结构重构，则使用本 skill。
2. 先阅读相关规则文档和规则实现，确认南北规则差异。
3. 再阅读 `ai/rulebased/` 当前结构，确认本次改动落在哪一层：
    - 候选层
    - 约束层
    - 过牌层
    - 选择层
    - 评分层
4. 若是结构重构，优先小步迁移，避免一次性重写。
    - `./gradlew.bat test --no-daemon --console=plain`

## 输出要求

当任务涉及 AI 说明或代码修改时，应优先说明：

- 当前 AI 属于哪种类型
- 当前结构边界是什么
- 本次修改落在哪一层
- 是否影响南北规则语义
- 是否已通过 `detekt` 和 `test`

## 常见误区

1. 把规则层判断直接写回 AI 主流程中。
2. 在 AI 内部绕开规则系统，自行实现一套合法性判断。
3. 修改响应逻辑时混淆“规则是否允许过牌”和“策略是否选择过牌”。
4. 为了快速改动，把评分、候选过滤、随机选择重新塞回 `RuleBasedAiPlayer`。
5. 修改 AI 后只看编译是否通过，不跑 `detekt` 和 `test`。
