package com.example.chudadi.ai.onnx

import com.example.chudadi.model.game.entity.Card
import com.example.chudadi.model.game.entity.CardRank
import com.example.chudadi.model.game.entity.CardSuit
import com.example.chudadi.model.game.rule.CombinationType
import org.junit.Assert.assertEquals
import org.junit.Test

class ActionFeatureEncoderMainKickerTest {

    private val encoder = ActionFeatureEncoder()

    @Test
    fun fullHouse_setsMainAndKickerRank() {
        val actionCards = listOf(
            card(CardRank.ACE, CardSuit.SPADES),
            card(CardRank.ACE, CardSuit.HEARTS),
            card(CardRank.ACE, CardSuit.CLUBS),
            card(CardRank.KING, CardSuit.SPADES),
            card(CardRank.KING, CardSuit.DIAMONDS),
        )

        val feature = encoder.encodeActionFeature(
            handCards = actionCards,
            actionCards = actionCards,
            actionType = CombinationType.FULL_HOUSE,
        )

        assertEquals(1f, feature[MAIN_RANK_OFFSET + CardRank.ACE.ordinal], ZERO_TOLERANCE)
        assertEquals(1f, feature[KICKER_RANK_OFFSET + CardRank.KING.ordinal], ZERO_TOLERANCE)
        assertEquals(1f, feature.sliceArray(MAIN_RANK_OFFSET until KICKER_RANK_OFFSET).sum(), ZERO_TOLERANCE)
        val kickerRankRange = KICKER_RANK_OFFSET until ActionFeatureEncoder.ACTION_FEATURE_DIM
        assertEquals(1f, feature.sliceArray(kickerRankRange).sum(), ZERO_TOLERANCE)
    }

    private fun card(rank: CardRank, suit: CardSuit): Card = Card(rank = rank, suit = suit)

    private companion object {
        const val ACTION_TYPE_DIM = 9
        const val RANK_DIM = 13
        const val MAIN_RANK_OFFSET = 52 + 52 + ACTION_TYPE_DIM
        const val KICKER_RANK_OFFSET = MAIN_RANK_OFFSET + RANK_DIM
        const val ZERO_TOLERANCE = 0f
    }
}

