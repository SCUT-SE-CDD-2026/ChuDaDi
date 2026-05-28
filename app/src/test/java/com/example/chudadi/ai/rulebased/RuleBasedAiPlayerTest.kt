package com.example.chudadi.ai.rulebased

import com.example.chudadi.ai.base.AIDecision
import com.example.chudadi.model.game.entity.Card
import com.example.chudadi.model.game.entity.CardRank
import com.example.chudadi.model.game.entity.CardSuit
import com.example.chudadi.model.game.entity.Match
import com.example.chudadi.model.game.entity.PlayCombination
import com.example.chudadi.model.game.entity.Seat
import com.example.chudadi.model.game.entity.SeatControllerType
import com.example.chudadi.model.game.entity.SeatStatus
import com.example.chudadi.model.game.fixture.MatchFixtureFactory
import com.example.chudadi.model.game.rule.CombinationEvaluator
import com.example.chudadi.model.game.rule.GameRuleSet
import com.example.chudadi.model.game.rule.GameRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class RuleBasedAiPlayerTest {
    @Test
    fun decideAction_openingMoveKeepsDiamondThreeInsideStructure() {
        val aiPlayer = RuleBasedAiPlayer(random = FixedDoubleRandom(0.0))
        val match =
            createMatch(
                ruleSet = GameRuleSet.SOUTHERN,
                activeSeatIndex = 1,
                seats = listOf(
                    createSeat(
                        seatId = 0,
                        controllerType = SeatControllerType.HUMAN,
                        cards = listOf(MatchFixtureFactory.card(CardRank.ACE, CardSuit.SPADES)),
                    ),
                    createSeat(
                        seatId = 1,
                        cards = listOf(
                            MatchFixtureFactory.card(CardRank.THREE, CardSuit.DIAMONDS),
                            MatchFixtureFactory.card(CardRank.FOUR, CardSuit.CLUBS),
                            MatchFixtureFactory.card(CardRank.FIVE, CardSuit.HEARTS),
                            MatchFixtureFactory.card(CardRank.SIX, CardSuit.SPADES),
                            MatchFixtureFactory.card(CardRank.SEVEN, CardSuit.DIAMONDS),
                            MatchFixtureFactory.card(CardRank.ACE, CardSuit.HEARTS),
                            MatchFixtureFactory.card(CardRank.TWO, CardSuit.CLUBS),
                        ),
                    ),
                    createSeat(2, listOf(MatchFixtureFactory.card(CardRank.EIGHT, CardSuit.CLUBS))),
                    createSeat(3, listOf(MatchFixtureFactory.card(CardRank.NINE, CardSuit.SPADES))),
                ),
            )

        val decision = aiPlayer.decideAction(match, 1)

        assertTrue(decision is AIDecision.PlayCards)
        val playedCardIds = (decision as AIDecision.PlayCards).cards.map { it.id }.toSet()
        assertTrue(playedCardIds.contains(MatchFixtureFactory.card(CardRank.THREE, CardSuit.DIAMONDS).id))
        assertTrue(playedCardIds.size > 1)
    }

    @Test
    fun decideAction_northernRuleMustPlayWhenSameTypeResponseExists() {
        val aiPlayer = RuleBasedAiPlayer(random = FixedDoubleRandom(0.0))
        val evaluator = CombinationEvaluator(GameRules.forRuleSet(GameRuleSet.NORTHERN))
        val currentCombination =
            evaluator.parse(listOf(MatchFixtureFactory.card(CardRank.FIVE, CardSuit.SPADES)))!!
        val match =
            createMatch(
                ruleSet = GameRuleSet.NORTHERN,
                activeSeatIndex = 1,
                currentCombination = currentCombination,
                lastWinningSeatIndex = 0,
                seats = listOf(
                    createSeat(
                        0,
                        listOf(MatchFixtureFactory.card(CardRank.THREE, CardSuit.DIAMONDS)),
                        SeatControllerType.HUMAN,
                    ),
                    createSeat(
                        1,
                        listOf(
                            MatchFixtureFactory.card(CardRank.SIX, CardSuit.CLUBS),
                            MatchFixtureFactory.card(CardRank.NINE, CardSuit.HEARTS),
                        ),
                    ),
                    createSeat(2, listOf(MatchFixtureFactory.card(CardRank.SEVEN, CardSuit.DIAMONDS))),
                    createSeat(3, listOf(MatchFixtureFactory.card(CardRank.EIGHT, CardSuit.CLUBS))),
                ),
            )

        val decision = aiPlayer.decideAction(match, 1)

        assertTrue(decision is AIDecision.PlayCards)
        val playedIds = (decision as AIDecision.PlayCards).cards.map { it.id }.toSet()
        assertEquals(1, playedIds.size)
        assertTrue(playedIds.first() in setOf(
            MatchFixtureFactory.card(CardRank.SIX, CardSuit.CLUBS).id,
            MatchFixtureFactory.card(CardRank.NINE, CardSuit.HEARTS).id,
        ))
    }

    @Test
    fun decideAction_northernRuleCanUseCrossTypeFiveCardResponse() {
        val aiPlayer = RuleBasedAiPlayer(random = FixedDoubleRandom(0.0))
        val evaluator = CombinationEvaluator(GameRules.forRuleSet(GameRuleSet.NORTHERN))
        val currentCombination =
            evaluator.parse(
                listOf(
                    MatchFixtureFactory.card(CardRank.THREE, CardSuit.DIAMONDS),
                    MatchFixtureFactory.card(CardRank.FOUR, CardSuit.CLUBS),
                    MatchFixtureFactory.card(CardRank.FIVE, CardSuit.HEARTS),
                    MatchFixtureFactory.card(CardRank.SIX, CardSuit.SPADES),
                    MatchFixtureFactory.card(CardRank.SEVEN, CardSuit.DIAMONDS),
                ),
            )!!
        val match =
            createMatch(
                ruleSet = GameRuleSet.NORTHERN,
                activeSeatIndex = 1,
                currentCombination = currentCombination,
                lastWinningSeatIndex = 0,
                seats = listOf(
                    createSeat(
                        0,
                        listOf(MatchFixtureFactory.card(CardRank.THREE, CardSuit.CLUBS)),
                        SeatControllerType.HUMAN,
                    ),
                    createSeat(
                        1,
                        listOf(
                            MatchFixtureFactory.card(CardRank.THREE, CardSuit.SPADES),
                            MatchFixtureFactory.card(CardRank.FIVE, CardSuit.SPADES),
                            MatchFixtureFactory.card(CardRank.SEVEN, CardSuit.SPADES),
                            MatchFixtureFactory.card(CardRank.NINE, CardSuit.SPADES),
                            MatchFixtureFactory.card(CardRank.JACK, CardSuit.SPADES),
                        ),
                    ),
                    createSeat(2, listOf(MatchFixtureFactory.card(CardRank.EIGHT, CardSuit.DIAMONDS))),
                    createSeat(3, listOf(MatchFixtureFactory.card(CardRank.NINE, CardSuit.CLUBS))),
                ),
            )

        val decision = aiPlayer.decideAction(match, 1)

        assertTrue(decision is AIDecision.PlayCards)
        assertEquals(
            setOf(
                MatchFixtureFactory.card(CardRank.THREE, CardSuit.SPADES).id,
                MatchFixtureFactory.card(CardRank.FIVE, CardSuit.SPADES).id,
                MatchFixtureFactory.card(CardRank.SEVEN, CardSuit.SPADES).id,
                MatchFixtureFactory.card(CardRank.NINE, CardSuit.SPADES).id,
                MatchFixtureFactory.card(CardRank.JACK, CardSuit.SPADES).id,
            ),
            (decision as AIDecision.PlayCards).cards.map { it.id }.toSet(),
        )
    }

    @Test
    fun decideAction_mustTopSingleWhenNextOpponentReportedOneCard() {
        val aiPlayer = RuleBasedAiPlayer(random = FixedDoubleRandom(0.0))
        val evaluator = CombinationEvaluator(GameRules.forRuleSet(GameRuleSet.SOUTHERN))
        val currentCombination =
            evaluator.parse(listOf(MatchFixtureFactory.card(CardRank.TEN, CardSuit.DIAMONDS)))!!
        val match =
            createMatch(
                ruleSet = GameRuleSet.SOUTHERN,
                activeSeatIndex = 1,
                currentCombination = currentCombination,
                lastWinningSeatIndex = 0,
                seats = listOf(
                    createSeat(
                        0,
                        listOf(MatchFixtureFactory.card(CardRank.THREE, CardSuit.CLUBS)),
                        SeatControllerType.HUMAN,
                    ),
                    createSeat(
                        1,
                        listOf(
                            MatchFixtureFactory.card(CardRank.JACK, CardSuit.CLUBS),
                            MatchFixtureFactory.card(CardRank.ACE, CardSuit.SPADES),
                        ),
                    ),
                    createSeat(2, listOf(MatchFixtureFactory.card(CardRank.KING, CardSuit.HEARTS))),
                    createSeat(3, listOf(MatchFixtureFactory.card(CardRank.SEVEN, CardSuit.DIAMONDS))),
                ),
            )

        val decision = aiPlayer.decideAction(match, 1)

        assertTrue(decision is AIDecision.PlayCards)
        assertEquals(
            setOf(MatchFixtureFactory.card(CardRank.ACE, CardSuit.SPADES).id),
            (decision as AIDecision.PlayCards).cards.map { it.id }.toSet(),
        )
    }

    @Test
    fun decideAction_southernRuleMayPassHighCostResponse() {
        val aiPlayer = RuleBasedAiPlayer(random = FixedDoubleRandom(0.0))
        val evaluator = CombinationEvaluator(GameRules.forRuleSet(GameRuleSet.SOUTHERN))
        val currentCombination =
            evaluator.parse(listOf(MatchFixtureFactory.card(CardRank.ACE, CardSuit.SPADES)))!!
        val match =
            createMatch(
                ruleSet = GameRuleSet.SOUTHERN,
                activeSeatIndex = 1,
                currentCombination = currentCombination,
                lastWinningSeatIndex = 0,
                seats = listOf(
                    createSeat(
                        0,
                        listOf(MatchFixtureFactory.card(CardRank.THREE, CardSuit.DIAMONDS)),
                        SeatControllerType.HUMAN,
                    ),
                    createSeat(
                        1,
                        listOf(
                            MatchFixtureFactory.card(CardRank.TWO, CardSuit.SPADES),
                            MatchFixtureFactory.card(CardRank.THREE, CardSuit.CLUBS),
                            MatchFixtureFactory.card(CardRank.THREE, CardSuit.HEARTS),
                            MatchFixtureFactory.card(CardRank.FOUR, CardSuit.DIAMONDS),
                            MatchFixtureFactory.card(CardRank.FOUR, CardSuit.CLUBS),
                        ),
                    ),
                    createSeat(2, listOf(MatchFixtureFactory.card(CardRank.FIVE, CardSuit.DIAMONDS))),
                    createSeat(3, listOf(MatchFixtureFactory.card(CardRank.SIX, CardSuit.CLUBS))),
                ),
            )

        val decision = aiPlayer.decideAction(match, 1)

        assertEquals(AIDecision.Pass, decision)
    }

    @Test
    fun decideAction_prefersKeepingPairWhenRespondingSingle() {
        val aiPlayer = RuleBasedAiPlayer(random = FixedDoubleRandom(0.0))
        val evaluator = CombinationEvaluator(GameRules.forRuleSet(GameRuleSet.SOUTHERN))
        val currentCombination =
            evaluator.parse(listOf(MatchFixtureFactory.card(CardRank.SEVEN, CardSuit.SPADES)))!!
        val match =
            createMatch(
                ruleSet = GameRuleSet.SOUTHERN,
                activeSeatIndex = 1,
                currentCombination = currentCombination,
                lastWinningSeatIndex = 0,
                seats = listOf(
                    createSeat(
                        0,
                        listOf(MatchFixtureFactory.card(CardRank.THREE, CardSuit.DIAMONDS)),
                        SeatControllerType.HUMAN,
                    ),
                    createSeat(
                        1,
                        listOf(
                            MatchFixtureFactory.card(CardRank.EIGHT, CardSuit.CLUBS),
                            MatchFixtureFactory.card(CardRank.EIGHT, CardSuit.DIAMONDS),
                            MatchFixtureFactory.card(CardRank.NINE, CardSuit.SPADES),
                        ),
                    ),
                    createSeat(2, listOf(MatchFixtureFactory.card(CardRank.FIVE, CardSuit.DIAMONDS))),
                    createSeat(3, listOf(MatchFixtureFactory.card(CardRank.SIX, CardSuit.CLUBS))),
                ),
            )

        val decision = aiPlayer.decideAction(match, 1)

        assertTrue(decision is AIDecision.PlayCards)
        assertEquals(
            setOf(MatchFixtureFactory.card(CardRank.NINE, CardSuit.SPADES).id),
            (decision as AIDecision.PlayCards).cards.map { it.id }.toSet(),
        )
        assertFalse(
            (decision as AIDecision.PlayCards).cards.any {
                it.id == MatchFixtureFactory.card(CardRank.EIGHT, CardSuit.CLUBS).id
            },
        )
    }

    private fun createMatch(
        ruleSet: GameRuleSet,
        activeSeatIndex: Int,
        seats: List<Seat>,
        currentCombination: PlayCombination? = null,
        lastWinningSeatIndex: Int = activeSeatIndex,
    ): Match {
        val baseMatch = MatchFixtureFactory.localMatch(
            activeSeatIndex = activeSeatIndex,
            seats = seats,
            ruleSet = ruleSet,
        )

        return baseMatch.copy(
            trickState = baseMatch.trickState.copy(
                currentCombination = currentCombination,
                lastWinningSeatIndex = lastWinningSeatIndex,
            ),
        )
    }

    private fun createSeat(
        seatId: Int,
        cards: List<Card>,
        controllerType: SeatControllerType = SeatControllerType.RULE_BASED_AI,
    ): Seat {
        return Seat(
            seatId = seatId,
            displayName = if (controllerType == SeatControllerType.HUMAN) "You" else "AI $seatId",
            controllerType = controllerType,
            hand = cards.sortedWith(Card.gameComparator),
            status = SeatStatus.ACTIVE,
        )
    }

    private class FixedDoubleRandom(
        private val fixedValue: Double,
    ) : Random() {
        override fun nextBits(bitCount: Int): Int = 0

        override fun nextDouble(): Double = fixedValue
    }
}
