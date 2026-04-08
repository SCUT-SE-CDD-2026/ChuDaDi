package com.example.chudadi.ai.rulebased

import com.example.chudadi.ai.rulebased.policy.CandidatePolicy
import com.example.chudadi.ai.rulebased.policy.DefaultCandidatePolicy
import com.example.chudadi.ai.rulebased.policy.DefaultTurnConstraintPolicy
import com.example.chudadi.ai.rulebased.policy.PassPolicy
import com.example.chudadi.ai.rulebased.policy.SelectionPolicy
import com.example.chudadi.ai.rulebased.policy.TurnConstraintPolicy
import com.example.chudadi.ai.rulebased.scoring.DefaultScoringPolicy
import com.example.chudadi.ai.rulebased.scoring.ScoringPolicy

internal data class RuleBasedAiPolicies(
    val candidatePolicy: CandidatePolicy = DefaultCandidatePolicy(),
    val scoringPolicy: ScoringPolicy = DefaultScoringPolicy(),
    val selectionPolicy: SelectionPolicy,
    val passPolicy: PassPolicy,
    val turnConstraintPolicy: TurnConstraintPolicy = DefaultTurnConstraintPolicy(),
)
