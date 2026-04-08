package com.example.chudadi.ai.rulebased

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
                        cards = listOf(
                            MatchFixtureFactory.card(CardRank.ACE, CardSuit.SPADES),
                        ),
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
                    createSeat(
                        seatId = 2,
                        cards = listOf(MatchFixtureFactory.card(CardRank.EIGHT, CardSuit.CLUBS)),
                    ),
                    createSeat(
                        seatId = 3,
                        cards = listOf(MatchFixtureFactory.card(CardRank.NINE, CardSuit.SPADES)),
                    ),
                ),
            )

        val decision = aiPlayer.decideAction(match, 1)

        assertTrue(decision is AiDecision.Play)
        val playedCardIds = (decision as AiDecision.Play).cardIds
        assertTrue(playedCardIds.contains(MatchFixtureFactory.card(CardRank.THREE, CardSuit.DIAMONDS).id))
        assertTrue(playedCardIds.size > 1)
    }

    @Test
    fun decideAction_northernRuleMustPlayWhenSameTypeResponseExists() {
        val aiPlayer = RuleBasedAiPlayer(random = FixedDoubleRandom(0.0))
        val evaluator = CombinationEvaluator(GameRules.forRuleSet(GameRuleSet.NORTHERN))
        val currentCombination =
            evaluator.parse(
                listOf(MatchFixtureFactory.card(CardRank.FIVE, CardSuit.SPADES)),
            )!!
        val match =
            createMatch(
                ruleSet = GameRuleSet.NORTHERN,
                activeSeatIndex = 1,
                currentCombination = currentCombination,
                lastWinningSeatIndex = 0,
                seats = listOf(
                    createSeat(
                        seatId = 0,
                        controllerType = SeatControllerType.HUMAN,
                        cards = listOf(MatchFixtureFactory.card(CardRank.THREE, CardSuit.DIAMONDS)),
                    ),
                    createSeat(
                        seatId = 1,
                        cards = listOf(
                            MatchFixtureFactory.card(CardRank.SIX, CardSuit.CLUBS),
                            MatchFixtureFactory.card(CardRank.NINE, CardSuit.HEARTS),
                        ),
                    ),
                    createSeat(
                        seatId = 2,
                        cards = listOf(
                            MatchFixtureFactory.card(CardRank.SEVEN, CardSuit.DIAMONDS),
                            MatchFixtureFactory.card(CardRank.EIGHT, CardSuit.DIAMONDS),
                            MatchFixtureFactory.card(CardRank.NINE, CardSuit.DIAMONDS),
                            MatchFixtureFactory.card(CardRank.TEN, CardSuit.DIAMONDS),
                            MatchFixtureFactory.card(CardRank.JACK, CardSuit.DIAMONDS),
                        ),
                    ),
                    createSeat(
                        seatId = 3,
                        cards = listOf(
                            MatchFixtureFactory.card(CardRank.EIGHT, CardSuit.CLUBS),
                            MatchFixtureFactory.card(CardRank.NINE, CardSuit.CLUBS),
                            MatchFixtureFactory.card(CardRank.TEN, CardSuit.CLUBS),
                            MatchFixtureFactory.card(CardRank.JACK, CardSuit.CLUBS),
                            MatchFixtureFactory.card(CardRank.QUEEN, CardSuit.CLUBS),
                        ),
                    ),
                ),
            )

        val decision = aiPlayer.decideAction(match, 1)

        assertTrue(decision is AiDecision.Play)
        assertEquals(
            setOf(MatchFixtureFactory.card(CardRank.SIX, CardSuit.CLUBS).id),
            (decision as AiDecision.Play).cardIds,
        )
    }

    @Test
    fun decideAction_northernRuleCanPassWhenOnlyBombResponseExists() {
        val aiPlayer = RuleBasedAiPlayer(random = FixedDoubleRandom(0.0))
        val evaluator = CombinationEvaluator(GameRules.forRuleSet(GameRuleSet.NORTHERN))
        val currentCombination =
            evaluator.parse(
                listOf(MatchFixtureFactory.card(CardRank.ACE, CardSuit.SPADES)),
            )!!
        val match =
            createMatch(
                ruleSet = GameRuleSet.NORTHERN,
                activeSeatIndex = 1,
                currentCombination = currentCombination,
                lastWinningSeatIndex = 0,
                seats = listOf(
                    createSeat(
                        seatId = 0,
                        controllerType = SeatControllerType.HUMAN,
                        cards = listOf(MatchFixtureFactory.card(CardRank.THREE, CardSuit.DIAMONDS)),
                    ),
                    createSeat(
                        seatId = 1,
                        cards = listOf(
                            MatchFixtureFactory.card(CardRank.FIVE, CardSuit.DIAMONDS),
                            MatchFixtureFactory.card(CardRank.FIVE, CardSuit.CLUBS),
                            MatchFixtureFactory.card(CardRank.FIVE, CardSuit.HEARTS),
                            MatchFixtureFactory.card(CardRank.FIVE, CardSuit.SPADES),
                            MatchFixtureFactory.card(CardRank.THREE, CardSuit.CLUBS),
                        ),
                    ),
                    createSeat(
                        seatId = 2,
                        cards = listOf(MatchFixtureFactory.card(CardRank.SEVEN, CardSuit.DIAMONDS)),
                    ),
                    createSeat(
                        seatId = 3,
                        cards = listOf(MatchFixtureFactory.card(CardRank.EIGHT, CardSuit.CLUBS)),
                    ),
                ),
            )

        val decision = aiPlayer.decideAction(match, 1)

        assertEquals(AiDecision.Pass, decision)
    }

    @Test
    fun decideAction_southernRuleMayPassHighCostResponse() {
        val aiPlayer = RuleBasedAiPlayer(random = FixedDoubleRandom(0.0))
        val evaluator = CombinationEvaluator(GameRules.forRuleSet(GameRuleSet.SOUTHERN))
        val currentCombination =
            evaluator.parse(
                listOf(MatchFixtureFactory.card(CardRank.ACE, CardSuit.SPADES)),
            )!!
        val match =
            createMatch(
                ruleSet = GameRuleSet.SOUTHERN,
                activeSeatIndex = 1,
                currentCombination = currentCombination,
                lastWinningSeatIndex = 0,
                seats = listOf(
                    createSeat(
                        seatId = 0,
                        controllerType = SeatControllerType.HUMAN,
                        cards = listOf(MatchFixtureFactory.card(CardRank.THREE, CardSuit.DIAMONDS)),
                    ),
                    createSeat(
                        seatId = 1,
                        cards = listOf(
                            MatchFixtureFactory.card(CardRank.TWO, CardSuit.SPADES),
                            MatchFixtureFactory.card(CardRank.THREE, CardSuit.CLUBS),
                            MatchFixtureFactory.card(CardRank.THREE, CardSuit.HEARTS),
                            MatchFixtureFactory.card(CardRank.FOUR, CardSuit.DIAMONDS),
                            MatchFixtureFactory.card(CardRank.FOUR, CardSuit.CLUBS),
                        ),
                    ),
                    createSeat(
                        seatId = 2,
                        cards = listOf(MatchFixtureFactory.card(CardRank.FIVE, CardSuit.DIAMONDS)),
                    ),
                    createSeat(
                        seatId = 3,
                        cards = listOf(MatchFixtureFactory.card(CardRank.SIX, CardSuit.CLUBS)),
                    ),
                ),
            )

        val decision = aiPlayer.decideAction(match, 1)

        assertEquals(AiDecision.Pass, decision)
    }

    @Test
    fun decideAction_prefersKeepingPairWhenRespondingSingle() {
        val aiPlayer = RuleBasedAiPlayer(random = FixedDoubleRandom(0.0))
        val evaluator = CombinationEvaluator(GameRules.forRuleSet(GameRuleSet.SOUTHERN))
        val currentCombination =
            evaluator.parse(
                listOf(MatchFixtureFactory.card(CardRank.SEVEN, CardSuit.SPADES)),
            )!!
        val match =
            createMatch(
                ruleSet = GameRuleSet.SOUTHERN,
                activeSeatIndex = 1,
                currentCombination = currentCombination,
                lastWinningSeatIndex = 0,
                seats = listOf(
                    createSeat(
                        seatId = 0,
                        controllerType = SeatControllerType.HUMAN,
                        cards = listOf(MatchFixtureFactory.card(CardRank.THREE, CardSuit.DIAMONDS)),
                    ),
                    createSeat(
                        seatId = 1,
                        cards = listOf(
                            MatchFixtureFactory.card(CardRank.EIGHT, CardSuit.CLUBS),
                            MatchFixtureFactory.card(CardRank.EIGHT, CardSuit.DIAMONDS),
                            MatchFixtureFactory.card(CardRank.NINE, CardSuit.SPADES),
                        ),
                    ),
                    createSeat(
                        seatId = 2,
                        cards = listOf(MatchFixtureFactory.card(CardRank.FIVE, CardSuit.DIAMONDS)),
                    ),
                    createSeat(
                        seatId = 3,
                        cards = listOf(MatchFixtureFactory.card(CardRank.SIX, CardSuit.CLUBS)),
                    ),
                ),
            )

        val decision = aiPlayer.decideAction(match, 1)

        assertTrue(decision is AiDecision.Play)
        assertEquals(
            setOf(MatchFixtureFactory.card(CardRank.NINE, CardSuit.SPADES).id),
            (decision as AiDecision.Play).cardIds,
        )
        assertFalse(decision.cardIds.contains(MatchFixtureFactory.card(CardRank.EIGHT, CardSuit.CLUBS).id))
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
            displayName =
                if (controllerType == SeatControllerType.HUMAN) {
                    "You"
                } else {
                    "AI $seatId"
                },
            controllerType = controllerType,
            hand = cards.sortedWith(Card.gameComparator),
            status = SeatStatus.ACTIVE,
        )
    }

    private class FixedDoubleRandom(
        private val fixedValue: Double,
    ) : Random() {
        override fun nextBits(bitCount: Int): Int {
            return 0
        }

        override fun nextDouble(): Double {
            return fixedValue
        }
    }
}
