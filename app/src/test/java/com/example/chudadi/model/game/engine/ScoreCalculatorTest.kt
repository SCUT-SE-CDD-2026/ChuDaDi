package com.example.chudadi.model.game.engine

import com.example.chudadi.model.game.entity.Card
import com.example.chudadi.model.game.entity.CardRank
import com.example.chudadi.model.game.entity.CardSuit
import com.example.chudadi.model.game.entity.PlayCombination
import com.example.chudadi.model.game.entity.RoundScore
import com.example.chudadi.model.game.entity.Seat
import com.example.chudadi.model.game.entity.SeatControllerType
import com.example.chudadi.model.game.entity.SeatStatus
import com.example.chudadi.model.game.fixture.MatchFixtureFactory
import com.example.chudadi.model.game.rule.CombinationType
import com.example.chudadi.model.game.rule.GameRuleSet
import org.junit.Assert.assertEquals
import org.junit.Test

@Suppress("MagicNumber")
class ScoreCalculatorTest {

    private fun seat(id: Int, cards: List<Card>) = Seat(
        seatId = id,
        displayName = "Player $id",
        controllerType = SeatControllerType.HUMAN,
        hand = cards,
        status = SeatStatus.FINISHED,
    )

    @Test
    fun southern_basicPenalty_equalsRemainingCards() {
        val seats = listOf(
            seat(0, emptyList()),
            seat(1, listOf(MatchFixtureFactory.card(CardRank.FOUR, CardSuit.CLUBS))),
            seat(2, listOf(MatchFixtureFactory.card(CardRank.FIVE, CardSuit.HEARTS))),
        )

        val scores = ScoreCalculator.calculateRoundScores(
            ruleSet = GameRuleSet.SOUTHERN,
            seats = seats,
            winnerSeatId = 0,
            winningCombination = null,
        )

        assertEquals(2, scoreOf(scores, 0))
        assertEquals(-1, scoreOf(scores, 1))
        assertEquals(-1, scoreOf(scores, 2))
    }

    @Test
    fun southern_hasTwoInHand_doublesPenalty() {
        val seats = listOf(
            seat(0, emptyList()),
            seat(1, listOf(MatchFixtureFactory.card(CardRank.TWO, CardSuit.DIAMONDS))),
        )

        val scores = ScoreCalculator.calculateRoundScores(
            ruleSet = GameRuleSet.SOUTHERN,
            seats = seats,
            winnerSeatId = 0,
            winningCombination = null,
        )

        assertEquals(-2, scoreOf(scores, 1))
        assertEquals(2, scoreOf(scores, 0))
    }

    @Test
    fun southern_spadeTwoInWinningPlay_doublesAllPenalties() {
        val winningCombination = PlayCombination(
            type = CombinationType.SINGLE,
            cards = listOf(MatchFixtureFactory.card(CardRank.TWO, CardSuit.SPADES)),
            primaryRank = CardRank.TWO.strength,
            primarySuit = CardSuit.SPADES.sortOrder,
        )
        val seats = listOf(
            seat(0, emptyList()),
            seat(1, listOf(MatchFixtureFactory.card(CardRank.FOUR, CardSuit.CLUBS))),
            seat(2, listOf(MatchFixtureFactory.card(CardRank.FIVE, CardSuit.HEARTS))),
        )

        val scores = ScoreCalculator.calculateRoundScores(
            ruleSet = GameRuleSet.SOUTHERN,
            seats = seats,
            winnerSeatId = 0,
            winningCombination = winningCombination,
        )

        assertEquals(-2, scoreOf(scores, 1))
        assertEquals(-2, scoreOf(scores, 2))
        assertEquals(4, scoreOf(scores, 0))
    }

    @Test
    fun northern_fullHandPenalty_is52() {
        val fullHand = List(13) { MatchFixtureFactory.card(CardRank.THREE, CardSuit.DIAMONDS) }
        val seats = listOf(
            seat(0, emptyList()),
            seat(1, fullHand),
        )

        val scores = ScoreCalculator.calculateRoundScores(
            ruleSet = GameRuleSet.NORTHERN,
            seats = seats,
            winnerSeatId = 0,
            winningCombination = null,
        )

        assertEquals(-52, scoreOf(scores, 1))
        assertEquals(52, scoreOf(scores, 0))
    }

    @Test
    fun northern_threshold10_orMore_doublesPenalty() {
        val seats = listOf(
            seat(0, emptyList()),
            seat(1, List(10) { MatchFixtureFactory.card(CardRank.FOUR, CardSuit.CLUBS) }),
        )

        val scores = ScoreCalculator.calculateRoundScores(
            ruleSet = GameRuleSet.NORTHERN,
            seats = seats,
            winnerSeatId = 0,
            winningCombination = null,
        )

        assertEquals(-20, scoreOf(scores, 1))
        assertEquals(20, scoreOf(scores, 0))
    }

    @Test
    fun northern_belowThreshold_noDouble() {
        val seats = listOf(
            seat(0, emptyList()),
            seat(1, List(9) { MatchFixtureFactory.card(CardRank.FOUR, CardSuit.CLUBS) }),
        )

        val scores = ScoreCalculator.calculateRoundScores(
            ruleSet = GameRuleSet.NORTHERN,
            seats = seats,
            winnerSeatId = 0,
            winningCombination = null,
        )

        assertEquals(-9, scoreOf(scores, 1))
        assertEquals(9, scoreOf(scores, 0))
    }

    @Test
    fun baopei_baopeiSeatPaysAllOthersPayZero() {
        val seats = listOf(
            seat(0, emptyList()),
            seat(1, listOf(MatchFixtureFactory.card(CardRank.FOUR, CardSuit.CLUBS))),
            seat(2, listOf(MatchFixtureFactory.card(CardRank.FIVE, CardSuit.HEARTS))),
            seat(3, listOf(MatchFixtureFactory.card(CardRank.SIX, CardSuit.SPADES))),
        )

        val scores = ScoreCalculator.calculateRoundScores(
            ruleSet = GameRuleSet.SOUTHERN,
            seats = seats,
            winnerSeatId = 0,
            winningCombination = null,
            baopeiSeatId = 2,
        )

        assertEquals(3, scoreOf(scores, 0))
        assertEquals(0, scoreOf(scores, 1))
        assertEquals(-3, scoreOf(scores, 2))
        assertEquals(0, scoreOf(scores, 3))
    }

    @Test(expected = IllegalArgumentException::class)
    fun baopei_baopeiEqualsWinner_throws() {
        val seats = listOf(
            seat(0, emptyList()),
            seat(1, listOf(MatchFixtureFactory.card(CardRank.FOUR, CardSuit.CLUBS))),
            seat(2, listOf(MatchFixtureFactory.card(CardRank.FIVE, CardSuit.HEARTS))),
        )

        ScoreCalculator.calculateRoundScores(
            ruleSet = GameRuleSet.SOUTHERN,
            seats = seats,
            winnerSeatId = 0,
            winningCombination = null,
            baopeiSeatId = 0,
        )
    }

    @Test
    fun southern_doubleMultipliers_stackMultiplicatively() {
        val winningCombination = PlayCombination(
            type = CombinationType.SINGLE,
            cards = listOf(MatchFixtureFactory.card(CardRank.TWO, CardSuit.SPADES)),
            primaryRank = CardRank.TWO.strength,
            primarySuit = CardSuit.SPADES.sortOrder,
        )
        val seats = listOf(
            seat(0, emptyList()),
            seat(1, listOf(MatchFixtureFactory.card(CardRank.TWO, CardSuit.DIAMONDS))),
        )

        val scores = ScoreCalculator.calculateRoundScores(
            ruleSet = GameRuleSet.SOUTHERN,
            seats = seats,
            winnerSeatId = 0,
            winningCombination = winningCombination,
        )

        // basePenalty = 1, holdingTwo doubles to 2, spadeTwo doubles to 4
        assertEquals(-4, scoreOf(scores, 1))
        assertEquals(4, scoreOf(scores, 0))
    }

    @Test
    fun southern_baopeiAndSpadeTwo_doublesTotalAfterBaopei() {
        val winningCombination = PlayCombination(
            type = CombinationType.SINGLE,
            cards = listOf(MatchFixtureFactory.card(CardRank.TWO, CardSuit.SPADES)),
            primaryRank = CardRank.TWO.strength,
            primarySuit = CardSuit.SPADES.sortOrder,
        )
        val seats = listOf(
            seat(0, emptyList()),
            seat(1, listOf(MatchFixtureFactory.card(CardRank.FOUR, CardSuit.CLUBS))),
            seat(2, listOf(MatchFixtureFactory.card(CardRank.FIVE, CardSuit.HEARTS))),
            seat(3, listOf(MatchFixtureFactory.card(CardRank.SIX, CardSuit.SPADES))),
        )

        val scores = ScoreCalculator.calculateRoundScores(
            ruleSet = GameRuleSet.SOUTHERN,
            seats = seats,
            winnerSeatId = 0,
            winningCombination = winningCombination,
            baopeiSeatId = 2,
        )

        // totalPenalty = (1+1+1)*2 = 6, baopei seat 2 pays all 6
        assertEquals(6, scoreOf(scores, 0))
        assertEquals(0, scoreOf(scores, 1))
        assertEquals(-6, scoreOf(scores, 2))
        assertEquals(0, scoreOf(scores, 3))
    }

    @Test
    fun northern_baopei_appliesCorrectly() {
        val seats = listOf(
            seat(0, emptyList()),
            seat(1, List(10) { MatchFixtureFactory.card(CardRank.FOUR, CardSuit.CLUBS) }),
            seat(2, List(5) { MatchFixtureFactory.card(CardRank.FIVE, CardSuit.HEARTS) }),
        )

        val scores = ScoreCalculator.calculateRoundScores(
            ruleSet = GameRuleSet.NORTHERN,
            seats = seats,
            winnerSeatId = 0,
            winningCombination = null,
            baopeiSeatId = 1,
        )

        // totalPenalty = 20 + 5 = 25, baopei seat 1 pays all
        assertEquals(25, scoreOf(scores, 0))
        assertEquals(-25, scoreOf(scores, 1))
        assertEquals(0, scoreOf(scores, 2))
    }

    @Test(expected = IllegalArgumentException::class)
    fun emptySeats_throws() {
        ScoreCalculator.calculateRoundScores(
            ruleSet = GameRuleSet.SOUTHERN,
            seats = emptyList(),
            winnerSeatId = 0,
            winningCombination = null,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidWinnerSeatId_throws() {
        val seats = listOf(seat(0, emptyList()))
        ScoreCalculator.calculateRoundScores(
            ruleSet = GameRuleSet.SOUTHERN,
            seats = seats,
            winnerSeatId = 99,
            winningCombination = null,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidBaopeiSeatId_throws() {
        val seats = listOf(
            seat(0, emptyList()),
            seat(1, listOf(MatchFixtureFactory.card(CardRank.FOUR, CardSuit.CLUBS))),
        )
        ScoreCalculator.calculateRoundScores(
            ruleSet = GameRuleSet.SOUTHERN,
            seats = seats,
            winnerSeatId = 0,
            winningCombination = null,
            baopeiSeatId = 99,
        )
    }

    // -- ONNX-adapted tests: score conservation checks with new API --

    @Test
    fun calculateRoundScores_winnerEqualsSumOfLoserDeductions_southern() {
        val seats = listOf(
            seat(0, emptyList()),
            seat(1, List(4) { MatchFixtureFactory.card(CardRank.FOUR, CardSuit.CLUBS) }),
            seat(2, List(10) { MatchFixtureFactory.card(CardRank.FIVE, CardSuit.HEARTS) }),
            seat(3, List(13) { MatchFixtureFactory.card(CardRank.SIX, CardSuit.SPADES) }),
        )

        val scores = ScoreCalculator.calculateRoundScores(
            ruleSet = GameRuleSet.SOUTHERN,
            seats = seats,
            winnerSeatId = 0,
            winningCombination = null,
        )

        assertConservedScore(scores, winnerSeatId = 0)
    }

    @Test
    fun calculateRoundScores_winnerEqualsSumOfLoserDeductions_northern() {
        val seats = listOf(
            seat(0, emptyList()),
            seat(1, List(4) { MatchFixtureFactory.card(CardRank.FOUR, CardSuit.CLUBS) }),
            seat(2, List(10) { MatchFixtureFactory.card(CardRank.FIVE, CardSuit.HEARTS) }),
            seat(3, List(13) { MatchFixtureFactory.card(CardRank.SIX, CardSuit.SPADES) }),
        )

        val scores = ScoreCalculator.calculateRoundScores(
            ruleSet = GameRuleSet.NORTHERN,
            seats = seats,
            winnerSeatId = 0,
            winningCombination = null,
        )

        assertConservedScore(scores, winnerSeatId = 0)
    }

    @Test
    fun calculateRoundScores_withSpadeTwo_winnerEqualsSumOfLoserDeductions() {
        val winningCombination = PlayCombination(
            type = CombinationType.SINGLE,
            cards = listOf(MatchFixtureFactory.card(CardRank.TWO, CardSuit.SPADES)),
            primaryRank = CardRank.TWO.strength,
            primarySuit = CardSuit.SPADES.sortOrder,
        )
        val seats = listOf(
            seat(0, emptyList()),
            seat(1, List(4) { MatchFixtureFactory.card(CardRank.FOUR, CardSuit.CLUBS) }),
            seat(2, List(10) { MatchFixtureFactory.card(CardRank.FIVE, CardSuit.HEARTS) }),
        )

        val scores = ScoreCalculator.calculateRoundScores(
            ruleSet = GameRuleSet.SOUTHERN,
            seats = seats,
            winnerSeatId = 0,
            winningCombination = winningCombination,
        )

        assertConservedScore(scores, winnerSeatId = 0)
    }

    private fun scoreOf(scores: List<RoundScore>, seatId: Int): Int {
        return scores.first { it.seatId == seatId }.roundScore
    }

    private fun assertConservedScore(
        scores: List<RoundScore>,
        winnerSeatId: Int,
    ) {
        val winner = scores.first { it.seatId == winnerSeatId }
        val loserDeduction = scores.filterNot { it.seatId == winnerSeatId }.sumOf { -it.roundScore }
        assertEquals(loserDeduction, winner.roundScore)
    }
}
