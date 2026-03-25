package com.example.chudadi.network.protocol

import com.example.chudadi.model.game.engine.ActionResult
import com.example.chudadi.model.game.engine.GameEngine
import com.example.chudadi.model.game.entity.Match

data object PassCommand : GameCommand {
    override fun execute(
        match: Match,
        seatIndex: Int,
        engine: GameEngine,
    ): ActionResult {
        return engine.passTurn(
            match = match,
            seatIndex = seatIndex,
        )
    }
}
