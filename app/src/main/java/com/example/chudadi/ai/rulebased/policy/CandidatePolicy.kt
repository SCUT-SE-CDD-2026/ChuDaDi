package com.example.chudadi.ai.rulebased.policy

import com.example.chudadi.ai.rulebased.OPENING_CARD
import com.example.chudadi.ai.rulebased.RuleBasedAiContext
import com.example.chudadi.model.game.entity.PlayCombination
import com.example.chudadi.model.game.entity.SeatStatus
import com.example.chudadi.model.game.rule.CombinationType

internal interface CandidatePolicy {
    fun generateLeadCandidates(context: RuleBasedAiContext): List<PlayCombination>

    fun filterLegalResponses(context: RuleBasedAiContext): List<PlayCombination>
}

internal class DefaultCandidatePolicy(
    private val turnConstraintPolicy: TurnConstraintPolicy = DefaultTurnConstraintPolicy(),
) : CandidatePolicy {
    override fun generateLeadCandidates(context: RuleBasedAiContext): List<PlayCombination> {
        if (!turnConstraintPolicy.requiresOpeningThree(context)) {
            return context.allCombinations
        }

        return context.allCombinations.filter { combination ->
            combination.cards.any { it.id == OPENING_CARD.id }
        }
    }

    override fun filterLegalResponses(context: RuleBasedAiContext): List<PlayCombination> {
        val currentCombination = context.currentCombination ?: return emptyList()
        val legalResponses = context.allCombinations
            .filter { context.evaluator.canBeat(it, currentCombination) }

        return if (requiresTopSingleResponse(context, currentCombination)) {
            restrictToTopSingle(legalResponses)
        } else {
            legalResponses
        }
    }

    private fun requiresTopSingleResponse(
        context: RuleBasedAiContext,
        currentCombination: PlayCombination,
    ): Boolean {
        if (currentCombination.type != CombinationType.SINGLE) {
            return false
        }

        val nextSeat = nextActiveSeat(context) ?: return false
        return nextSeat.hand.size == 1
    }

    private fun nextActiveSeat(context: RuleBasedAiContext) =
        context.match.seats
            .sortedBy { it.seatId }
            .let { seats ->
                val maxSeatId = seats.maxOf { it.seatId }
                var cursor = context.seatIndex
                repeat(maxSeatId + 1) {
                    cursor = (cursor + 1) % (maxSeatId + 1)
                    val seat = seats.first { it.seatId == cursor }
                    if (seat.status != SeatStatus.FINISHED) {
                        return@let seat
                    }
                }
                null
            }

    private fun restrictToTopSingle(legalResponses: List<PlayCombination>): List<PlayCombination> {
        val legalSingles = legalResponses.filter { it.type == CombinationType.SINGLE }
        val maxSingle = legalSingles.maxWithOrNull(
            compareBy<PlayCombination> { it.primaryRank }.thenBy { it.primarySuit },
        )

        return if (maxSingle == null) {
            legalResponses
        } else {
            legalResponses.filter { candidate ->
                candidate.type != CombinationType.SINGLE ||
                    (
                        candidate.primaryRank == maxSingle.primaryRank &&
                            candidate.primarySuit == maxSingle.primarySuit
                    )
            }
        }
    }
}
