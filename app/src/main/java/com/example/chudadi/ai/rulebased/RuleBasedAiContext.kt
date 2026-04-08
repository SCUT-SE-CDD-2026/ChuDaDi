package com.example.chudadi.ai.rulebased

import com.example.chudadi.model.game.entity.Card
import com.example.chudadi.model.game.entity.Match
import com.example.chudadi.model.game.entity.PlayCombination
import com.example.chudadi.model.game.entity.Seat
import com.example.chudadi.model.game.rule.CombinationEvaluator
import com.example.chudadi.model.game.rule.GameRules

internal data class RuleBasedAiContext(
    val match: Match,
    val seatIndex: Int,
    val seat: Seat,
    val hand: List<Card>,
    val currentCombination: PlayCombination?,
    val evaluator: CombinationEvaluator,
    val rules: GameRules,
    val allCombinations: List<PlayCombination>,
    val handProfile: HandProfile,
)
