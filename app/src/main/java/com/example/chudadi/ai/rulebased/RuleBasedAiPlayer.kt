package com.example.chudadi.ai.rulebased

import com.example.chudadi.model.game.entity.Card
import com.example.chudadi.model.game.entity.CardRank
import com.example.chudadi.model.game.entity.CardSuit
import com.example.chudadi.model.game.entity.Match
import com.example.chudadi.model.game.entity.PlayCombination
import com.example.chudadi.model.game.rule.CombinationEvaluator
import com.example.chudadi.model.game.rule.CombinationType
import com.example.chudadi.model.game.rule.GameRules
import kotlin.random.Random

sealed interface AiDecision {
    data class Play(
        val cardIds: Set<String>,
    ) : AiDecision

    data object Pass : AiDecision
}

class RuleBasedAiPlayer(
    private val evaluatorFactory: (Match) -> CombinationEvaluator = { match ->
        CombinationEvaluator(GameRules.forRuleSet(match.ruleSet))
    },
    private val random: Random = Random.Default,
) {
    fun decideAction(
        match: Match,
        seatIndex: Int,
    ): AiDecision {
        val evaluator = evaluatorFactory(match)
        val rules = GameRules.forRuleSet(match.ruleSet)
        val seat = match.seats.first { it.seatId == seatIndex }
        val currentCombination = match.trickState.currentCombination
        val allCombinations = evaluator.generateAllValidCombinations(seat.hand)
        val handProfile = buildHandProfile(seat.hand, allCombinations)
        val scorer = RuleBasedAiScoring(match, seatIndex, rules, seat.hand, handProfile)

        val candidate =
            if (currentCombination == null) {
                chooseLeadCombination(
                    combinations = allCombinations,
                    match = match,
                    seatIndex = seatIndex,
                    scorer = scorer,
                )
            } else {
                chooseResponseCombination(
                    combinations = allCombinations,
                    currentCombination = currentCombination,
                    evaluator = evaluator,
                    rules = rules,
                    scorer = scorer,
                )
            }

        return if (candidate == null) {
            AiDecision.Pass
        } else {
            AiDecision.Play(candidate.cards.map { it.id }.toSet())
        }
    }

    private fun chooseLeadCombination(
        combinations: List<PlayCombination>,
        match: Match,
        seatIndex: Int,
        scorer: RuleBasedAiScoring,
    ): PlayCombination? {
        if (combinations.isEmpty()) {
            return null
        }

        val requiresOpeningThree = requiresOpeningThree(match, seatIndex)
        val candidatePool =
            if (requiresOpeningThree) {
                combinations.filter { combination ->
                    combination.cards.any { it.id == OPENING_CARD.id }
                }
            } else {
                combinations
            }

        if (candidatePool.isEmpty()) {
            return null
        }

        val scoredCandidates =
            candidatePool
                .map { candidate ->
                    ScoredCandidate(
                        combination = candidate,
                        score = scorer.scoreLeadCandidate(candidate, requiresOpeningThree),
                    )
                }
                .sortedByDescending { it.score }

        return selectWeightedCandidate(scoredCandidates)
    }

    private fun chooseResponseCombination(
        combinations: List<PlayCombination>,
        currentCombination: PlayCombination,
        evaluator: CombinationEvaluator,
        rules: GameRules,
        scorer: RuleBasedAiScoring,
    ): PlayCombination? {
        val sameTypeResponses = combinations.filter { combination ->
            !rules.isBomb(combination.type) &&
                combination.type == currentCombination.type &&
                combination.cardCount == currentCombination.cardCount &&
                evaluator.canBeat(combination, currentCombination)
        }

        val legalResponses =
            combinations
                .filter { evaluator.canBeat(it, currentCombination) }
                .filter { combination ->
                    if (!rules.isBomb(combination.type) || !rules.bombRequiresNoSameTypeResponse) {
                        return@filter true
                    }
                    sameTypeResponses.isEmpty() || currentCombination.type == CombinationType.FOUR_OF_A_KIND_BOMB
                }

        if (legalResponses.isEmpty()) {
            return null
        }

        val scoredCandidates =
            legalResponses
                .map { candidate ->
                    ScoredCandidate(
                        combination = candidate,
                        score = scorer.scoreResponseCandidate(candidate, currentCombination),
                    )
                }
                .sortedByDescending { it.score }

        if (shouldPassResponse(scoredCandidates, currentCombination, rules, scorer)) {
            return null
        }

        return selectWeightedCandidate(scoredCandidates)
    }

    private fun shouldPassResponse(
        scoredCandidates: List<ScoredCandidate>,
        currentCombination: PlayCombination,
        rules: GameRules,
        scorer: RuleBasedAiScoring,
    ): Boolean {
        val shouldForcePlay =
            rules.mustBeatIfPossible &&
                hasMandatorySameTypeResponse(scoredCandidates, currentCombination, rules)
        val passProbability =
            scoredCandidates.firstOrNull()?.let { bestCandidate ->
                scorer.computePassProbability(
                    bestCandidate = bestCandidate.combination,
                    bestScore = bestCandidate.score,
                )
            }

        return when {
            scoredCandidates.isEmpty() -> true
            shouldForcePlay -> false
            passProbability == null -> false
            else -> random.nextDouble() < passProbability
        }
    }

    private fun hasMandatorySameTypeResponse(
        scoredCandidates: List<ScoredCandidate>,
        currentCombination: PlayCombination,
        rules: GameRules,
    ): Boolean {
        return scoredCandidates.any { scored ->
            val candidate = scored.combination
            !rules.isBomb(candidate.type) &&
                !rules.isBomb(currentCombination.type) &&
                candidate.type == currentCombination.type &&
                candidate.cardCount == currentCombination.cardCount
        }
    }

    private fun selectWeightedCandidate(scoredCandidates: List<ScoredCandidate>): PlayCombination? {
        if (scoredCandidates.isEmpty()) {
            return null
        }

        val topCandidates = scoredCandidates.take(TOP_CANDIDATE_COUNT)
        val minScore = topCandidates.minOf { it.score }
        val weightedCandidates =
            topCandidates.map { scored ->
                val weight =
                    (scored.score - minScore + BASE_SELECTION_WEIGHT)
                        .coerceAtLeast(MIN_SELECTION_WEIGHT)
                WeightedCandidate(scored.combination, weight)
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

    private fun requiresOpeningThree(
        match: Match,
        seatIndex: Int,
    ): Boolean {
        return match.trickState.currentCombination == null &&
            match.trickState.roundNumber == OPENING_ROUND_NUMBER &&
            match.trickState.passCount == ZERO_PASS_COUNT &&
            match.trickState.leadSeatIndex == seatIndex &&
            match.trickState.lastWinningSeatIndex == seatIndex
    }

    private data class ScoredCandidate(
        val combination: PlayCombination,
        val score: Double,
    )

    private data class WeightedCandidate(
        val combination: PlayCombination,
        val weight: Double,
    )

    companion object {
        private const val TOP_CANDIDATE_COUNT = 3
        private const val MIN_SELECTION_WEIGHT = 0.2
        private const val BASE_SELECTION_WEIGHT = 1.0
        private const val ZERO_DOUBLE = 0.0
        private const val OPENING_ROUND_NUMBER = 1
        private const val ZERO_PASS_COUNT = 0

        private val OPENING_CARD = Card(
            suit = CardSuit.DIAMONDS,
            rank = CardRank.THREE,
        )
    }
}
