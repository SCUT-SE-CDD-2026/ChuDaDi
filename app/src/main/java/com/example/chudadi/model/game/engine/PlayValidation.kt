package com.example.chudadi.model.game.engine

import com.example.chudadi.model.game.entity.PlayCombination
import com.example.chudadi.model.game.entity.Seat
import com.example.chudadi.model.game.rule.CombinationEvaluator

sealed class PlayValidation {
    data class Valid(
        val seat: Seat,
        val candidate: PlayCombination,
        val evaluator: CombinationEvaluator,
    ) : PlayValidation()

    data class Invalid(val error: GameActionError) : PlayValidation()
}
