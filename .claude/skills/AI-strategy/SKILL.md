## AI 实现说明

本项目当前本地人机对战使用的是**规则型启发式 AI（Rule-Based Heuristic AI）**。
当前 AI 不是 ONNX 推理 AI，也不是强化学习 AI。

AI 相关代码位于：

- `app/src/main/java/com/example/chudadi/ai/rulebased/`

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

当前 AI 的实现特点：

- 依赖现有南北规则系统进行合法性判断
- 先筛选规则允许的动作集合，再进行策略决策
- 对候选动作进行启发式评分，并结合过牌策略决定最终动作
- 最终在高分候选中通过加权随机进行选择，避免行为完全僵化

协作建议：

- 修改 AI 前，应先阅读南北规则文档与规则实现代码。
- 修改 AI 时，应尽量保持当前的策略对象结构，不要将职责重新集中回单一类中。
- 若后续扩展 ONNX AI、学习型 AI 或不同难度 AI，应优先复用现有规则层、上下文对象和策略边界。
