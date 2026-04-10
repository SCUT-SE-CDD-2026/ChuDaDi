package com.example.chudadi.controller.game

import com.example.chudadi.model.game.engine.GameEngine
import com.example.chudadi.model.game.fixture.MatchFixtureFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class MatchUiStateMapperTest {
    private val engine = GameEngine(random = Random(0))

    @Test
    fun map_debugHandsKeepsEntriesWhenDisplayNamesDuplicate() {
        val seats = MatchFixtureFactory.defaultSeats().map { seat ->
            if (seat.seatId == 1 || seat.seatId == 2) {
                seat.copy(displayName = "AI Same")
            } else {
                seat
            }
        }
        val match = MatchFixtureFactory.localMatch(activeSeatIndex = 0, seats = seats)
        val mapper = MatchUiStateMapper(engine = engine, enableDebugHands = true)

        val uiState = mapper.map(
            match = match,
            selectedCardIds = emptySet(),
            lastActionMessage = null,
            localSeatId = 0,
        )

        assertEquals(3, uiState.debugOpponentHands.size)
        assertEquals(setOf(1, 2, 3), uiState.debugOpponentHands.map { it.seatId }.toSet())
        assertEquals(2, uiState.debugOpponentHands.count { it.displayName == "AI Same" })
    }

    @Test
    fun map_debugHandsDisabledReturnsEmptyList() {
        val match = MatchFixtureFactory.localMatch(activeSeatIndex = 0)
        val mapper = MatchUiStateMapper(engine = engine, enableDebugHands = false)

        val uiState = mapper.map(
            match = match,
            selectedCardIds = emptySet(),
            lastActionMessage = null,
            localSeatId = 0,
        )

        assertTrue(uiState.debugOpponentHands.isEmpty())
    }
}
