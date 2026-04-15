package com.example.chudadi.model.game.engine

import com.example.chudadi.model.game.entity.CardRank
import com.example.chudadi.model.game.entity.CardSuit
import com.example.chudadi.model.game.entity.Seat
import com.example.chudadi.model.game.entity.SeatControllerType
import com.example.chudadi.model.game.entity.SeatStatus
import com.example.chudadi.model.game.fixture.MatchFixtureFactory
import com.example.chudadi.model.game.rule.GameRuleSet
import org.junit.Assert.assertEquals
import org.junit.Test

class ScoreCalculatorTest {
    @Test
    fun southernScore_doublesPenaltyWhenLoserStillHasTwo() {
        val seats = listOf(
            Seat(0, "Winner", SeatControllerType.HUMAN, emptyList(), SeatStatus.FINISHED),
            Seat(
                1,
                "A",
                SeatControllerType.RULE_BASED_AI,
                listOf(
                    MatchFixtureFactory.card(CardRank.TWO, CardSuit.SPADES),
                    MatchFixtureFactory.card(CardRank.FIVE, CardSuit.CLUBS),
                ),
            ),
            Seat(
                2,
                "B",
                SeatControllerType.RULE_BASED_AI,
                listOf(MatchFixtureFactory.card(CardRank.SEVEN, CardSuit.DIAMONDS)),
            ),
            Seat(
                3,
                "C",
                SeatControllerType.RULE_BASED_AI,
                listOf(MatchFixtureFactory.card(CardRank.NINE, CardSuit.HEARTS)),
            ),
        )

        val scores = ScoreCalculator.calculateRoundScores(
            ruleSet = GameRuleSet.SOUTHERN,
            seats = seats,
            winnerSeatId = 0,
        ).associateBy { it.seatId }

        assertEquals(SOUTHERN_WINNER_SCORE, scores.getValue(0).roundScore)
        assertEquals(SOUTHERN_TWO_PENALTY, scores.getValue(1).roundScore)
        assertEquals(SOUTHERN_SINGLE_PENALTY, scores.getValue(2).roundScore)
        assertEquals(SOUTHERN_SINGLE_PENALTY, scores.getValue(3).roundScore)
    }

    @Test
    fun baoPay_transfersAllLoserPenaltyToResponsibleSeat() {
        val seats = listOf(
            Seat(0, "Winner", SeatControllerType.HUMAN, emptyList(), SeatStatus.FINISHED),
            Seat(
                1,
                "Responsible",
                SeatControllerType.RULE_BASED_AI,
                listOf(
                    MatchFixtureFactory.card(CardRank.FIVE, CardSuit.CLUBS),
                    MatchFixtureFactory.card(CardRank.SIX, CardSuit.CLUBS),
                ),
            ),
            Seat(
                2,
                "B",
                SeatControllerType.RULE_BASED_AI,
                listOf(
                    MatchFixtureFactory.card(CardRank.SEVEN, CardSuit.DIAMONDS),
                    MatchFixtureFactory.card(CardRank.EIGHT, CardSuit.DIAMONDS),
                ),
            ),
            Seat(
                3,
                "C",
                SeatControllerType.RULE_BASED_AI,
                listOf(MatchFixtureFactory.card(CardRank.NINE, CardSuit.HEARTS)),
            ),
        )

        val scores = ScoreCalculator.calculateRoundScores(
            ruleSet = GameRuleSet.SOUTHERN,
            seats = seats,
            winnerSeatId = 0,
            baoPaySeatId = 1,
        ).associateBy { it.seatId }

        assertEquals(BAO_PAY_WINNER_SCORE, scores.getValue(0).roundScore)
        assertEquals(BAO_PAY_RESPONSIBLE_SCORE, scores.getValue(1).roundScore)
        assertEquals(NO_PENALTY_SCORE, scores.getValue(2).roundScore)
        assertEquals(NO_PENALTY_SCORE, scores.getValue(3).roundScore)
    }

    private companion object {
        const val SOUTHERN_WINNER_SCORE = 6
        const val SOUTHERN_TWO_PENALTY = -4
        const val SOUTHERN_SINGLE_PENALTY = -1
        const val BAO_PAY_WINNER_SCORE = 5
        const val BAO_PAY_RESPONSIBLE_SCORE = -5
        const val NO_PENALTY_SCORE = 0
    }
}
