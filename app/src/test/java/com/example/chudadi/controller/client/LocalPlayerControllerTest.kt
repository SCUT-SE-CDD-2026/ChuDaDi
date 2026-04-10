package com.example.chudadi.controller.client

import com.example.chudadi.ai.rulebased.RuleBasedAiPlayer
import com.example.chudadi.controller.game.MatchUiStateMapper
import com.example.chudadi.controller.server.LocalAuthoritativeController
import com.example.chudadi.model.game.engine.GameEngine
import com.example.chudadi.model.game.entity.Match
import com.example.chudadi.model.game.entity.MatchPhase
import com.example.chudadi.model.game.entity.SeatControllerType
import com.example.chudadi.model.game.fixture.MatchFixtureFactory
import com.example.chudadi.model.game.rule.GameRuleSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalPlayerControllerTest {
    @Test
    fun startLocalMatch_forwardsSelectedRuleSetToEngine() {
        val engine = CapturingGameEngine()
        val controller = LocalPlayerController(
            serverController = LocalAuthoritativeController(engine),
            aiPlayer = RuleBasedAiPlayer(),
            mapper = MatchUiStateMapper(engine),
            scope = CoroutineScope(Dispatchers.Unconfined),
        )
        val seatConfigs = listOf(
            Triple(0, "You", SeatControllerType.HUMAN),
            Triple(1, "AI 1", SeatControllerType.RULE_BASED_AI),
            Triple(2, "AI 2", SeatControllerType.RULE_BASED_AI),
            Triple(3, "AI 3", SeatControllerType.RULE_BASED_AI),
        )

        controller.onRequestStartLocalMatch(
            seatConfigs = seatConfigs,
            localSeatId = 0,
            ruleSet = GameRuleSet.NORTHERN,
        )

        assertEquals(GameRuleSet.NORTHERN, engine.capturedRuleSet)
        assertEquals(MatchPhase.PLAYER_TURN, controller.uiState.value.phase)
    }

    private class CapturingGameEngine : GameEngine() {
        var capturedRuleSet: GameRuleSet? = null

        override fun startLocalMatch(ruleSet: GameRuleSet): Match {
            capturedRuleSet = ruleSet
            return MatchFixtureFactory.localMatch(ruleSet = ruleSet)
        }

        override fun startLocalMatch(
            ruleSet: GameRuleSet,
            seatConfigs: List<Triple<Int, String, SeatControllerType>>,
        ): Match {
            capturedRuleSet = ruleSet
            return MatchFixtureFactory.localMatch(ruleSet = ruleSet)
        }
    }
}
