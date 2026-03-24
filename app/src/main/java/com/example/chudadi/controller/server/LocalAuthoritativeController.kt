package com.example.chudadi.controller.server

import com.example.chudadi.model.game.engine.ActionResult
import com.example.chudadi.model.game.engine.GameEngine
import com.example.chudadi.model.game.entity.Match
import com.example.chudadi.network.protocol.GameCommand

class LocalAuthoritativeController(
    private val engine: GameEngine,
) {
    fun startLocalMatch(): Match = engine.startLocalMatch()

    fun handleCommand(
        match: Match,
        seatIndex: Int,
        command: GameCommand,
    ): ActionResult {
        return command.execute(
            match = match,
            seatIndex = seatIndex,
            engine = engine,
        )
    }

    fun canSubmitSelectedCards(
        match: Match?,
        seatIndex: Int,
        selectedCardIds: Set<String>,
    ): Boolean {
        return engine.canSubmitSelectedCards(
            match = match,
            seatIndex = seatIndex,
            selectedCardIds = selectedCardIds,
        )
    }

    fun canPass(
        match: Match?,
        seatIndex: Int,
    ): Boolean {
        return engine.canPass(
            match = match,
            seatIndex = seatIndex,
        )
    }
}
