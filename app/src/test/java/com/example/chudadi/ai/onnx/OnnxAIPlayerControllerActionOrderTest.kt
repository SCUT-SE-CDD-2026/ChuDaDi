package com.example.chudadi.ai.onnx

import com.example.chudadi.ai.base.AIDifficulty
import com.example.chudadi.model.game.entity.PlayCombination
import com.example.chudadi.model.game.fixture.MatchFixtureFactory
import com.example.chudadi.model.game.rule.CombinationType
import com.example.chudadi.model.game.rule.GameRuleSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OnnxAIPlayerControllerActionOrderTest {

    @Test
    fun buildActionCandidates_preservesActionOrderAndAppendsPassForSouthern() {
        val controller = OnnxAIPlayerController(
            seatIndex = 0,
            difficulty = AIDifficulty.NORMAL,
            modelPath = "",
        )
        val baseMatch = MatchFixtureFactory.localMatch(ruleSet = GameRuleSet.SOUTHERN)
        val lastActionCard = MatchFixtureFactory.card(
            rank = com.example.chudadi.model.game.entity.CardRank.SEVEN,
            suit = com.example.chudadi.model.game.entity.CardSuit.SPADES,
        )
        val lastAction = PlayCombination(
            type = CombinationType.SINGLE,
            cards = listOf(lastActionCard),
            primaryRank = lastActionCard.rank.strength,
            primarySuit = lastActionCard.suit.sortOrder,
        )
        val match = baseMatch.copy(
            trickState = baseMatch.trickState.copy(currentCombination = lastAction),
        )

        val hand = match.seats.first { it.seatId == 0 }.hand
        val actionA = listOf(hand[0])
        val actionB = listOf(hand[1])
        val validActions = listOf(actionA, actionB, actionA) // duplicate A should be de-duplicated by first occurrence

        val candidates = controller.buildActionCandidates(
            handCards = hand,
            validActions = validActions,
            match = match,
            ruleSet = GameRuleSet.SOUTHERN,
        )

        val actionAId = 1L shl GameStateEncoder.cardToIndex(actionA.first())
        val actionBId = 1L shl GameStateEncoder.cardToIndex(actionB.first())

        assertEquals(listOf(actionAId, actionBId, 0L), candidates.map { it.actionId })
        assertEquals(3, candidates.size)
        assertTrue(candidates.all { it.feature.size == ActionFeatureEncoder.ACTION_FEATURE_DIM })
    }
}

