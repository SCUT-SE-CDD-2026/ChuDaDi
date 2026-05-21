package com.example.chudadi.controller.server

import com.example.chudadi.ai.rulebased.RuleBasedAiPlayer
import com.example.chudadi.controller.game.MatchUiStateMapper
import com.example.chudadi.model.game.engine.GameEngine
import com.example.chudadi.model.game.entity.Match
import com.example.chudadi.model.game.entity.SeatControllerType
import com.example.chudadi.model.game.fixture.MatchFixtureFactory
import com.example.chudadi.model.game.rule.GameRuleSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class BluetoothAuthoritativeMatchControllerTest {
    @Test
    fun hostMatchController_whenOldTimerFires_ignoresStaleTurnTimeout() {
        val controller = createController()
        controller.startMatch(seatConfigs = seatConfigs(), ruleSet = GameRuleSet.SOUTHERN)
        val oldExpectedTurn = requireNotNull(controller.currentTurnSnapshot())

        val playResult = controller.handlePlayRequest(
            seatId = 0,
            selectedCardIds = setOf("THREE_DIAMONDS"),
            expectedTurn = oldExpectedTurn,
        )
        val matchAfterPlayerAction = requireNotNull(controller.currentMatch())

        val staleTimeoutResult = controller.handlePassRequest(
            seatId = oldExpectedTurn.activeSeatIndex,
            expectedTurn = oldExpectedTurn,
        )

        assertTrue(playResult.success)
        assertFalse(controller.isCurrentTurnExpired(oldExpectedTurn, nowMillis = Long.MAX_VALUE))
        assertSame(matchAfterPlayerAction, controller.currentMatch())
        assertEmptyEnvelope(staleTimeoutResult)
    }

    @Test
    fun hostMatchController_whenOldAiResultReturns_ignoresStaleTurn() {
        lateinit var controller: BluetoothAuthoritativeMatchController
        lateinit var oldExpectedTurn: AuthoritativeTurnSnapshot
        lateinit var matchAfterPlayerAction: Match
        var aiDecisionStarted = false
        val engine = FixtureGameEngine()
        val aiActionResolver = AiActionResolver { match, seatId, actionEngine ->
            aiDecisionStarted = true
            val playResult = controller.handlePlayRequest(
                seatId = 0,
                selectedCardIds = setOf("THREE_DIAMONDS"),
                expectedTurn = oldExpectedTurn,
            )
            assertTrue(playResult.success)
            matchAfterPlayerAction = requireNotNull(controller.currentMatch())
            actionEngine.submitSelectedCards(
                match = match,
                seatIndex = seatId,
                selectedCardIds = setOf("THREE_DIAMONDS"),
            )
        }
        controller = BluetoothAuthoritativeMatchController(
            engine = engine,
            mapper = MatchUiStateMapper(engine),
            aiActionResolver = aiActionResolver,
        )
        controller.startMatch(seatConfigs = seatConfigs(), ruleSet = GameRuleSet.SOUTHERN)
        oldExpectedTurn = requireNotNull(controller.currentTurnSnapshot())

        val staleAiResult = controller.resolveCurrentSeatByAi(
            lastMessage = "旧 AI 结果",
            expectedTurn = oldExpectedTurn,
        )

        assertTrue(aiDecisionStarted)
        assertSame(matchAfterPlayerAction, controller.currentMatch())
        assertEmptyEnvelope(staleAiResult)
    }

    private fun createController(): BluetoothAuthoritativeMatchController {
        val engine = FixtureGameEngine()
        return BluetoothAuthoritativeMatchController(
            engine = engine,
            mapper = MatchUiStateMapper(engine),
            aiPlayer = RuleBasedAiPlayer(),
        )
    }

    private fun seatConfigs(): List<Triple<Int, String, SeatControllerType>> {
        return listOf(
            Triple(0, "You", SeatControllerType.HUMAN),
            Triple(1, "AI 1", SeatControllerType.RULE_BASED_AI),
            Triple(2, "AI 2", SeatControllerType.RULE_BASED_AI),
            Triple(3, "AI 3", SeatControllerType.RULE_BASED_AI),
        )
    }

    private fun assertEmptyEnvelope(envelope: MatchActionEnvelope) {
        assertNull(envelope.match)
        assertNull(envelope.message)
        assertFalse(envelope.success)
        assertEquals(emptyList<Any>(), envelope.roundScores)
    }

    private class FixtureGameEngine : GameEngine() {
        override fun startLocalMatch(
            ruleSet: GameRuleSet,
            seatConfigs: List<Triple<Int, String, SeatControllerType>>,
        ): Match {
            return MatchFixtureFactory.localMatch(ruleSet = ruleSet)
        }
    }
}
