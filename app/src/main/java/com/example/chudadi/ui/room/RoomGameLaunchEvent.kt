package com.example.chudadi.ui.room

import com.example.chudadi.controller.game.SeatConfig
import com.example.chudadi.model.game.rule.GameRuleSet

sealed interface RoomGameLaunchEvent {
    data class StartLocalMatch(
        val seatConfigs: List<SeatConfig>,
        val localSeatId: Int,
        val ruleSet: GameRuleSet,
        val aiMoveDelayMillis: Long = 0L,
        val aiPlaySpeed: AiPlaySpeed = AiPlaySpeed.NORMAL,
    ) : RoomGameLaunchEvent
}
