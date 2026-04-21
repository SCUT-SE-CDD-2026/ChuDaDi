package com.example.chudadi.ui.room

import com.example.chudadi.model.game.entity.SeatControllerType
import com.example.chudadi.model.game.rule.GameRuleSet

sealed interface RoomGameLaunchEvent {
    data class StartLocalMatch(
        val seatConfigs: List<Triple<Int, String, SeatControllerType>>,
        val localSeatId: Int,
        val ruleSet: GameRuleSet,
    ) : RoomGameLaunchEvent
}
