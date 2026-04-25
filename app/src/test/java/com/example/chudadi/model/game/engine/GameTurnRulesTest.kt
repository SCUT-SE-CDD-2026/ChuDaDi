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
import com.example.chudadi.model.game.rule.CombinationType
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
        assertEquals(GameActionError.PLAY_TOO_SMALL, result.error)
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
    fun southernStraightFlush_canInterruptSingle() {
        val current = southernEvaluator.parse(
            listOf(MatchFixtureFactory.card(CardRank.ACE, CardSuit.SPADES)),
        )!!
        val bomb = southernEvaluator.parse(
            listOf(
                MatchFixtureFactory.card(CardRank.FOUR, CardSuit.CLUBS),
                MatchFixtureFactory.card(CardRank.FIVE, CardSuit.CLUBS),
                MatchFixtureFactory.card(CardRank.SIX, CardSuit.CLUBS),
                MatchFixtureFactory.card(CardRank.SEVEN, CardSuit.CLUBS),
                MatchFixtureFactory.card(CardRank.EIGHT, CardSuit.CLUBS),
            ),
        )!!

        assertTrue(southernEvaluator.canBeat(bomb, current))
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

        assertEquals(CombinationType.FOUR_WITH_ONE, combination.type)
        assertEquals(CardRank.FIVE.strength, combination.primaryRank)
    }

    @Test
    fun southernStraightFlush_canBeatFourWithOne() {
        val fourWithOne = southernEvaluator.parse(
            listOf(
                MatchFixtureFactory.card(CardRank.FIVE, CardSuit.DIAMONDS),
                MatchFixtureFactory.card(CardRank.FIVE, CardSuit.CLUBS),
                MatchFixtureFactory.card(CardRank.FIVE, CardSuit.HEARTS),
                MatchFixtureFactory.card(CardRank.FIVE, CardSuit.SPADES),
                MatchFixtureFactory.card(CardRank.THREE, CardSuit.CLUBS),
            ),
        )!!
        val straightFlush = southernEvaluator.parse(
            listOf(
                MatchFixtureFactory.card(CardRank.FOUR, CardSuit.CLUBS),
                MatchFixtureFactory.card(CardRank.FIVE, CardSuit.CLUBS),
                MatchFixtureFactory.card(CardRank.SIX, CardSuit.CLUBS),
                MatchFixtureFactory.card(CardRank.SEVEN, CardSuit.CLUBS),
                MatchFixtureFactory.card(CardRank.EIGHT, CardSuit.CLUBS),
            ),
        )!!

        assertTrue(southernEvaluator.canBeat(straightFlush, fourWithOne))
        assertFalse(southernEvaluator.canBeat(fourWithOne, straightFlush))
    }

    @Test
    fun northernFourWithOne_isValidButNotBomb() {
        val cards = listOf(
            MatchFixtureFactory.card(CardRank.FIVE, CardSuit.DIAMONDS),
            MatchFixtureFactory.card(CardRank.FIVE, CardSuit.CLUBS),
            MatchFixtureFactory.card(CardRank.FIVE, CardSuit.HEARTS),
            MatchFixtureFactory.card(CardRank.FIVE, CardSuit.SPADES),
            MatchFixtureFactory.card(CardRank.THREE, CardSuit.CLUBS),
        )
        val currentSingle = northernEvaluator.parse(
            listOf(MatchFixtureFactory.card(CardRank.ACE, CardSuit.SPADES)),
        )!!

        val combination = northernEvaluator.parse(cards)!!

        assertEquals(CombinationType.FOUR_WITH_ONE, combination.type)
        assertFalse(northernEvaluator.canBeat(combination, currentSingle))
    }

    @Test
    fun northernPassTurn_rejectsWhenBeatOptionExists() {
        val northernEngine = GameEngine(defaultRuleSet = GameRuleSet.NORTHERN)
        val currentCombination = northernEvaluator.parse(
            listOf(MatchFixtureFactory.card(CardRank.FIVE, CardSuit.SPADES)),
        )
        val seats = listOf(
            Seat(
                seatId = 0,
                displayName = "You",
                controllerType = SeatControllerType.HUMAN,
                hand = listOf(MatchFixtureFactory.card(CardRank.THREE, CardSuit.DIAMONDS)),
                status = SeatStatus.ACTIVE,
            ),
            Seat(
                seatId = 1,
                displayName = "AI 1",
                controllerType = SeatControllerType.RULE_BASED_AI,
                hand = listOf(
                    MatchFixtureFactory.card(CardRank.SIX, CardSuit.CLUBS),
                    MatchFixtureFactory.card(CardRank.NINE, CardSuit.HEARTS),
                ),
                status = SeatStatus.ACTIVE,
            ),
            Seat(
                seatId = 2,
                displayName = "AI 2",
                controllerType = SeatControllerType.RULE_BASED_AI,
                hand = listOf(MatchFixtureFactory.card(CardRank.SEVEN, CardSuit.DIAMONDS)),
                status = SeatStatus.ACTIVE,
            ),
            Seat(
                seatId = 3,
                displayName = "AI 3",
                controllerType = SeatControllerType.RULE_BASED_AI,
                hand = listOf(MatchFixtureFactory.card(CardRank.EIGHT, CardSuit.CLUBS)),
                status = SeatStatus.ACTIVE,
            ),
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
}
