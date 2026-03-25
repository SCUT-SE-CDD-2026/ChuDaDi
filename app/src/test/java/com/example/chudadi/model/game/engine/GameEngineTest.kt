package com.example.chudadi.model.game.engine

import com.example.chudadi.model.game.entity.Card
import com.example.chudadi.model.game.entity.CardRank
import com.example.chudadi.model.game.entity.CardSuit
import com.example.chudadi.model.game.fixture.MatchFixtureFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class GameEngineTest {
    private val engine = GameEngine(random = Random(0))

    @Test
    fun startLocalMatch_dealsThirteenCardsAndPicksOpeningSeat() {
        val match = engine.startLocalMatch()

        assertEquals(4, match.seats.size)
        assertTrue(match.seats.all { it.hand.size == 13 })
        assertTrue(
            match.seats
                .first { it.seatId == match.activeSeatIndex }
                .hand
                .contains(Card(rank = CardRank.THREE, suit = CardSuit.DIAMONDS)),
        )
    }

    @Test
    fun submitSelectedCards_rejectsOpeningMoveWithoutDiamondThree() {
        val match = MatchFixtureFactory.localMatch(activeSeatIndex = 0)

        val result = engine.submitSelectedCards(
            match = match,
            seatIndex = 0,
            selectedCardIds = setOf(MatchFixtureFactory.card(CardRank.FOUR, CardSuit.CLUBS).id),
        )

        assertFalse(result.success)
        assertEquals(GameActionError.OPENING_MOVE_MUST_CONTAIN_DIAMOND_THREE, result.error)
        assertNull(result.message)
    }

    @Test
    fun submitSelectedCards_acceptsOpeningMoveWithDiamondThree() {
        val match = MatchFixtureFactory.localMatch(activeSeatIndex = 0)

        val result = engine.submitSelectedCards(
            match = match,
            seatIndex = 0,
            selectedCardIds = setOf(MatchFixtureFactory.card(CardRank.THREE, CardSuit.DIAMONDS).id),
        )

        assertTrue(result.success)
        assertNull(result.error)
        assertNotNull(result.match.trickState.currentCombination)
        assertEquals(2, result.match.seats.first { it.seatId == 0 }.hand.size)
    }
}
