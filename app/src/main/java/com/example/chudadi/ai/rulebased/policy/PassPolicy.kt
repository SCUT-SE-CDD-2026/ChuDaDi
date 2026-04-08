package com.example.chudadi.ai.rulebased.policy

import com.example.chudadi.ai.rulebased.RuleBasedAiContext
import kotlin.random.Random

internal interface PassPolicy {
    fun shouldPass(
        context: RuleBasedAiContext,
        scoredCandidates: List<ScoredCandidate>,
        passProbability: Double?,
        passAllowed: Boolean,
    ): Boolean
}

internal class ProbabilisticPassPolicy(
    private val random: Random = Random.Default,
) : PassPolicy {
    override fun shouldPass(
        context: RuleBasedAiContext,
        scoredCandidates: List<ScoredCandidate>,
        passProbability: Double?,
        passAllowed: Boolean,
    ): Boolean {
        return when {
            scoredCandidates.isEmpty() -> true
            !passAllowed -> false
            passProbability == null -> false
            else -> random.nextDouble() < passProbability
        }
    }
}
