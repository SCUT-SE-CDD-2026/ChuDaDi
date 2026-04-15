package com.example.chudadi.model.game.rule

data class GameRules(
    val ruleSet: GameRuleSet,
    val mustBeatIfPossible: Boolean,
) {
    companion object {
        fun forRuleSet(ruleSet: GameRuleSet): GameRules {
            return when (ruleSet) {
                GameRuleSet.SOUTHERN ->
                    GameRules(
                        ruleSet = ruleSet,
                        mustBeatIfPossible = false,
                    )

                GameRuleSet.NORTHERN ->
                    GameRules(
                        ruleSet = ruleSet,
                        mustBeatIfPossible = true,
                    )
            }
        }
    }
}
