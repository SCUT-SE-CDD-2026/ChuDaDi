package com.example.chudadi.model.game.engine

import com.example.chudadi.model.game.entity.CardRank
import com.example.chudadi.model.game.entity.CardSuit
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

class GameCombinationRulesTest {
    private val engine = GameEngine()
    private val southernEvaluator = CombinationEvaluator(GameRules.forRuleSet(GameRuleSet.SOUTHERN))
    private val northernEvaluator = CombinationEvaluator(GameRules.forRuleSet(GameRuleSet.NORTHERN))

    @Test
    fun northernPassTurn_allowsPassWhenNoValidResponse() {
        val northernEngine = GameEngine(defaultRuleSet = GameRuleSet.NORTHERN)
        val currentCombination = northernEvaluator.parse(
            listOf(MatchFixtureFactory.card(CardRank.ACE, CardSuit.SPADES)),
        )!!
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
                    MatchFixtureFactory.card(CardRank.FIVE, CardSuit.DIAMONDS),
                    MatchFixtureFactory.card(CardRank.FIVE, CardSuit.CLUBS),
                    MatchFixtureFactory.card(CardRank.FIVE, CardSuit.HEARTS),
                    MatchFixtureFactory.card(CardRank.FIVE, CardSuit.SPADES),
                    MatchFixtureFactory.card(CardRank.THREE, CardSuit.CLUBS),
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
    fun fiveCardCombinations_canBeatLowerTypePower() {
        val straight = southernEvaluator.parse(
            listOf(
                MatchFixtureFactory.card(CardRank.THREE, CardSuit.DIAMONDS),
                MatchFixtureFactory.card(CardRank.FOUR, CardSuit.CLUBS),
                MatchFixtureFactory.card(CardRank.FIVE, CardSuit.DIAMONDS),
                MatchFixtureFactory.card(CardRank.SIX, CardSuit.HEARTS),
                MatchFixtureFactory.card(CardRank.SEVEN, CardSuit.DIAMONDS),
            ),
        )!!
        val flush = southernEvaluator.parse(
            listOf(
                MatchFixtureFactory.card(CardRank.THREE, CardSuit.DIAMONDS),
                MatchFixtureFactory.card(CardRank.FIVE, CardSuit.DIAMONDS),
                MatchFixtureFactory.card(CardRank.SEVEN, CardSuit.DIAMONDS),
                MatchFixtureFactory.card(CardRank.NINE, CardSuit.DIAMONDS),
                MatchFixtureFactory.card(CardRank.JACK, CardSuit.DIAMONDS),
            ),
        )!!
        val fullHouse = southernEvaluator.parse(
            listOf(
                MatchFixtureFactory.card(CardRank.THREE, CardSuit.DIAMONDS),
                MatchFixtureFactory.card(CardRank.THREE, CardSuit.CLUBS),
                MatchFixtureFactory.card(CardRank.THREE, CardSuit.HEARTS),
                MatchFixtureFactory.card(CardRank.FOUR, CardSuit.DIAMONDS),
                MatchFixtureFactory.card(CardRank.FOUR, CardSuit.CLUBS),
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

        assertEquals(CombinationType.STRAIGHT, straight.type)
        assertEquals(CombinationType.FLUSH, flush.type)
        assertEquals(CombinationType.FULL_HOUSE, fullHouse.type)
        assertEquals(CombinationType.STRAIGHT_FLUSH, straightFlush.type)

        assertFalse(southernEvaluator.canBeat(flush, straight))
        assertFalse(southernEvaluator.canBeat(fullHouse, flush))
        assertTrue(southernEvaluator.canBeat(straightFlush, fullHouse))
        assertFalse(southernEvaluator.canBeat(straight, flush))
        assertFalse(southernEvaluator.canBeat(flush, fullHouse))
        assertFalse(southernEvaluator.canBeat(fullHouse, straightFlush))
    }

    @Test
    fun fiveCardCombinations_northernCanBeatLowerTypePower() {
        val straight = northernEvaluator.parse(
            listOf(
                MatchFixtureFactory.card(CardRank.THREE, CardSuit.DIAMONDS),
                MatchFixtureFactory.card(CardRank.FOUR, CardSuit.CLUBS),
                MatchFixtureFactory.card(CardRank.FIVE, CardSuit.DIAMONDS),
                MatchFixtureFactory.card(CardRank.SIX, CardSuit.HEARTS),
                MatchFixtureFactory.card(CardRank.SEVEN, CardSuit.DIAMONDS),
            ),
        )!!
        val flush = northernEvaluator.parse(
            listOf(
                MatchFixtureFactory.card(CardRank.THREE, CardSuit.DIAMONDS),
                MatchFixtureFactory.card(CardRank.FIVE, CardSuit.DIAMONDS),
                MatchFixtureFactory.card(CardRank.SEVEN, CardSuit.DIAMONDS),
                MatchFixtureFactory.card(CardRank.NINE, CardSuit.DIAMONDS),
                MatchFixtureFactory.card(CardRank.JACK, CardSuit.DIAMONDS),
            ),
        )!!
        val fullHouse = northernEvaluator.parse(
            listOf(
                MatchFixtureFactory.card(CardRank.THREE, CardSuit.DIAMONDS),
                MatchFixtureFactory.card(CardRank.THREE, CardSuit.CLUBS),
                MatchFixtureFactory.card(CardRank.THREE, CardSuit.HEARTS),
                MatchFixtureFactory.card(CardRank.FOUR, CardSuit.DIAMONDS),
                MatchFixtureFactory.card(CardRank.FOUR, CardSuit.CLUBS),
            ),
        )!!
        val straightFlush = northernEvaluator.parse(
            listOf(
                MatchFixtureFactory.card(CardRank.FOUR, CardSuit.CLUBS),
                MatchFixtureFactory.card(CardRank.FIVE, CardSuit.CLUBS),
                MatchFixtureFactory.card(CardRank.SIX, CardSuit.CLUBS),
                MatchFixtureFactory.card(CardRank.SEVEN, CardSuit.CLUBS),
                MatchFixtureFactory.card(CardRank.EIGHT, CardSuit.CLUBS),
            ),
        )!!

        assertTrue(northernEvaluator.canBeat(flush, straight))
        assertTrue(northernEvaluator.canBeat(fullHouse, flush))
        assertTrue(northernEvaluator.canBeat(straightFlush, fullHouse))
        assertFalse(northernEvaluator.canBeat(straight, flush))
        assertFalse(northernEvaluator.canBeat(flush, fullHouse))
        assertFalse(northernEvaluator.canBeat(fullHouse, straightFlush))
    }

    @Test
    fun fiveCardCombinations_sameTypePower_comparesPrimaryRank() {
        val lowerStraight = southernEvaluator.parse(
            listOf(
                MatchFixtureFactory.card(CardRank.THREE, CardSuit.DIAMONDS),
                MatchFixtureFactory.card(CardRank.FOUR, CardSuit.CLUBS),
                MatchFixtureFactory.card(CardRank.FIVE, CardSuit.HEARTS),
                MatchFixtureFactory.card(CardRank.SIX, CardSuit.SPADES),
                MatchFixtureFactory.card(CardRank.SEVEN, CardSuit.DIAMONDS),
            ),
        )!!
        val higherStraight = southernEvaluator.parse(
            listOf(
                MatchFixtureFactory.card(CardRank.FOUR, CardSuit.DIAMONDS),
                MatchFixtureFactory.card(CardRank.FIVE, CardSuit.CLUBS),
                MatchFixtureFactory.card(CardRank.SIX, CardSuit.HEARTS),
                MatchFixtureFactory.card(CardRank.SEVEN, CardSuit.SPADES),
                MatchFixtureFactory.card(CardRank.EIGHT, CardSuit.DIAMONDS),
            ),
        )!!

        assertTrue(southernEvaluator.canBeat(higherStraight, lowerStraight))
        assertFalse(southernEvaluator.canBeat(lowerStraight, higherStraight))
    }

    @Test
    fun northernPassTurn_rejectsWhenFlushCanBeatStraight() {
        val northernEngine = GameEngine(defaultRuleSet = GameRuleSet.NORTHERN)
        val straight = northernEvaluator.parse(
            listOf(
                MatchFixtureFactory.card(CardRank.THREE, CardSuit.DIAMONDS),
                MatchFixtureFactory.card(CardRank.FOUR, CardSuit.CLUBS),
                MatchFixtureFactory.card(CardRank.FIVE, CardSuit.DIAMONDS),
                MatchFixtureFactory.card(CardRank.SIX, CardSuit.HEARTS),
                MatchFixtureFactory.card(CardRank.SEVEN, CardSuit.DIAMONDS),
            ),
        )!!
        val seats = listOf(
            Seat(
                seatId = 0,
                displayName = "You",
                controllerType = SeatControllerType.HUMAN,
                hand = listOf(MatchFixtureFactory.card(CardRank.TWO, CardSuit.DIAMONDS)),
                status = SeatStatus.ACTIVE,
            ),
            Seat(
                seatId = 1,
                displayName = "AI 1",
                controllerType = SeatControllerType.RULE_BASED_AI,
                hand = listOf(
                    MatchFixtureFactory.card(CardRank.THREE, CardSuit.HEARTS),
                    MatchFixtureFactory.card(CardRank.FIVE, CardSuit.HEARTS),
                    MatchFixtureFactory.card(CardRank.SEVEN, CardSuit.HEARTS),
                    MatchFixtureFactory.card(CardRank.NINE, CardSuit.HEARTS),
                    MatchFixtureFactory.card(CardRank.JACK, CardSuit.HEARTS),
                ),
                status = SeatStatus.ACTIVE,
            ),
            Seat(
                seatId = 2,
                displayName = "AI 2",
                controllerType = SeatControllerType.RULE_BASED_AI,
                hand = listOf(MatchFixtureFactory.card(CardRank.KING, CardSuit.DIAMONDS)),
                status = SeatStatus.ACTIVE,
            ),
            Seat(
                seatId = 3,
                displayName = "AI 3",
                controllerType = SeatControllerType.RULE_BASED_AI,
                hand = listOf(MatchFixtureFactory.card(CardRank.ACE, CardSuit.CLUBS)),
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
                currentCombination = straight,
                lastWinningSeatIndex = 0,
            ),
        )

        val result = northernEngine.passTurn(match = match, seatIndex = 1)

        assertFalse(result.success)
        assertEquals(GameActionError.MUST_BEAT_IF_POSSIBLE, result.error)
    }

    @Test
    fun invalidFourCardCombination_isRejected() {
        val fourCards = listOf(
            MatchFixtureFactory.card(CardRank.FIVE, CardSuit.DIAMONDS),
            MatchFixtureFactory.card(CardRank.FIVE, CardSuit.CLUBS),
            MatchFixtureFactory.card(CardRank.FIVE, CardSuit.HEARTS),
            MatchFixtureFactory.card(CardRank.FIVE, CardSuit.SPADES),
        )

        assertNull(southernEvaluator.parse(fourCards))
        assertNull(northernEvaluator.parse(fourCards))
    }

    @Test
    fun submitSelectedCards_southern_bombCanBeatBomb() {
        val current = southernEvaluator.parse(
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
        val seats = listOf(
            Seat(
                seatId = 0,
                displayName = "You",
                controllerType = SeatControllerType.HUMAN,
                hand = straightFlush.cards,
                status = SeatStatus.ACTIVE,
            ),
            Seat(
                seatId = 1,
                displayName = "AI 1",
                controllerType = SeatControllerType.RULE_BASED_AI,
                hand = emptyList(),
                status = SeatStatus.FINISHED,
            ),
            Seat(
                seatId = 2,
                displayName = "AI 2",
                controllerType = SeatControllerType.RULE_BASED_AI,
                hand = emptyList(),
                status = SeatStatus.FINISHED,
            ),
            Seat(
                seatId = 3,
                displayName = "AI 3",
                controllerType = SeatControllerType.RULE_BASED_AI,
                hand = current.cards,
                status = SeatStatus.ACTIVE,
            ),
        )
        val baseMatch = MatchFixtureFactory.localMatch(
            activeSeatIndex = 0,
            seats = seats,
            ruleSet = GameRuleSet.SOUTHERN,
        )
        val match = baseMatch.copy(
            trickState = baseMatch.trickState.copy(
                currentCombination = current,
                lastWinningSeatIndex = 3,
            ),
        )

        val result = engine.submitSelectedCards(
            match = match,
            seatIndex = 0,
            selectedCardIds = straightFlush.cards.map { it.id }.toSet(),
        )

        assertTrue(result.success)
    }

    @Test
    fun submitSelectedCards_southern_bombCanInterruptWithoutCondition() {
        val current = southernEvaluator.parse(
            listOf(MatchFixtureFactory.card(CardRank.ACE, CardSuit.SPADES)),
        )!!
        val bomb = southernEvaluator.parse(
            listOf(
                MatchFixtureFactory.card(CardRank.FIVE, CardSuit.DIAMONDS),
                MatchFixtureFactory.card(CardRank.FIVE, CardSuit.CLUBS),
                MatchFixtureFactory.card(CardRank.FIVE, CardSuit.HEARTS),
                MatchFixtureFactory.card(CardRank.FIVE, CardSuit.SPADES),
                MatchFixtureFactory.card(CardRank.THREE, CardSuit.CLUBS),
            ),
        )!!
        val seats = listOf(
            Seat(
                seatId = 0,
                displayName = "You",
                controllerType = SeatControllerType.HUMAN,
                hand = bomb.cards + MatchFixtureFactory.card(CardRank.THREE, CardSuit.DIAMONDS),
                status = SeatStatus.ACTIVE,
            ),
            Seat(
                seatId = 1,
                displayName = "AI 1",
                controllerType = SeatControllerType.RULE_BASED_AI,
                hand = emptyList(),
                status = SeatStatus.FINISHED,
            ),
            Seat(
                seatId = 2,
                displayName = "AI 2",
                controllerType = SeatControllerType.RULE_BASED_AI,
                hand = emptyList(),
                status = SeatStatus.FINISHED,
            ),
            Seat(
                seatId = 3,
                displayName = "AI 3",
                controllerType = SeatControllerType.RULE_BASED_AI,
                hand = current.cards,
                status = SeatStatus.ACTIVE,
            ),
        )
        val baseMatch = MatchFixtureFactory.localMatch(
            activeSeatIndex = 0,
            seats = seats,
            ruleSet = GameRuleSet.SOUTHERN,
        )
        val match = baseMatch.copy(
            trickState = baseMatch.trickState.copy(
                currentCombination = current,
                lastWinningSeatIndex = 3,
            ),
        )

        val result = engine.submitSelectedCards(
            match = match,
            seatIndex = 0,
            selectedCardIds = bomb.cards.map { it.id }.toSet(),
        )

        assertTrue(result.success)
    }

    @Test
    fun invalidSixCardCombination_isRejected() {
        val sixCards = listOf(
            MatchFixtureFactory.card(CardRank.FIVE, CardSuit.DIAMONDS),
            MatchFixtureFactory.card(CardRank.FIVE, CardSuit.CLUBS),
            MatchFixtureFactory.card(CardRank.FIVE, CardSuit.HEARTS),
            MatchFixtureFactory.card(CardRank.FIVE, CardSuit.SPADES),
            MatchFixtureFactory.card(CardRank.THREE, CardSuit.CLUBS),
            MatchFixtureFactory.card(CardRank.FOUR, CardSuit.HEARTS),
        )

        assertNull(southernEvaluator.parse(sixCards))
        assertNull(northernEvaluator.parse(sixCards))
    }
}
