package com.example.chudadi.ai.onnx.pipeline

import com.example.chudadi.ai.base.AIDecision
import com.example.chudadi.ai.base.AIDifficulty
import com.example.chudadi.ai.base.ValidCombinationResolver
import com.example.chudadi.ai.base.variant.InferencePipeline
import com.example.chudadi.ai.base.variant.ObservationEncoder
import com.example.chudadi.ai.onnx.ActionDecoder
import com.example.chudadi.ai.onnx.ActionFeatureEncoder
import com.example.chudadi.ai.onnx.OnnxModel
import com.example.chudadi.ai.onnx.PlayedHistorySequenceEncoder
import com.example.chudadi.model.game.entity.Card
import com.example.chudadi.model.game.entity.Match
import com.example.chudadi.model.game.rule.CombinationParser
import com.example.chudadi.model.game.rule.GameRuleSet

class V2GruDqnPipeline : InferencePipeline {
    private val actionFeatureEncoder = ActionFeatureEncoder()
    private val historyEncoder = PlayedHistorySequenceEncoder()
    private val decoder = ActionDecoder()
    private val actionIdMapper = BitmaskActionIdMapper()

    internal data class ActionCandidate(
        val actionId: Long,
        val feature: FloatArray,
    )

    override suspend fun decide(
        model: OnnxModel,
        obsEncoder: ObservationEncoder,
        match: Match,
        seatIndex: Int,
        handCards: List<Card>,
        validActions: List<List<Card>>,
        ruleSet: GameRuleSet,
        difficulty: AIDifficulty,
    ): AIDecision {
        val candidates = buildActionCandidates(
            handCards = handCards,
            validActions = validActions,
            match = match,
            seatIndex = seatIndex,
            ruleSet = ruleSet,
        )
        val obs = obsEncoder.encode(match, seatIndex, ruleSet)
        val history = historyEncoder.encode(match, seatIndex)
        val prediction = model.predictActionValuesWithObsAndHistory(
            obs = obs,
            history = history,
            actionFeatures = candidates.map { it.feature },
        )
        return if (prediction != null) {
            decoder.decode(
                modelOutput = prediction,
                handCards = handCards,
                validActionMask = candidates.map { it.actionId }.toLongArray(),
                difficulty = difficulty,
            )
        } else {
            AIDecision.Error("ONNX V2 returned empty prediction")
        }
    }

    internal fun buildActionCandidates(
        handCards: List<Card>,
        validActions: List<List<Card>>,
        match: Match,
        seatIndex: Int,
        ruleSet: GameRuleSet,
    ): List<ActionCandidate> {
        val candidates = mutableListOf<ActionCandidate>()
        val seenActionIds = mutableSetOf<Long>()
        val parser = CombinationParser()
        for (cards in validActions) {
            val actionId = actionIdMapper.cardsToId(cards)
            if (!seenActionIds.add(actionId)) continue
            candidates += ActionCandidate(
                actionId = actionId,
                feature = actionFeatureEncoder.encodeActionFeature(
                    handCards = handCards,
                    actionCards = cards,
                    actionType = parser.parse(cards)?.type,
                ),
            )
        }
        if (isPassLegal(match, seatIndex, ruleSet) && seenActionIds.add(BitmaskActionIdMapper.PASS_ACTION_ID)) {
            candidates += ActionCandidate(
                actionId = BitmaskActionIdMapper.PASS_ACTION_ID,
                feature = actionFeatureEncoder.encodeActionFeature(
                    handCards = handCards,
                    actionCards = emptyList(),
                    actionType = null,
                ),
            )
        }
        if (candidates.isEmpty()) {
            candidates += ActionCandidate(
                actionId = BitmaskActionIdMapper.PASS_ACTION_ID,
                feature = actionFeatureEncoder.encodeActionFeature(
                    handCards = handCards,
                    actionCards = emptyList(),
                    actionType = null,
                ),
            )
        }
        return candidates
    }

    private fun isPassLegal(match: Match, seatIndex: Int, ruleSet: GameRuleSet): Boolean {
        return when (ruleSet) {
            GameRuleSet.SOUTHERN -> match.trickState.currentCombination != null
            GameRuleSet.NORTHERN -> ValidCombinationResolver.canPassUnderNorthernRule(
                match, seatIndex, ruleSet,
            )
        }
    }
}
