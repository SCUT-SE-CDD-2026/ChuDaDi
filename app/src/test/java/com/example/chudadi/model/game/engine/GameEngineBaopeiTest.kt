package com.example.chudadi.model.game.engine

import com.example.chudadi.model.game.entity.Card
import com.example.chudadi.model.game.entity.CardRank
import com.example.chudadi.model.game.entity.CardSuit
import com.example.chudadi.model.game.entity.Seat
import com.example.chudadi.model.game.entity.SeatControllerType
import com.example.chudadi.model.game.entity.SeatStatus
import com.example.chudadi.model.game.entity.TrickState
import com.example.chudadi.model.game.fixture.MatchFixtureFactory
import com.example.chudadi.model.game.rule.CombinationEvaluator
import com.example.chudadi.model.game.rule.GameRuleSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameEngineBaopeiTest {
    private val engine = GameEngine()
    private val evaluator = CombinationEvaluator()

    private fun card(rank: CardRank, suit: CardSuit) = MatchFixtureFactory.card(rank, suit)

    @Test
    fun submitSelectedCards_nextSeatHasOneCard_andPlayedNotMaxSingle_appliesBaopei() {
        val match = createMatchWithTrick(
            activeSeatIndex = 0,
            seats = listOf(
                seat(0, listOf(card(CardRank.FOUR, CardSuit.HEARTS), card(CardRank.FIVE, CardSuit.HEARTS))),
                seat(1, listOf(card(CardRank.SIX, CardSuit.SPADES))),
                seat(2, listOf(card(CardRank.SEVEN, CardSuit.CLUBS))),
                seat(3, listOf(card(CardRank.EIGHT, CardSuit.DIAMONDS))),
            ),
            currentCombination = single(CardRank.THREE, CardSuit.DIAMONDS),
        )

        val result = engine.submitSelectedCards(
            match = match,
            seatIndex = 0,
            selectedCardIds = setOf(card(CardRank.FOUR, CardSuit.HEARTS).id),
        )

        assertTrue(result.success)
        assertEquals(0, result.match.trickState.baopeiSeatId)
    }

    @Test
    fun submitSelectedCards_nextSeatHasOneCard_andPlayedMaxSingle_doesNotApplyBaopei() {
        val match = createMatchWithTrick(
            activeSeatIndex = 0,
            seats = listOf(
                seat(0, listOf(card(CardRank.FOUR, CardSuit.HEARTS), card(CardRank.FIVE, CardSuit.HEARTS))),
                seat(1, listOf(card(CardRank.SIX, CardSuit.SPADES))),
                seat(2, listOf(card(CardRank.SEVEN, CardSuit.CLUBS))),
                seat(3, listOf(card(CardRank.EIGHT, CardSuit.DIAMONDS))),
            ),
            currentCombination = single(CardRank.THREE, CardSuit.DIAMONDS),
        )

        val result = engine.submitSelectedCards(
            match = match,
            seatIndex = 0,
            selectedCardIds = setOf(card(CardRank.FIVE, CardSuit.HEARTS).id),
        )

        assertTrue(result.success)
        assertNull(result.match.trickState.baopeiSeatId)
    }

    @Test
    fun submitSelectedCards_nextSeatHasMoreThanOneCard_doesNotApplyBaopei() {
        val match = createMatchWithTrick(
            activeSeatIndex = 0,
            seats = listOf(
                seat(0, listOf(card(CardRank.FOUR, CardSuit.HEARTS), card(CardRank.FIVE, CardSuit.HEARTS))),
                seat(1, listOf(card(CardRank.SIX, CardSuit.SPADES), card(CardRank.SEVEN, CardSuit.SPADES))),
                seat(2, listOf(card(CardRank.SEVEN, CardSuit.CLUBS))),
                seat(3, listOf(card(CardRank.EIGHT, CardSuit.DIAMONDS))),
            ),
            currentCombination = single(CardRank.THREE, CardSuit.DIAMONDS),
        )

        val result = engine.submitSelectedCards(
            match = match,
            seatIndex = 0,
            selectedCardIds = setOf(card(CardRank.FOUR, CardSuit.HEARTS).id),
        )

        assertTrue(result.success)
        assertNull(result.match.trickState.baopeiSeatId)
    }

    @Test
    fun submitSelectedCards_notSingle_doesNotApplyBaopei() {
        val match = createMatchWithTrick(
            activeSeatIndex = 0,
            seats = listOf(
                seat(0, listOf(
                    card(CardRank.FOUR, CardSuit.CLUBS),
                    card(CardRank.FIVE, CardSuit.CLUBS),
                    card(CardRank.SIX, CardSuit.CLUBS),
                    card(CardRank.SEVEN, CardSuit.CLUBS),
                    card(CardRank.EIGHT, CardSuit.CLUBS),
                    card(CardRank.NINE, CardSuit.HEARTS),
                )),
                seat(1, listOf(card(CardRank.SIX, CardSuit.SPADES))),
                seat(2, listOf(card(CardRank.SEVEN, CardSuit.CLUBS))),
                seat(3, listOf(card(CardRank.EIGHT, CardSuit.DIAMONDS))),
            ),
            currentCombination = single(CardRank.THREE, CardSuit.DIAMONDS),
            roundNumber = 2,
        )
        val straightFlush = evaluator.parse(
            listOf(
                card(CardRank.FOUR, CardSuit.CLUBS),
                card(CardRank.FIVE, CardSuit.CLUBS),
                card(CardRank.SIX, CardSuit.CLUBS),
                card(CardRank.SEVEN, CardSuit.CLUBS),
                card(CardRank.EIGHT, CardSuit.CLUBS),
            ),
        )!!

        val result = engine.submitSelectedCards(
            match = match,
            seatIndex = 0,
            selectedCardIds = straightFlush.cards.map { it.id }.toSet(),
        )

        assertTrue(result.success)
        assertNull(result.match.trickState.baopeiSeatId)
    }

    @Test
    fun submitSelectedCards_skipsFinishedSeat_andAppliesBaopeiToNextActive() {
        val match = createMatchWithTrick(
            activeSeatIndex = 0,
            seats = listOf(
                seat(0, listOf(card(CardRank.FOUR, CardSuit.HEARTS), card(CardRank.FIVE, CardSuit.HEARTS))),
                seat(1, emptyList()).copy(status = SeatStatus.FINISHED),
                seat(2, listOf(card(CardRank.SIX, CardSuit.SPADES))),
                seat(3, listOf(card(CardRank.EIGHT, CardSuit.DIAMONDS))),
            ),
            currentCombination = single(CardRank.THREE, CardSuit.DIAMONDS),
        )

        val result = engine.submitSelectedCards(
            match = match,
            seatIndex = 0,
            selectedCardIds = setOf(card(CardRank.FOUR, CardSuit.HEARTS).id),
        )

        assertTrue(result.success)
        assertEquals(0, result.match.trickState.baopeiSeatId)
    }

    @Test
    fun submitSelectedCards_sameRankDifferentSuit_playedLowerSuit_appliesBaopei() {
        val match = createMatchWithTrick(
            activeSeatIndex = 0,
            seats = listOf(
                seat(0, listOf(card(CardRank.ACE, CardSuit.SPADES), card(CardRank.ACE, CardSuit.HEARTS))),
                seat(1, listOf(card(CardRank.TWO, CardSuit.DIAMONDS))),
                seat(2, listOf(card(CardRank.SEVEN, CardSuit.CLUBS))),
                seat(3, listOf(card(CardRank.EIGHT, CardSuit.DIAMONDS))),
            ),
            currentCombination = single(CardRank.KING, CardSuit.DIAMONDS),
        )

        val result = engine.submitSelectedCards(
            match = match,
            seatIndex = 0,
            selectedCardIds = setOf(card(CardRank.ACE, CardSuit.HEARTS).id),
        )

        assertTrue(result.success)
        assertEquals(0, result.match.trickState.baopeiSeatId)
    }

    @Test
    fun submitSelectedCards_leadTurn_doesNotApplyBaopei() {
        val match = createMatchWithTrick(
            activeSeatIndex = 0,
            seats = listOf(
                seat(0, listOf(card(CardRank.FOUR, CardSuit.HEARTS), card(CardRank.FIVE, CardSuit.HEARTS))),
                seat(1, listOf(card(CardRank.SIX, CardSuit.SPADES))),
                seat(2, listOf(card(CardRank.SEVEN, CardSuit.CLUBS))),
                seat(3, listOf(card(CardRank.EIGHT, CardSuit.DIAMONDS))),
            ),
            currentCombination = null,
            roundNumber = 2,
        )

        val result = engine.submitSelectedCards(
            match = match,
            seatIndex = 0,
            selectedCardIds = setOf(card(CardRank.FOUR, CardSuit.HEARTS).id),
        )

        assertTrue(result.success)
        assertNull(result.match.trickState.baopeiSeatId)
    }

    @Test
    fun submitSelectedCards_nextSeatCanOnlyPass_doesNotApplyBaopei() {
        val match = createMatchWithTrick(
            activeSeatIndex = 0,
            seats = listOf(
                seat(0, listOf(card(CardRank.FOUR, CardSuit.HEARTS), card(CardRank.FIVE, CardSuit.HEARTS))),
                seat(1, listOf(card(CardRank.THREE, CardSuit.DIAMONDS))),
                seat(2, listOf(card(CardRank.SEVEN, CardSuit.CLUBS))),
                seat(3, listOf(card(CardRank.EIGHT, CardSuit.DIAMONDS))),
            ),
            currentCombination = single(CardRank.THREE, CardSuit.CLUBS),
        )

        val result = engine.submitSelectedCards(
            match = match,
            seatIndex = 0,
            selectedCardIds = setOf(card(CardRank.FOUR, CardSuit.HEARTS).id),
        )

        assertTrue(result.success)
        assertNull(result.match.trickState.baopeiSeatId)
    }

    @Test
    fun submitSelectedCards_northernRule_nextSeatHasOneCard_andPlayedNotMaxSingle_appliesBaopei() {
        val match = createMatchWithTrick(
            activeSeatIndex = 0,
            seats = listOf(
                seat(0, listOf(card(CardRank.FOUR, CardSuit.HEARTS), card(CardRank.FIVE, CardSuit.HEARTS))),
                seat(1, listOf(card(CardRank.SIX, CardSuit.SPADES))),
                seat(2, listOf(card(CardRank.SEVEN, CardSuit.CLUBS))),
                seat(3, listOf(card(CardRank.EIGHT, CardSuit.DIAMONDS))),
            ),
            currentCombination = single(CardRank.THREE, CardSuit.DIAMONDS),
            ruleSet = GameRuleSet.NORTHERN,
        )

        val result = engine.submitSelectedCards(
            match = match,
            seatIndex = 0,
            selectedCardIds = setOf(card(CardRank.FOUR, CardSuit.HEARTS).id),
        )

        assertTrue(result.success)
        assertEquals(0, result.match.trickState.baopeiSeatId)
    }

    @Test
    fun applyPass_resetsBaopeiSeatId_whenRoundResets() {
        val match = createMatchWithTrick(
            activeSeatIndex = 3,
            seats = listOf(
                seat(0, listOf(card(CardRank.TWO, CardSuit.DIAMONDS))),
                seat(1, emptyList()).copy(status = SeatStatus.FINISHED),
                seat(2, emptyList()).copy(status = SeatStatus.FINISHED),
                seat(3, listOf(card(CardRank.SEVEN, CardSuit.DIAMONDS))),
            ),
            currentCombination = single(CardRank.THREE, CardSuit.HEARTS),
            lastWinningSeatIndex = 0,
            baopeiSeatId = 0,
        )

        val result = engine.passTurn(match = match, seatIndex = 3)

        assertTrue(result.success)
        assertNull(result.match.trickState.baopeiSeatId)
    }

    private fun seat(id: Int, hand: List<Card>) = Seat(
        seatId = id,
        displayName = "Player $id",
        controllerType = SeatControllerType.HUMAN,
        hand = hand,
        status = SeatStatus.ACTIVE,
    )

    private fun single(rank: CardRank, suit: CardSuit) = evaluator.parse(listOf(card(rank, suit)))

    @Suppress("LongParameterList")
    private fun createMatchWithTrick(
        activeSeatIndex: Int,
        seats: List<Seat>,
        currentCombination: com.example.chudadi.model.game.entity.PlayCombination?,
        lastWinningSeatIndex: Int = activeSeatIndex,
        baopeiSeatId: Int? = null,
        roundNumber: Int = 1,
        ruleSet: GameRuleSet = GameRuleSet.SOUTHERN,
    ) = MatchFixtureFactory.localMatch(
        activeSeatIndex = activeSeatIndex,
        seats = seats,
        ruleSet = ruleSet,
    ).copy(
        trickState = TrickState(
            leadSeatIndex = lastWinningSeatIndex,
            lastWinningSeatIndex = lastWinningSeatIndex,
            currentCombination = currentCombination,
            roundNumber = roundNumber,
            baopeiSeatId = baopeiSeatId,
        ),
    )
}
