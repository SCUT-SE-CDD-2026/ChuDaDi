package com.example.chudadi.ai.rulebased.scoring

import com.example.chudadi.ai.rulebased.RuleBasedAiContext
import com.example.chudadi.ai.rulebased.policy.ScoredCandidate
import com.example.chudadi.model.game.entity.PlayCombination

internal interface ScoringPolicy {
    fun scoreLead(
        context: RuleBasedAiContext,
        candidates: List<PlayCombination>,
        requiresOpeningThree: Boolean,
    ): List<ScoredCandidate>

    fun scoreResponse(
        context: RuleBasedAiContext,
        candidates: List<PlayCombination>,
        currentCombination: PlayCombination,
    ): List<ScoredCandidate>

    fun computePassProbability(
        context: RuleBasedAiContext,
        bestCandidate: ScoredCandidate?,
    ): Double?
}

internal class DefaultScoringPolicy : ScoringPolicy {
    override fun scoreLead(
        context: RuleBasedAiContext,
        candidates: List<PlayCombination>,
        requiresOpeningThree: Boolean,
    ): List<ScoredCandidate> {
        val scorer = LeadScorer(context)
        return candidates
            .map { candidate ->
                ScoredCandidate(
                    combination = candidate,
                    score = scorer.score(candidate, requiresOpeningThree),
                )
            }
            .sortedByDescending { it.score }
    }

    override fun scoreResponse(
        context: RuleBasedAiContext,
        candidates: List<PlayCombination>,
        currentCombination: PlayCombination,
    ): List<ScoredCandidate> {
        val scorer = ResponseScorer(context)
        return candidates
            .map { candidate ->
                ScoredCandidate(
                    combination = candidate,
                    score = scorer.score(candidate, currentCombination),
                )
            }
            .sortedByDescending { it.score }
    }

    override fun computePassProbability(
        context: RuleBasedAiContext,
        bestCandidate: ScoredCandidate?,
    ): Double? {
        val candidate = bestCandidate ?: return null
        return PassProbabilityEvaluator(context).compute(
            bestCandidate = candidate.combination,
            bestScore = candidate.score,
        )
    }
}
