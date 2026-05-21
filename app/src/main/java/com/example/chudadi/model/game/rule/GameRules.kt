package com.example.chudadi.model.game.rule

data class GameRules(
    val ruleSet: GameRuleSet,
    val mustBeatIfPossible: Boolean,
    val crossTypeFiveCardAllowed: Boolean,
    val fourWithOneIsBomb: Boolean,
    val straightFlushIsBomb: Boolean,
    val bombCanInterruptAnyCombination: Boolean,
    val bombRequiresNoSameTypeResponse: Boolean,
) {
    fun isBomb(type: CombinationType): Boolean {
        return when (type) {
            CombinationType.FOUR_WITH_ONE -> fourWithOneIsBomb
            CombinationType.STRAIGHT_FLUSH -> straightFlushIsBomb
            else -> false
        }
    }

    companion object {
        fun forRuleSet(ruleSet: GameRuleSet): GameRules {
            return when (ruleSet) {
                GameRuleSet.SOUTHERN ->
                    GameRules(
                        ruleSet = ruleSet,
                        mustBeatIfPossible = false,
                        crossTypeFiveCardAllowed = false,
                        fourWithOneIsBomb = true,
                        straightFlushIsBomb = true,
                        bombCanInterruptAnyCombination = true,
                        bombRequiresNoSameTypeResponse = false,
                    )

                GameRuleSet.NORTHERN ->
                    GameRules(
                        ruleSet = ruleSet,
                        // 北方玩法习惯：有大必出（mustBeatIfPossible）。
                        // 规则文档虽未显式区分南北在此处的差异，但该设定也是的常见约束，已作为设计决策保留。
                        mustBeatIfPossible = true,
                        crossTypeFiveCardAllowed = true,
                        fourWithOneIsBomb = false,
                        straightFlushIsBomb = false,
                        bombCanInterruptAnyCombination = false,
                        bombRequiresNoSameTypeResponse = true,
                    )
            }
        }
    }
}
