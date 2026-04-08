package com.example.chudadi.ai.rulebased.policy

import com.example.chudadi.model.game.entity.PlayCombination
import kotlin.random.Random

internal data class ScoredCandidate(
    val combination: PlayCombination,
    val score: Double,
)

internal interface SelectionPolicy {
    fun selectWeighted(candidates: List<ScoredCandidate>): PlayCombination?
}

internal class WeightedTopSelectionPolicy(
    private val random: Random = Random.Default,
) : SelectionPolicy {
    override fun selectWeighted(candidates: List<ScoredCandidate>): PlayCombination? {
        if (candidates.isEmpty()) {
            return null
        }

        val topCandidates = candidates.take(TOP_CANDIDATE_COUNT)
        val minScore = topCandidates.minOf { it.score }
        val weightedCandidates =
            topCandidates.map { scored ->
                WeightedCandidate(
                    combination = scored.combination,
                    weight = (scored.score - minScore + BASE_SELECTION_WEIGHT)
                        .coerceAtLeast(MIN_SELECTION_WEIGHT),
                )
            }

        val totalWeight = weightedCandidates.sumOf { it.weight }
        var roll = random.nextDouble() * totalWeight

        weightedCandidates.forEach { candidate ->
            roll -= candidate.weight
            if (roll <= ZERO_DOUBLE) {
                return candidate.combination
            }
        }

        return weightedCandidates.last().combination
    }

    private data class WeightedCandidate(
        val combination: PlayCombination,
        val weight: Double,
    )

    private companion object {
        const val TOP_CANDIDATE_COUNT = 3
        const val MIN_SELECTION_WEIGHT = 0.2
        const val BASE_SELECTION_WEIGHT = 1.0
        const val ZERO_DOUBLE = 0.0
    }
}
