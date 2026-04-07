---
title: ai:strategy
description: Guidance for modifying, explaining, and extending this project's rule-based Big Two AI while preserving
    the existing southern/northern rule system and current strategy-object structure.
---

## AI 实现说明

本项目当前本地人机对战使用的是**规则型启发式 AI（Rule-Based Heuristic AI）**。
当前 AI 不是 ONNX 推理 AI，也不是强化学习 AI。

AI 相关代码位于：

- `app/src/main/java/com/example/chudadi/ai/rulebased/`

## 规则依赖说明

AI 决策建立在项目当前已实现的南北规则系统之上。
涉及 AI 行为时，应优先参考以下规则文档与规则实现：

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

- 当前项目已实现南方规则（`SOUTHERN`）与北方规则（`NORTHERN`）。
- AI 不应绕开 `GameRules`、`CombinationEvaluator` 或 `GameEngine` 单独定义一套规则。
- 若文档与代码实现存在差异，修改前应先确认当前项目阶段以哪一方为准。

## 当前 AI 结构

当前 AI 结构包括：

- `RuleBasedAiPlayer.kt`
    - AI 主入口与决策流程调度层
- `RuleBasedAiContext.kt`
    - AI 决策所需上下文
- `RuleBasedAiPolicies.kt`
    - 策略对象聚合
- `HandProfile.kt`
    - 手牌结构分析
- `RuleBasedAiScoringConstants.kt`
    - 启发式评分常量

策略层位于：

- `policy/CandidatePolicy.kt`
    - 候选生成与合法响应过滤
- `policy/TurnConstraintPolicy.kt`
    - 规则硬约束判断
- `policy/PassPolicy.kt`
    - 过牌策略判断
- `policy/SelectionPolicy.kt`
    - 高分候选的加权随机选择

评分层位于：

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

## 当前 AI 的实现特点

当前 AI 的决策流程可以概括为：

1. 读取当前规则集、桌面牌型、手牌与局面信息
2. 枚举当前手牌中的合法候选组合
3. 根据南北规则过滤出允许动作集合
4. 对候选动作进行启发式评分
5. 在规则允许的前提下判断是否过牌
6. 在高分候选中通过加权随机选择最终动作

当前 AI 的特点：

- 依赖现有南北规则系统进行合法性判断
- 先筛选规则允许的动作集合，再进行策略决策
- 对候选动作进行启发式评分，并结合过牌策略决定最终动作
- 在高分候选中通过加权随机进行选择，避免行为完全僵化
- 更偏规则型、结构保护型与资源保留型，而不是搜索型或学习型 AI

## 修改建议

- 修改 AI 时，应尽量保持当前的策略对象结构，不要将职责重新集中回单一类中。
- 修改 AI 行为时，应区分“规则约束”和“策略决策”：
    - 规则是否合法、是否允许过牌，属于规则层
    - 候选评分、是否主动过牌、最终动作选择，属于策略层
- 若后续扩展 ONNX AI、学习型 AI 或不同难度 AI，应优先复用现有规则层、上下文对象和策略边界。
