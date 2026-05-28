package com.example.chudadi.ai.onnx

import com.example.chudadi.ai.base.AIDecision
import com.example.chudadi.ai.base.AIDifficulty
import com.example.chudadi.model.game.entity.Card
import com.example.chudadi.model.game.entity.CardRank
import com.example.chudadi.model.game.entity.CardSuit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionDecoderDifficultyTest {

    private val decoder = ActionDecoder()

    @Test
    fun hard_selectsHighestQAction() {
        val lowCard = card(CardRank.THREE, CardSuit.DIAMONDS)
        val highCard = card(CardRank.FOUR, CardSuit.DIAMONDS)
        val handCards = listOf(lowCard, highCard)
        val validActionMask = longArrayOf(actionId(lowCard), actionId(highCard))
        val modelOutput = floatArrayOf(0.1f, 0.9f)

        val decision = decoder.decode(
            modelOutput = modelOutput,
            handCards = handCards,
            validActionMask = validActionMask,
            difficulty = AIDifficulty.HARD,
        )

        assertTrue(decision is AIDecision.PlayCards)
        val cards = (decision as AIDecision.PlayCards).cards
        assertEquals(1, cards.size)
        assertEquals(highCard.id, cards.first().id)
    }

    @Test
    fun normal_withSingleValidAction_remainsDeterministic() {
        val onlyCard = card(CardRank.FIVE, CardSuit.CLUBS)
        val handCards = listOf(onlyCard)
        val modelOutput = floatArrayOf(0.4f)

        val decision = decoder.decode(
            modelOutput = modelOutput,
            handCards = handCards,
            validActionMask = longArrayOf(actionId(onlyCard)),
            difficulty = AIDifficulty.NORMAL,
        )

        assertTrue(decision is AIDecision.PlayCards)
        val cards = (decision as AIDecision.PlayCards).cards
        assertEquals(1, cards.size)
        assertEquals(onlyCard.id, cards.first().id)
    }

    @Test
    fun easy_withSingleValidAction_remainsDeterministic() {
        val onlyCard = card(CardRank.SIX, CardSuit.HEARTS)
        val handCards = listOf(onlyCard)
        val modelOutput = floatArrayOf(-0.3f)

        val decision = decoder.decode(
            modelOutput = modelOutput,
            handCards = handCards,
            validActionMask = longArrayOf(actionId(onlyCard)),
            difficulty = AIDifficulty.EASY,
        )

        assertTrue(decision is AIDecision.PlayCards)
        val cards = (decision as AIDecision.PlayCards).cards
        assertEquals(1, cards.size)
        assertEquals(onlyCard.id, cards.first().id)
    }

    private fun card(rank: CardRank, suit: CardSuit): Card = Card(rank = rank, suit = suit)

    private fun actionId(card: Card): Long {
        return 1L shl GameStateEncoder.cardToIndex(card)
    }
}

