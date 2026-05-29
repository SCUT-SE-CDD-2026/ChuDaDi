package com.example.chudadi.controller.game

import com.example.chudadi.model.game.engine.GameEngine
import com.example.chudadi.model.game.entity.Card
import com.example.chudadi.model.game.entity.CardRank
import com.example.chudadi.model.game.entity.CardSuit
import com.example.chudadi.model.game.entity.PlayCombination
import com.example.chudadi.model.game.entity.TrickState
import com.example.chudadi.model.game.fixture.MatchFixtureFactory
import com.example.chudadi.model.game.rule.CombinationType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class MatchUiStateMapperTest {

    // Shared mapper for tablePlayOrders tests (BT)
    private val mapper = MatchUiStateMapper(GameEngine())

    // Mapper with deterministic engine for ONNX debug-hands tests
    private val engine = GameEngine(random = Random(0))

    // -- ONNX tests: debugHands / observer mode --

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
        val debugMapper = MatchUiStateMapper(engine = engine, enableDebugHands = true)

        val uiState = debugMapper.map(
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
        val debugMapper = MatchUiStateMapper(engine = engine, enableDebugHands = false)

        val uiState = debugMapper.map(
            match = match,
            selectedCardIds = emptySet(),
            lastActionMessage = null,
            localSeatId = 0,
        )

        assertTrue(uiState.debugOpponentHands.isEmpty())
    }

    @Test
    fun map_withoutLocalSeatTreatsAsObserverAndDoesNotExposeHumanTurn() {
        val match = MatchFixtureFactory.localMatch(activeSeatIndex = 0)
        val debugMapper = MatchUiStateMapper(engine = engine, enableDebugHands = true)

        val uiState = debugMapper.map(
            match = match,
            selectedCardIds = emptySet(),
            lastActionMessage = null,
            localSeatId = MatchUiStateMapper.NO_LOCAL_SEAT_ID,
        )

        assertTrue(uiState.playerHand.isEmpty())
        assertTrue(uiState.selectedCards.isEmpty())
        assertEquals(4, uiState.opponentSummaries.size)
        assertFalse(uiState.isHumanTurn)
        assertFalse(uiState.canSubmitPlay)
        assertFalse(uiState.canPass)
    }

    // -- BT tests: tablePlayOrders / stackOrder --

    @Test
    fun map_whenTablePlayOrdersExist_usesLatestPlayOrderInsteadOfLastWinningSeat() {
        val match = MatchFixtureFactory.localMatch().copy(
            trickState = trickState(
                lastWinningSeatIndex = 2,
                tablePlays = linkedMapOf(
                    1 to single(CardRank.THREE, CardSuit.CLUBS),
                    2 to single(CardRank.FOUR, CardSuit.HEARTS),
                    3 to single(CardRank.FIVE, CardSuit.SPADES),
                ),
                tablePlayOrders = mapOf(
                    1 to 3,
                    2 to 1,
                    3 to 2,
                ),
            ),
        )

        val tablePlays = mapper.map(match, selectedCardIds = emptySet(), lastActionMessage = null).tablePlays
        val latestPlay = tablePlays.single { it.ownerName == "AI 1" }
        val winningPlay = tablePlays.single { it.ownerName == "AI 2" }

        assertEquals(tablePlays.maxOf { it.stackOrder }, latestPlay.stackOrder)
        assertTrue(winningPlay.stackOrder < latestPlay.stackOrder)
        assertEquals(1, tablePlays.count { it.stackOrder == latestPlay.stackOrder })
    }

    @Test
    fun map_whenSeatReplacesPreviousTablePlay_usesUpdatedOrderAsTop() {
        val tablePlays = linkedMapOf(
            1 to single(CardRank.THREE, CardSuit.CLUBS),
            2 to single(CardRank.FOUR, CardSuit.HEARTS),
            3 to single(CardRank.FIVE, CardSuit.SPADES),
        )
        tablePlays[1] = single(CardRank.SIX, CardSuit.DIAMONDS)
        val match = MatchFixtureFactory.localMatch().copy(
            trickState = trickState(
                lastWinningSeatIndex = 1,
                tablePlays = tablePlays,
                tablePlayOrders = mapOf(
                    1 to 4,
                    2 to 2,
                    3 to 3,
                ),
            ),
        )

        val mappedTablePlays = mapper.map(match, selectedCardIds = emptySet(), lastActionMessage = null).tablePlays
        val replacedPlay = mappedTablePlays.single { it.ownerName == "AI 1" }

        assertEquals(mappedTablePlays.maxOf { it.stackOrder }, replacedPlay.stackOrder)
        assertTrue(
            mappedTablePlays
                .filterNot { it.ownerName == "AI 1" }
                .all { it.stackOrder < replacedPlay.stackOrder },
        )
    }

    @Test
    fun map_whenTablePlayOrderIsMissing_keepsMapIndexedStackOrder() {
        val match = MatchFixtureFactory.localMatch().copy(
            trickState = trickState(
                lastWinningSeatIndex = 3,
                tablePlays = linkedMapOf(
                    0 to single(CardRank.THREE, CardSuit.CLUBS),
                    2 to single(CardRank.FOUR, CardSuit.HEARTS),
                ),
            ),
        )

        val tablePlays = mapper.map(match, selectedCardIds = emptySet(), lastActionMessage = null).tablePlays

        assertEquals(2, tablePlays.size)
        assertEquals(0, tablePlays.single { it.ownerName == "You" }.stackOrder)
        assertEquals(1, tablePlays.single { it.ownerName == "AI 2" }.stackOrder)
    }

    // -- Helpers --

    private fun trickState(
        lastWinningSeatIndex: Int,
        tablePlays: Map<Int, PlayCombination>,
        tablePlayOrders: Map<Int, Int> = emptyMap(),
    ): TrickState {
        return TrickState(
            leadSeatIndex = 0,
            lastWinningSeatIndex = lastWinningSeatIndex,
            currentCombination = tablePlays[lastWinningSeatIndex],
            roundNumber = 1,
            tablePlays = tablePlays,
            tablePlayOrders = tablePlayOrders,
            nextTablePlayOrder = (tablePlayOrders.values.maxOrNull() ?: -1) + 1,
        )
    }

    private fun single(rank: CardRank, suit: CardSuit): PlayCombination {
        val card = Card(rank = rank, suit = suit)
        return PlayCombination(
            type = CombinationType.SINGLE,
            cards = listOf(card),
            primaryRank = rank.strength,
            primarySuit = suit.sortOrder,
        )
    }
}
