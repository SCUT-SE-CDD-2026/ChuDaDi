package com.example.chudadi.model.game.rule

data class GameRules(
    val ruleSet: GameRuleSet,
    val bombCanInterruptAnyCombination: Boolean,
    val bombRequiresNoSameTypeResponse: Boolean,
    val mustBeatIfPossible: Boolean,
    val fourOfAKindBombEnabled: Boolean,
    val fourWithOneIsBomb: Boolean,
    val fourWithTwoEnabled: Boolean,
    val fourWithTwoIsBomb: Boolean,
    val straightFlushIsBomb: Boolean,
) {
    fun isBomb(type: CombinationType): Boolean {
        return when (type) {
            CombinationType.FOUR_OF_A_KIND_BOMB -> fourOfAKindBombEnabled
            CombinationType.FOUR_WITH_ONE -> fourWithOneIsBomb
            CombinationType.STRAIGHT_FLUSH -> straightFlushIsBomb
            CombinationType.FOUR_WITH_TWO -> fourWithTwoIsBomb
            else -> false
        }
    }

    companion object {
        fun forRuleSet(ruleSet: GameRuleSet): GameRules {
            return when (ruleSet) {
                GameRuleSet.SOUTHERN ->
                    GameRules(
                        ruleSet = ruleSet,
                        bombCanInterruptAnyCombination = true,
                        bombRequiresNoSameTypeResponse = false,
                        mustBeatIfPossible = false,
                        fourOfAKindBombEnabled = true,
                        fourWithOneIsBomb = true,
                        fourWithTwoEnabled = true,
                        fourWithTwoIsBomb = true,
                        straightFlushIsBomb = false,
                    )

                GameRuleSet.NORTHERN ->
                    GameRules(
                        ruleSet = ruleSet,
                        bombCanInterruptAnyCombination = false,
                        bombRequiresNoSameTypeResponse = true,
                        mustBeatIfPossible = true,
                        fourOfAKindBombEnabled = true,
                        fourWithOneIsBomb = false,
                        fourWithTwoEnabled = false,
                        fourWithTwoIsBomb = false,
                        straightFlushIsBomb = false,
                    )
            }
        }
    }
}
