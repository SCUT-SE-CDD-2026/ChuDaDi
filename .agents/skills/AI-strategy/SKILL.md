## AI 实现与修改约束

本项目当前本地人机对战使用的是**规则型启发式 AI（Rule-Based Heuristic AI）**，不是 ONNX 推理 AI 或强化学习 AI。

AI 相关代码位于：

- `app/src/main/java/com/example/chudadi/ai/rulebased/`

当前 AI 结构包括：

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

策略层位于：

- `policy/CandidatePolicy.kt`
    - 候选生成与合法响应过滤
- `policy/TurnConstraintPolicy.kt`
    - 规则硬约束判断
- `policy/PassPolicy.kt`
    - 是否过牌的策略判断
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

Agent 修改 AI 时应遵守以下约束：

- AI 必须依赖当前项目已实现的南北规则系统进行决策，不应绕开 `GameRules`、`CombinationEvaluator` 或 `GameEngine` 自行定
  义规则。
- 修改 AI 时，应优先保持“规则约束”和“策略决策”分离：
    - 规则是否合法、是否允许过牌，属于规则层
    - 候选评分、是否主动过牌、最终选择哪个动作，属于策略层
- 不应将候选生成、评分逻辑、过牌逻辑、随机选择逻辑重新堆回单一大类。
- 若涉及炸弹、过牌、首轮带方块 3、有牌必出、四带一、四带二等行为，必须先核对规则文档与规则实现。
- 修改 AI 后，至少应运行：
    - `./gradlew.bat detekt --no-daemon --console=plain`
    - `./gradlew.bat test --no-daemon --console=plain`
- 若后续扩展 ONNX AI、学习型 AI 或不同难度 AI，应尽量复用现有规则层与 AI 结构边界，而不是重复实现一套独立规则。
