package com.example.chudadi.ui.room

import com.example.chudadi.controller.game.SeatConfig
import com.example.chudadi.model.game.rule.GameRuleSet

sealed interface RoomGameLaunchEvent {
    data class StartLocalMatch(
        val seatConfigs: List<SeatConfig>,
        val localSeatId: Int,
        val ruleSet: GameRuleSet,
        val aiMoveDelayMillis: Long = DEFAULT_AI_MOVE_DELAY_MILLIS,
    ) : RoomGameLaunchEvent
}

private const val DEFAULT_AI_MOVE_DELAY_MILLIS = 450L
