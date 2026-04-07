package com.example.chudadi.ai.rulebased

import com.example.chudadi.ai.rulebased.policy.CandidatePolicy
import com.example.chudadi.ai.rulebased.policy.DefaultCandidatePolicy
import com.example.chudadi.ai.rulebased.policy.DefaultTurnConstraintPolicy
import com.example.chudadi.ai.rulebased.policy.PassPolicy
import com.example.chudadi.ai.rulebased.policy.ProbabilisticPassPolicy
import com.example.chudadi.ai.rulebased.policy.SelectionPolicy
import com.example.chudadi.ai.rulebased.policy.TurnConstraintPolicy
import com.example.chudadi.ai.rulebased.policy.WeightedTopSelectionPolicy
import com.example.chudadi.ai.rulebased.scoring.DefaultScoringPolicy
import com.example.chudadi.ai.rulebased.scoring.ScoringPolicy
import com.example.chudadi.model.game.entity.Match
import com.example.chudadi.model.game.entity.PlayCombination
import com.example.chudadi.model.game.rule.CombinationEvaluator
import com.example.chudadi.model.game.rule.GameRules
import kotlin.random.Random

sealed interface AiDecision {
    data class Play(
        val cardIds: Set<String>,
    ) : AiDecision

    data object Pass : AiDecision
}

class RuleBasedAiPlayer internal constructor(
    private val evaluatorFactory: (Match) -> CombinationEvaluator = { match ->
        CombinationEvaluator(GameRules.forRuleSet(match.ruleSet))
    },
    private val policies: RuleBasedAiPolicies,
) {
    constructor(
        evaluatorFactory: (Match) -> CombinationEvaluator = { match ->
            CombinationEvaluator(GameRules.forRuleSet(match.ruleSet))
        },
        random: Random = Random.Default,
    ) : this(
        evaluatorFactory = evaluatorFactory,
        policies = RuleBasedAiPolicies(
            candidatePolicy = DefaultCandidatePolicy(),
            scoringPolicy = DefaultScoringPolicy(),
            selectionPolicy = WeightedTopSelectionPolicy(random),
            passPolicy = ProbabilisticPassPolicy(random),
            turnConstraintPolicy = DefaultTurnConstraintPolicy(),
        ),
    )

    fun decideAction(
        match: Match,
        seatIndex: Int,
    ): AiDecision {
        val context = buildContext(match, seatIndex)

        return if (context.currentCombination == null) {
            decideLead(context)
        } else {
            decideResponse(context)
        }
    }

    private fun decideLead(context: RuleBasedAiContext): AiDecision {
        val candidates = policies.candidatePolicy.generateLeadCandidates(context)
        if (candidates.isEmpty()) {
            return AiDecision.Pass
        }

        val scoredCandidates =
            policies.scoringPolicy.scoreLead(
                context = context,
                candidates = candidates,
                requiresOpeningThree = policies.turnConstraintPolicy.requiresOpeningThree(context),
            )

        val selected = policies.selectionPolicy.selectWeighted(scoredCandidates) ?: return AiDecision.Pass
        return AiDecision.Play(selected.cards.map { it.id }.toSet())
    }

    private fun decideResponse(context: RuleBasedAiContext): AiDecision {
        val selected = resolveResponseSelection(context)
        return if (selected == null) {
            AiDecision.Pass
        } else {
            AiDecision.Play(selected.cards.map { it.id }.toSet())
        }
    }

    private fun resolveResponseSelection(context: RuleBasedAiContext): PlayCombination? {
        val currentCombination = context.currentCombination ?: return null
        val legalResponses = policies.candidatePolicy.filterLegalResponses(context)
        if (legalResponses.isEmpty()) {
            return null
        }

        val scoredCandidates =
            policies.scoringPolicy.scoreResponse(
                context = context,
                candidates = legalResponses,
                currentCombination = currentCombination,
            )
        val passAllowed = policies.turnConstraintPolicy.canPass(context, legalResponses)
        val passProbability =
            policies.scoringPolicy.computePassProbability(context, scoredCandidates.firstOrNull())

        return if (policies.passPolicy.shouldPass(context, scoredCandidates, passProbability, passAllowed)) {
            null
        } else {
            policies.selectionPolicy.selectWeighted(scoredCandidates)
        }
    }

    private fun buildContext(
        match: Match,
        seatIndex: Int,
    ): RuleBasedAiContext {
        val evaluator = evaluatorFactory(match)
        val rules = GameRules.forRuleSet(match.ruleSet)
        val seat = match.seats.first { it.seatId == seatIndex }
        val allCombinations = evaluator.generateAllValidCombinations(seat.hand)
        val handProfile = buildHandProfile(seat.hand, allCombinations)

        return RuleBasedAiContext(
            match = match,
            seatIndex = seatIndex,
            seat = seat,
            hand = seat.hand,
            currentCombination = match.trickState.currentCombination,
            evaluator = evaluator,
            rules = rules,
            allCombinations = allCombinations,
            handProfile = handProfile,
        )
    }
}
