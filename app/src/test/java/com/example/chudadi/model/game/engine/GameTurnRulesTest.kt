package com.example.chudadi.model.game.engine

import com.example.chudadi.model.game.entity.CardRank
import com.example.chudadi.model.game.entity.CardSuit
import com.example.chudadi.model.game.entity.MatchPhase
import com.example.chudadi.model.game.fixture.MatchFixtureFactory
import com.example.chudadi.model.game.rule.CombinationEvaluator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameTurnRulesTest {
    private val engine = GameEngine()
    private val evaluator = CombinationEvaluator()

    @Test
    fun passTurn_rejectsLeadingPlayer() {
        val match = MatchFixtureFactory.localMatch(activeSeatIndex = 0)

        val result = engine.passTurn(match = match, seatIndex = 0)

        assertFalse(result.success)
        assertEquals(GameActionError.CANNOT_PASS_LEAD_TURN, result.error)
    }

    @Test
    fun submitSelectedCards_rejectsSmallerSingle() {
        val baseMatch = MatchFixtureFactory.localMatch(activeSeatIndex = 0)
        val currentCombination = evaluator.parse(
            listOf(MatchFixtureFactory.card(CardRank.FIVE, CardSuit.SPADES)),
        )
        val match = baseMatch.copy(
            trickState = baseMatch.trickState.copy(
                currentCombination = currentCombination,
                lastWinningSeatIndex = 1,
            ),
        )

        val result = engine.submitSelectedCards(
            match = match,
            seatIndex = 0,
            selectedCardIds = setOf(MatchFixtureFactory.card(CardRank.THREE, CardSuit.DIAMONDS).id),
        )

        assertFalse(result.success)
        assertEquals(GameActionError.PLAY_DOES_NOT_BEAT_CURRENT, result.error)
    }

    @Test
    fun passTurn_resetsRoundAfterAllOtherActiveSeatsPass() {
        val baseMatch = MatchFixtureFactory.localMatch(activeSeatIndex = 1)
        val currentCombination = evaluator.parse(
            listOf(MatchFixtureFactory.card(CardRank.FIVE, CardSuit.SPADES)),
        )
        val match = baseMatch.copy(
            trickState = baseMatch.trickState.copy(
                currentCombination = currentCombination,
                lastWinningSeatIndex = 0,
            ),
        )

        val afterFirstPass = engine.passTurn(match = match, seatIndex = 1).match
        val afterSecondPass = engine.passTurn(match = afterFirstPass, seatIndex = 2).match
        val afterThirdPass = engine.passTurn(match = afterSecondPass, seatIndex = 3).match

        assertEquals(MatchPhase.ROUND_RESET, afterThirdPass.phase)
        assertEquals(0, afterThirdPass.activeSeatIndex)
        assertNull(afterThirdPass.trickState.currentCombination)
        assertTrue(afterThirdPass.seats.filter { it.seatId != 0 }.all { it.status.name == "ACTIVE" })
    }

    @Test
    fun passTurn_clearsPassedSeatTablePlay_andShowsBuYaoMessage() {
        val baseMatch = MatchFixtureFactory.localMatch(activeSeatIndex = 1)
        val combination = evaluator.parse(
            listOf(MatchFixtureFactory.card(CardRank.FIVE, CardSuit.SPADES)),
        )!!
        val match = baseMatch.copy(
            trickState = baseMatch.trickState.copy(
                currentCombination = combination,
                lastWinningSeatIndex = 0,
                tablePlays = mapOf(
                    0 to combination,
                    1 to combination,
                ),
            ),
        )

        val result = engine.passTurn(match = match, seatIndex = 1)

        assertTrue(result.success)
        assertEquals("AI 1 要不起", result.message)
        assertFalse(result.match.trickState.tablePlays.containsKey(1))
        assertTrue(result.match.trickState.tablePlays.containsKey(0))
    }
}
