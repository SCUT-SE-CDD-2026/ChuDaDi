package com.example.chudadi.model.game.rule

import com.example.chudadi.model.game.entity.CardRank
import com.example.chudadi.model.game.entity.CardSuit
import com.example.chudadi.model.game.fixture.MatchFixtureFactory
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CombinationEvaluatorTest {

    private val southernEvaluator = CombinationEvaluator(GameRules.forRuleSet(GameRuleSet.SOUTHERN))
    private val northernEvaluator = CombinationEvaluator(GameRules.forRuleSet(GameRuleSet.NORTHERN))

    private fun card(rank: CardRank, suit: CardSuit) = MatchFixtureFactory.card(rank, suit)

    // region hasMandatoryResponse

    @Test
    fun hasMandatoryResponse_southern_hasNonBombBeat_returnsTrue() {
        val current = southernEvaluator.parse(listOf(card(CardRank.FIVE, CardSuit.SPADES)))!!
        val candidates = listOf(
            southernEvaluator.parse(listOf(card(CardRank.SIX, CardSuit.HEARTS)))!!,
        )

        assertTrue(southernEvaluator.hasMandatoryResponse(candidates, current))
    }

    @Test
    fun hasMandatoryResponse_southern_onlyBombAvailable_returnsFalse() {
        val current = southernEvaluator.parse(listOf(card(CardRank.FIVE, CardSuit.SPADES)))!!
        // 同花顺是炸弹，在南方规则下不算 mandatory response
        val straightFlush = southernEvaluator.parse(
            listOf(
                card(CardRank.FOUR, CardSuit.CLUBS),
                card(CardRank.FIVE, CardSuit.CLUBS),
                card(CardRank.SIX, CardSuit.CLUBS),
                card(CardRank.SEVEN, CardSuit.CLUBS),
                card(CardRank.EIGHT, CardSuit.CLUBS),
            ),
        )!!

        assertFalse(southernEvaluator.hasMandatoryResponse(listOf(straightFlush), current))
    }

    @Test
    fun hasMandatoryResponse_southern_currentIsBomb_returnsFalse() {
        val current = southernEvaluator.parse(
            listOf(
                card(CardRank.FOUR, CardSuit.CLUBS),
                card(CardRank.FIVE, CardSuit.CLUBS),
                card(CardRank.SIX, CardSuit.CLUBS),
                card(CardRank.SEVEN, CardSuit.CLUBS),
                card(CardRank.EIGHT, CardSuit.CLUBS),
            ),
        )!!
        val single = southernEvaluator.parse(listOf(card(CardRank.SIX, CardSuit.HEARTS)))!!

        assertFalse(southernEvaluator.hasMandatoryResponse(listOf(single), current))
    }

    @Test
    fun hasMandatoryResponse_northern_flushCanBeatStraight_returnsTrue() {
        val straight = northernEvaluator.parse(
            listOf(
                card(CardRank.THREE, CardSuit.DIAMONDS),
                card(CardRank.FOUR, CardSuit.CLUBS),
                card(CardRank.FIVE, CardSuit.DIAMONDS),
                card(CardRank.SIX, CardSuit.HEARTS),
                card(CardRank.SEVEN, CardSuit.DIAMONDS),
            ),
        )!!
        val flush = northernEvaluator.parse(
            listOf(
                card(CardRank.THREE, CardSuit.HEARTS),
                card(CardRank.FIVE, CardSuit.HEARTS),
                card(CardRank.SEVEN, CardSuit.HEARTS),
                card(CardRank.NINE, CardSuit.HEARTS),
                card(CardRank.JACK, CardSuit.HEARTS),
            ),
        )!!

        assertTrue(northernEvaluator.hasMandatoryResponse(listOf(flush), straight))
    }

    @Test
    fun hasMandatoryResponse_noValidResponse_returnsFalse() {
        val current = southernEvaluator.parse(listOf(card(CardRank.ACE, CardSuit.SPADES)))!!
        val smaller = southernEvaluator.parse(listOf(card(CardRank.THREE, CardSuit.DIAMONDS)))!!

        assertFalse(southernEvaluator.hasMandatoryResponse(listOf(smaller), current))
    }

    // endregion

    // region hasSameTypeBeatOption

    @Test
    fun hasSameTypeBeatOption_southern_hasSameTypeBeat_returnsTrue() {
        val current = southernEvaluator.parse(listOf(card(CardRank.FIVE, CardSuit.SPADES)))!!
        val candidates = listOf(
            southernEvaluator.parse(listOf(card(CardRank.SIX, CardSuit.HEARTS)))!!,
        )

        assertTrue(southernEvaluator.hasSameTypeBeatOption(candidates, current))
    }

    @Test
    fun hasSameTypeBeatOption_northern_crossTypeDoesNotCount_returnsFalse() {
        val straight = northernEvaluator.parse(
            listOf(
                card(CardRank.THREE, CardSuit.DIAMONDS),
                card(CardRank.FOUR, CardSuit.CLUBS),
                card(CardRank.FIVE, CardSuit.DIAMONDS),
                card(CardRank.SIX, CardSuit.HEARTS),
                card(CardRank.SEVEN, CardSuit.DIAMONDS),
            ),
        )!!
        val flush = northernEvaluator.parse(
            listOf(
                card(CardRank.THREE, CardSuit.HEARTS),
                card(CardRank.FIVE, CardSuit.HEARTS),
                card(CardRank.SEVEN, CardSuit.HEARTS),
                card(CardRank.NINE, CardSuit.HEARTS),
                card(CardRank.JACK, CardSuit.HEARTS),
            ),
        )!!

        assertFalse(northernEvaluator.hasSameTypeBeatOption(listOf(flush), straight))
    }

    @Test
    fun hasSameTypeBeatOption_onlyBombAvailable_returnsFalse() {
        val current = southernEvaluator.parse(listOf(card(CardRank.FIVE, CardSuit.SPADES)))!!
        val straightFlush = southernEvaluator.parse(
            listOf(
                card(CardRank.FOUR, CardSuit.CLUBS),
                card(CardRank.FIVE, CardSuit.CLUBS),
                card(CardRank.SIX, CardSuit.CLUBS),
                card(CardRank.SEVEN, CardSuit.CLUBS),
                card(CardRank.EIGHT, CardSuit.CLUBS),
            ),
        )!!

        assertFalse(southernEvaluator.hasSameTypeBeatOption(listOf(straightFlush), current))
    }

    @Test
    fun hasSameTypeBeatOption_noValidResponse_returnsFalse() {
        val current = southernEvaluator.parse(listOf(card(CardRank.ACE, CardSuit.SPADES)))!!
        val smaller = southernEvaluator.parse(listOf(card(CardRank.THREE, CardSuit.DIAMONDS)))!!

        assertFalse(southernEvaluator.hasSameTypeBeatOption(listOf(smaller), current))
    }

    // endregion
}
