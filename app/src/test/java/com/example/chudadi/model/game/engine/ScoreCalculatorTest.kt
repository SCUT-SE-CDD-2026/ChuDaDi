package com.example.chudadi.model.game.engine

import com.example.chudadi.model.game.entity.Card
import com.example.chudadi.model.game.entity.RoundScore
import com.example.chudadi.model.game.entity.Seat
import com.example.chudadi.model.game.entity.SeatControllerType
import com.example.chudadi.model.game.rule.GameRuleSet
import org.junit.Assert.assertEquals
import org.junit.Test

class ScoreCalculatorTest {
    @Test
    fun calculateRoundScores_southernAppliesBombMultiplierToLosers() {
        val seats = listOf(
            seat(seatId = 0, name = "You", remainingCards = 0),
            seat(seatId = 1, name = "AI 1", remainingCards = 3),
            seat(seatId = 2, name = "AI 2", remainingCards = 5),
            seat(seatId = 3, name = "AI 3", remainingCards = 8),
        )

        val scores = ScoreCalculator.calculateRoundScores(
            ruleSet = GameRuleSet.SOUTHERN,
            seats = seats,
            winnerSeatId = 0,
            totalBombCount = 2,
        )

        assertEquals(
            listOf(
                RoundScore(0, "You", 0, 64),
                RoundScore(1, "AI 1", 3, -12),
                RoundScore(2, "AI 2", 5, -20),
                RoundScore(3, "AI 3", 8, -32),
            ),
            scores,
        )
    }

    @Test
    fun calculateRoundScores_northernAppliesDoubleAndNoPlayPenalty() {
        val seats = listOf(
            seat(seatId = 0, name = "You", remainingCards = 0),
            seat(seatId = 1, name = "AI 1", remainingCards = 9),
            seat(seatId = 2, name = "AI 2", remainingCards = 10),
            seat(seatId = 3, name = "AI 3", remainingCards = 13),
        )

        val scores = ScoreCalculator.calculateRoundScores(
            ruleSet = GameRuleSet.NORTHERN,
            seats = seats,
            winnerSeatId = 0,
            totalBombCount = 99,
        )

        assertEquals(
            listOf(
                RoundScore(0, "You", 0, 68),
                RoundScore(1, "AI 1", 9, -9),
                RoundScore(2, "AI 2", 10, -20),
                RoundScore(3, "AI 3", 13, -39),
            ),
            scores,
        )
    }

    @Test
    fun calculateRoundScores_northernUsesTripleForCardCountAboveThirteen() {
        val seats = listOf(
            seat(seatId = 0, name = "You", remainingCards = 0),
            seat(seatId = 1, name = "AI 1", remainingCards = 14),
            seat(seatId = 2, name = "AI 2", remainingCards = 1),
            seat(seatId = 3, name = "AI 3", remainingCards = 2),
        )

        val scores = ScoreCalculator.calculateRoundScores(
            ruleSet = GameRuleSet.NORTHERN,
            seats = seats,
            winnerSeatId = 0,
            totalBombCount = 0,
        )

        assertEquals(-42, scores.first { it.seatId == 1 }.roundScore)
    }

    @Test
    fun calculateRoundScores_winnerEqualsSumOfLoserDeductions() {
        val seats = listOf(
            seat(seatId = 0, name = "You", remainingCards = 0),
            seat(seatId = 1, name = "AI 1", remainingCards = 4),
            seat(seatId = 2, name = "AI 2", remainingCards = 10),
            seat(seatId = 3, name = "AI 3", remainingCards = 13),
        )

        val southernScores = ScoreCalculator.calculateRoundScores(
            ruleSet = GameRuleSet.SOUTHERN,
            seats = seats,
            winnerSeatId = 0,
            totalBombCount = 1,
        )
        val northernScores = ScoreCalculator.calculateRoundScores(
            ruleSet = GameRuleSet.NORTHERN,
            seats = seats,
            winnerSeatId = 0,
            totalBombCount = 99,
        )

        assertConservedScore(southernScores, winnerSeatId = 0)
        assertConservedScore(northernScores, winnerSeatId = 0)
    }

    private fun seat(
        seatId: Int,
        name: String,
        remainingCards: Int,
    ): Seat {
        val hand = Card.standardDeck().take(remainingCards)
        return Seat(
            seatId = seatId,
            displayName = name,
            controllerType = SeatControllerType.RULE_BASED_AI,
            hand = hand,
        )
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
