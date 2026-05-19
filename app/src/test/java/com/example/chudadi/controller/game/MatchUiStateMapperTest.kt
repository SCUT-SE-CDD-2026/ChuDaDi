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
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchUiStateMapperTest {
    private val mapper = MatchUiStateMapper(GameEngine())

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

    private fun trickState(
        lastWinningSeatIndex: Int,
        tablePlays: Map<Int, PlayCombination>,
        tablePlayOrders: Map<Int, Int> = emptyMap(),
    ): TrickState {
        return TrickState(
            leadSeatIndex = 0,
            lastWinningSeatIndex = lastWinningSeatIndex,
            currentCombination = tablePlays[lastWinningSeatIndex],
            passCount = 0,
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
