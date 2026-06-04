package com.example.chudadi.ai.onnx

import com.example.chudadi.model.game.entity.CardRank
import com.example.chudadi.model.game.entity.CardSuit
import com.example.chudadi.model.game.fixture.MatchFixtureFactory
import com.example.chudadi.model.game.rule.GameRuleSet
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class V2HistoryEncoderTest {

    @Test
    fun gameStateEncoderV2_removesCumulativeHistoryAndKeepsRuleFlag() {
        val match = MatchFixtureFactory.localMatch(ruleSet = GameRuleSet.NORTHERN)
        val v1 = GameStateEncoder().encode(match, seatIndex = 0, ruleSet = GameRuleSet.NORTHERN)
        val v2 = GameStateEncoderV2().encode(match, seatIndex = 0, ruleSet = GameRuleSet.NORTHERN)

        assertEquals(GameStateEncoderV2.INPUT_DIM, v2.size)
        assertArrayEquals(v1.copyOfRange(0, HISTORY_START), v2.copyOfRange(0, HISTORY_START), 0f)
        assertArrayEquals(v1.copyOfRange(HISTORY_END, v1.size), v2.copyOfRange(HISTORY_START, v2.size), 0f)
        assertEquals(1f, v2.last(), 0f)
    }

    @Test
    fun playedHistorySequenceEncoder_padsOldestRowsAndUsesRelativeSeatOrder() {
        val seatOneCard = MatchFixtureFactory.card(CardRank.SIX, CardSuit.SPADES)
        val seatThreeCard = MatchFixtureFactory.card(CardRank.EIGHT, CardSuit.CLUBS)
        val match = MatchFixtureFactory.localMatch().copy(
            trickState = MatchFixtureFactory.localMatch().trickState.copy(
                playedActionHistory = mapOf(
                    1 to listOf(listOf(seatOneCard)),
                    3 to listOf(listOf(seatThreeCard)),
                ),
            ),
        )

        val history = PlayedHistorySequenceEncoder(historyLen = 2).encode(match, seatIndex = 0)

        assertEquals(3 * 2 * 52, history.size)
        val nextPlayerLastRow = 52
        assertEquals(1f, history[nextPlayerLastRow + GameStateEncoder.cardToIndex(seatOneCard)], 0f)
        val previousPlayerBase = (2 * 2 + 1) * 52
        assertEquals(1f, history[previousPlayerBase + GameStateEncoder.cardToIndex(seatThreeCard)], 0f)
        assertEquals(0f, history[GameStateEncoder.cardToIndex(seatOneCard)], 0f)
    }

    private companion object {
        const val HISTORY_START = 172
        const val HISTORY_END = HISTORY_START + 156
    }
}
