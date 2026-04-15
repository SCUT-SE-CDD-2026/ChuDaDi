package com.example.chudadi.model.game.engine

import com.example.chudadi.model.game.entity.Card
import com.example.chudadi.model.game.entity.CardRank
import com.example.chudadi.model.game.entity.CardSuit
import com.example.chudadi.model.game.entity.MatchPhase
import com.example.chudadi.model.game.entity.Seat
import com.example.chudadi.model.game.entity.SeatControllerType
import com.example.chudadi.model.game.entity.SeatStatus
import com.example.chudadi.model.game.fixture.MatchFixtureFactory
import com.example.chudadi.model.game.rule.CombinationEvaluator
import com.example.chudadi.model.game.rule.GameRuleSet
import com.example.chudadi.model.game.rule.GameRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameTurnRulesTest {
    private val engine = GameEngine()
    private val evaluator = CombinationEvaluator()
    private val southernEvaluator = CombinationEvaluator(GameRules.forRuleSet(GameRuleSet.SOUTHERN))
    private val northernEvaluator = CombinationEvaluator(GameRules.forRuleSet(GameRuleSet.NORTHERN))

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
        assertTrue(afterThirdPass.seats.filter { it.seatId != 0 }.all { it.status == SeatStatus.ACTIVE })
    }

    @Test
    fun passTurn_clearsPassedSeatTablePlay_andShowsPassMessage() {
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
        assertEquals("AI 1 passed", result.message)
        assertFalse(result.match.trickState.tablePlays.containsKey(1))
        assertTrue(result.match.trickState.tablePlays.containsKey(0))
    }

    @Test
    fun fourOfAKind_isNotAValidPlay() {
        val combination = southernEvaluator.parse(
            listOf(
                MatchFixtureFactory.card(CardRank.FIVE, CardSuit.DIAMONDS),
                MatchFixtureFactory.card(CardRank.FIVE, CardSuit.CLUBS),
                MatchFixtureFactory.card(CardRank.FIVE, CardSuit.HEARTS),
                MatchFixtureFactory.card(CardRank.FIVE, CardSuit.SPADES),
            ),
        )

        assertNull(combination)
    }

    @Test
    fun fourWithTwo_isNotAValidPlay() {
        val combination = southernEvaluator.parse(
            listOf(
                MatchFixtureFactory.card(CardRank.FIVE, CardSuit.DIAMONDS),
                MatchFixtureFactory.card(CardRank.FIVE, CardSuit.CLUBS),
                MatchFixtureFactory.card(CardRank.FIVE, CardSuit.HEARTS),
                MatchFixtureFactory.card(CardRank.FIVE, CardSuit.SPADES),
                MatchFixtureFactory.card(CardRank.THREE, CardSuit.CLUBS),
                MatchFixtureFactory.card(CardRank.FOUR, CardSuit.HEARTS),
            ),
        )

        assertNull(combination)
    }

    @Test
    fun fourWithOne_isRecognizedAsFiveCardType() {
        val combination = southernEvaluator.parse(
            listOf(
                MatchFixtureFactory.card(CardRank.FIVE, CardSuit.DIAMONDS),
                MatchFixtureFactory.card(CardRank.FIVE, CardSuit.CLUBS),
                MatchFixtureFactory.card(CardRank.FIVE, CardSuit.HEARTS),
                MatchFixtureFactory.card(CardRank.FIVE, CardSuit.SPADES),
                MatchFixtureFactory.card(CardRank.THREE, CardSuit.CLUBS),
            ),
        )!!

        assertEquals("FOUR_WITH_ONE", combination.type.name)
        assertEquals(CardRank.FIVE.strength, combination.primaryRank)
    }

    @Test
    fun southernFiveCardTypes_cannotBeatAcrossTypes() {
        val current = southernEvaluator.parse(
            listOf(
                MatchFixtureFactory.card(CardRank.THREE, CardSuit.DIAMONDS),
                MatchFixtureFactory.card(CardRank.FOUR, CardSuit.CLUBS),
                MatchFixtureFactory.card(CardRank.FIVE, CardSuit.HEARTS),
                MatchFixtureFactory.card(CardRank.SIX, CardSuit.SPADES),
                MatchFixtureFactory.card(CardRank.SEVEN, CardSuit.DIAMONDS),
            ),
        )!!
        val flush = southernEvaluator.parse(
            listOf(
                MatchFixtureFactory.card(CardRank.THREE, CardSuit.SPADES),
                MatchFixtureFactory.card(CardRank.FIVE, CardSuit.SPADES),
                MatchFixtureFactory.card(CardRank.SEVEN, CardSuit.SPADES),
                MatchFixtureFactory.card(CardRank.NINE, CardSuit.SPADES),
                MatchFixtureFactory.card(CardRank.JACK, CardSuit.SPADES),
            ),
        )!!

        assertFalse(southernEvaluator.canBeat(flush, current))
    }

    @Test
    fun southernFourWithOne_canBeatNonStraightFlushFiveCardType() {
        val current = southernEvaluator.parse(
            listOf(
                MatchFixtureFactory.card(CardRank.THREE, CardSuit.DIAMONDS),
                MatchFixtureFactory.card(CardRank.FOUR, CardSuit.DIAMONDS),
                MatchFixtureFactory.card(CardRank.FIVE, CardSuit.DIAMONDS),
                MatchFixtureFactory.card(CardRank.SIX, CardSuit.HEARTS),
                MatchFixtureFactory.card(CardRank.SEVEN, CardSuit.HEARTS),
            ),
        )!!
        val ironBranch = southernEvaluator.parse(
            listOf(
                MatchFixtureFactory.card(CardRank.NINE, CardSuit.DIAMONDS),
                MatchFixtureFactory.card(CardRank.NINE, CardSuit.CLUBS),
                MatchFixtureFactory.card(CardRank.NINE, CardSuit.HEARTS),
                MatchFixtureFactory.card(CardRank.NINE, CardSuit.SPADES),
                MatchFixtureFactory.card(CardRank.THREE, CardSuit.CLUBS),
            ),
        )!!

        assertTrue(southernEvaluator.canBeat(ironBranch, current))
    }

    @Test
    fun southernFourWithOne_cannotBeatStraightFlush() {
        val current = southernEvaluator.parse(
            listOf(
                MatchFixtureFactory.card(CardRank.FIVE, CardSuit.SPADES),
                MatchFixtureFactory.card(CardRank.SIX, CardSuit.SPADES),
                MatchFixtureFactory.card(CardRank.SEVEN, CardSuit.SPADES),
                MatchFixtureFactory.card(CardRank.EIGHT, CardSuit.SPADES),
                MatchFixtureFactory.card(CardRank.NINE, CardSuit.SPADES),
            ),
        )!!
        val ironBranch = southernEvaluator.parse(
            listOf(
                MatchFixtureFactory.card(CardRank.TEN, CardSuit.DIAMONDS),
                MatchFixtureFactory.card(CardRank.TEN, CardSuit.CLUBS),
                MatchFixtureFactory.card(CardRank.TEN, CardSuit.HEARTS),
                MatchFixtureFactory.card(CardRank.TEN, CardSuit.SPADES),
                MatchFixtureFactory.card(CardRank.THREE, CardSuit.CLUBS),
            ),
        )!!

        assertFalse(southernEvaluator.canBeat(ironBranch, current))
    }

    @Test
    fun northernFiveCardTypes_canBeatAcrossTypesByPower() {
        val current = northernEvaluator.parse(
            listOf(
                MatchFixtureFactory.card(CardRank.THREE, CardSuit.DIAMONDS),
                MatchFixtureFactory.card(CardRank.FOUR, CardSuit.CLUBS),
                MatchFixtureFactory.card(CardRank.FIVE, CardSuit.HEARTS),
                MatchFixtureFactory.card(CardRank.SIX, CardSuit.SPADES),
                MatchFixtureFactory.card(CardRank.SEVEN, CardSuit.DIAMONDS),
            ),
        )!!
        val flush = northernEvaluator.parse(
            listOf(
                MatchFixtureFactory.card(CardRank.THREE, CardSuit.SPADES),
                MatchFixtureFactory.card(CardRank.FIVE, CardSuit.SPADES),
                MatchFixtureFactory.card(CardRank.SEVEN, CardSuit.SPADES),
                MatchFixtureFactory.card(CardRank.NINE, CardSuit.SPADES),
                MatchFixtureFactory.card(CardRank.JACK, CardSuit.SPADES),
            ),
        )!!

        assertTrue(northernEvaluator.canBeat(flush, current))
    }

    @Test
    fun flushComparison_usesHighestRankThenSuitOnly() {
        val strongerBySuit = southernEvaluator.parse(
            listOf(
                MatchFixtureFactory.card(CardRank.ACE, CardSuit.SPADES),
                MatchFixtureFactory.card(CardRank.NINE, CardSuit.SPADES),
                MatchFixtureFactory.card(CardRank.SEVEN, CardSuit.SPADES),
                MatchFixtureFactory.card(CardRank.FIVE, CardSuit.SPADES),
                MatchFixtureFactory.card(CardRank.THREE, CardSuit.SPADES),
            ),
        )!!
        val weakerBySuit = southernEvaluator.parse(
            listOf(
                MatchFixtureFactory.card(CardRank.ACE, CardSuit.HEARTS),
                MatchFixtureFactory.card(CardRank.KING, CardSuit.HEARTS),
                MatchFixtureFactory.card(CardRank.QUEEN, CardSuit.HEARTS),
                MatchFixtureFactory.card(CardRank.JACK, CardSuit.HEARTS),
                MatchFixtureFactory.card(CardRank.NINE, CardSuit.HEARTS),
            ),
        )!!

        assertTrue(southernEvaluator.canBeat(strongerBySuit, weakerBySuit))
    }

    @Test
    fun northernPassTurn_rejectsWhenSameTypeBeatOptionExists() {
        val northernEngine = GameEngine(defaultRuleSet = GameRuleSet.NORTHERN)
        val currentCombination = northernEvaluator.parse(
            listOf(MatchFixtureFactory.card(CardRank.FIVE, CardSuit.SPADES)),
        )
        val seats = listOf(
            seat(
                0,
                "You",
                listOf(MatchFixtureFactory.card(CardRank.THREE, CardSuit.DIAMONDS)),
                SeatControllerType.HUMAN,
            ),
            seat(
                1,
                "AI 1",
                listOf(
                    MatchFixtureFactory.card(CardRank.SIX, CardSuit.CLUBS),
                    MatchFixtureFactory.card(CardRank.NINE, CardSuit.HEARTS),
                ),
            ),
            seat(2, "AI 2", listOf(MatchFixtureFactory.card(CardRank.SEVEN, CardSuit.DIAMONDS))),
            seat(3, "AI 3", listOf(MatchFixtureFactory.card(CardRank.EIGHT, CardSuit.CLUBS))),
        )
        val baseMatch = MatchFixtureFactory.localMatch(
            activeSeatIndex = 1,
            seats = seats,
            ruleSet = GameRuleSet.NORTHERN,
        )
        val match = baseMatch.copy(
            trickState = baseMatch.trickState.copy(
                currentCombination = currentCombination,
                lastWinningSeatIndex = 0,
            ),
        )

        val result = northernEngine.passTurn(match = match, seatIndex = 1)

        assertFalse(result.success)
        assertEquals(GameActionError.MUST_BEAT_IF_POSSIBLE, result.error)
    }

    @Test
    fun northernPassTurn_allowsPassWhenOnlyCrossTypeFiveCardBeatExists() {
        val northernEngine = GameEngine(defaultRuleSet = GameRuleSet.NORTHERN)
        val currentCombination = northernEvaluator.parse(
            listOf(
                MatchFixtureFactory.card(CardRank.THREE, CardSuit.DIAMONDS),
                MatchFixtureFactory.card(CardRank.FOUR, CardSuit.CLUBS),
                MatchFixtureFactory.card(CardRank.FIVE, CardSuit.HEARTS),
                MatchFixtureFactory.card(CardRank.SIX, CardSuit.SPADES),
                MatchFixtureFactory.card(CardRank.SEVEN, CardSuit.DIAMONDS),
            ),
        )!!
        val seats = listOf(
            seat(
                0,
                "You",
                listOf(MatchFixtureFactory.card(CardRank.THREE, CardSuit.CLUBS)),
                SeatControllerType.HUMAN,
            ),
            seat(
                1,
                "AI 1",
                listOf(
                    MatchFixtureFactory.card(CardRank.THREE, CardSuit.SPADES),
                    MatchFixtureFactory.card(CardRank.FIVE, CardSuit.SPADES),
                    MatchFixtureFactory.card(CardRank.SEVEN, CardSuit.SPADES),
                    MatchFixtureFactory.card(CardRank.NINE, CardSuit.SPADES),
                    MatchFixtureFactory.card(CardRank.JACK, CardSuit.SPADES),
                ),
            ),
            seat(2, "AI 2", listOf(MatchFixtureFactory.card(CardRank.EIGHT, CardSuit.DIAMONDS))),
            seat(3, "AI 3", listOf(MatchFixtureFactory.card(CardRank.NINE, CardSuit.CLUBS))),
        )
        val baseMatch = MatchFixtureFactory.localMatch(
            activeSeatIndex = 1,
            seats = seats,
            ruleSet = GameRuleSet.NORTHERN,
        )

        val result = northernEngine.passTurn(
            match = baseMatch.copy(
                trickState = baseMatch.trickState.copy(
                    currentCombination = currentCombination,
                    lastWinningSeatIndex = 0,
                ),
            ),
            seatIndex = 1,
        )

        assertTrue(result.success)
    }

    @Test
    fun submitSelectedCards_recordsBaoPayWhenPlayerDoesNotTopSingleAndNextSeatWins() {
        val southernEngine = GameEngine(defaultRuleSet = GameRuleSet.SOUTHERN)
        val currentCombination = southernEvaluator.parse(
            listOf(MatchFixtureFactory.card(CardRank.TEN, CardSuit.DIAMONDS)),
        )!!

        val seats = listOf(
            seat(
                0,
                "You",
                listOf(MatchFixtureFactory.card(CardRank.THREE, CardSuit.CLUBS)),
                SeatControllerType.HUMAN,
            ),
            seat(
                1,
                "AI 1",
                listOf(
                    MatchFixtureFactory.card(CardRank.JACK, CardSuit.CLUBS),
                    MatchFixtureFactory.card(CardRank.ACE, CardSuit.SPADES),
                ),
            ),
            seat(2, "AI 2", listOf(MatchFixtureFactory.card(CardRank.KING, CardSuit.HEARTS))),
            seat(3, "AI 3", listOf(MatchFixtureFactory.card(CardRank.SEVEN, CardSuit.DIAMONDS))),
        )

        val baseMatch = MatchFixtureFactory.localMatch(
            activeSeatIndex = 1,
            seats = seats,
            ruleSet = GameRuleSet.SOUTHERN,
        )
        val match = baseMatch.copy(
            trickState = baseMatch.trickState.copy(
                currentCombination = currentCombination,
                lastWinningSeatIndex = 0,
            ),
        )

        val firstPlay = southernEngine.submitSelectedCards(
            match = match,
            seatIndex = 1,
            selectedCardIds = setOf(MatchFixtureFactory.card(CardRank.JACK, CardSuit.CLUBS).id),
        )

        assertTrue(firstPlay.success)
        assertEquals(1, firstPlay.match.trickState.pendingBaoPaySeatId)
        assertEquals(2, firstPlay.match.trickState.pendingBaoPayProtectedSeatId)

        val secondPlay = southernEngine.submitSelectedCards(
            match = firstPlay.match,
            seatIndex = 2,
            selectedCardIds = setOf(MatchFixtureFactory.card(CardRank.KING, CardSuit.HEARTS).id),
        )

        assertTrue(secondPlay.success)
        assertEquals(MatchPhase.FINISHED, secondPlay.match.phase)

        val scores = secondPlay.match.result!!.scoreSummary.roundScores.associateBy { it.seatId }
        assertEquals(BAO_PAY_WINNER_SCORE, scores.getValue(2).roundScore)
        assertEquals(BAO_PAY_RESPONSIBLE_SCORE, scores.getValue(1).roundScore)
        assertEquals(ZERO_SCORE, scores.getValue(0).roundScore)
        assertEquals(ZERO_SCORE, scores.getValue(3).roundScore)
    }

    @Test
    fun passTurn_recordsBaoPayOnlyWhenPlayerCouldBeatSingle() {
        val southernEngine = GameEngine(defaultRuleSet = GameRuleSet.SOUTHERN)
        val currentCombination = southernEvaluator.parse(
            listOf(MatchFixtureFactory.card(CardRank.TEN, CardSuit.DIAMONDS)),
        )!!
        val canBeatSeats = listOf(
            seat(
                0,
                "You",
                listOf(MatchFixtureFactory.card(CardRank.THREE, CardSuit.CLUBS)),
                SeatControllerType.HUMAN,
            ),
            seat(
                1,
                "AI 1",
                listOf(MatchFixtureFactory.card(CardRank.JACK, CardSuit.CLUBS)),
            ),
            seat(2, "AI 2", listOf(MatchFixtureFactory.card(CardRank.KING, CardSuit.HEARTS))),
            seat(3, "AI 3", listOf(MatchFixtureFactory.card(CardRank.SEVEN, CardSuit.DIAMONDS))),
        )
        val cannotBeatSeats = listOf(
            seat(
                0,
                "You",
                listOf(MatchFixtureFactory.card(CardRank.THREE, CardSuit.CLUBS)),
                SeatControllerType.HUMAN,
            ),
            seat(
                1,
                "AI 1",
                listOf(MatchFixtureFactory.card(CardRank.NINE, CardSuit.CLUBS)),
            ),
            seat(2, "AI 2", listOf(MatchFixtureFactory.card(CardRank.KING, CardSuit.HEARTS))),
            seat(3, "AI 3", listOf(MatchFixtureFactory.card(CardRank.SEVEN, CardSuit.DIAMONDS))),
        )

        val canBeatMatch = MatchFixtureFactory.localMatch(
            activeSeatIndex = 1,
            seats = canBeatSeats,
            ruleSet = GameRuleSet.SOUTHERN,
        ).let { baseMatch ->
            baseMatch.copy(
                trickState = baseMatch.trickState.copy(
                    currentCombination = currentCombination,
                    lastWinningSeatIndex = 0,
                ),
            )
        }
        val cannotBeatMatch = MatchFixtureFactory.localMatch(
            activeSeatIndex = 1,
            seats = cannotBeatSeats,
            ruleSet = GameRuleSet.SOUTHERN,
        ).let { baseMatch ->
            baseMatch.copy(
                trickState = baseMatch.trickState.copy(
                    currentCombination = currentCombination,
                    lastWinningSeatIndex = 0,
                ),
            )
        }

        val canBeatResult = southernEngine.passTurn(match = canBeatMatch, seatIndex = 1)
        val cannotBeatResult = southernEngine.passTurn(match = cannotBeatMatch, seatIndex = 1)

        assertTrue(canBeatResult.success)
        assertEquals(1, canBeatResult.match.trickState.pendingBaoPaySeatId)
        assertEquals(2, canBeatResult.match.trickState.pendingBaoPayProtectedSeatId)
        assertTrue(cannotBeatResult.success)
        assertEquals(null, cannotBeatResult.match.trickState.pendingBaoPaySeatId)
        assertEquals(null, cannotBeatResult.match.trickState.pendingBaoPayProtectedSeatId)
    }

    private fun seat(
        seatId: Int,
        name: String,
        cards: List<Card>,
        controllerType: SeatControllerType = SeatControllerType.RULE_BASED_AI,
    ): Seat {
        return Seat(
            seatId = seatId,
            displayName = name,
            controllerType = controllerType,
            hand = cards.sortedWith(Card.gameComparator),
            status = SeatStatus.ACTIVE,
        )
    }

    private companion object {
        const val BAO_PAY_WINNER_SCORE = 3
        const val BAO_PAY_RESPONSIBLE_SCORE = -3
        const val ZERO_SCORE = 0
    }
}
