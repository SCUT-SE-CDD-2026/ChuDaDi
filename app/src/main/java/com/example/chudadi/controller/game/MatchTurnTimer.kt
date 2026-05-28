package com.example.chudadi.controller.game

import kotlin.random.Random

private const val MILLIS_PER_SECOND = 1000L

class MatchTurnTimer(
    private val random: Random = Random.Default,
) {
    private var state: TurnTimerState = TurnTimerState.Idle

    fun reset() {
        state = TurnTimerState.Idle
    }

    fun scheduleTurn(
        isAiDrivenTurn: Boolean,
        isLeadingTurn: Boolean = false,
        nowMillis: Long = System.currentTimeMillis(),
        aiDelayMillis: Long = random.nextLong(AI_DELAY_MIN_MS, AI_DELAY_MAX_MS + 1),
    ) {
        val deadlineAtMillis = nowMillis + if (isAiDrivenTurn) {
            aiDelayMillis
        } else if (isLeadingTurn) {
            FIRST_TURN_DURATION_MS
        } else {
            HUMAN_TURN_DURATION_MS
        }
        state = if (isAiDrivenTurn) {
            TurnTimerState.AiThinking(startedAtMillis = nowMillis, deadlineAtMillis = deadlineAtMillis)
        } else {
            TurnTimerState.HumanTurn(startedAtMillis = nowMillis, deadlineAtMillis = deadlineAtMillis)
        }
    }

    fun isExpired(nowMillis: Long = System.currentTimeMillis()): Boolean {
        return state.deadlineAtMillis?.let { nowMillis >= it } ?: false
    }

    fun remainingTurnSeconds(nowMillis: Long = System.currentTimeMillis()): Int? {
        val deadline = state.deadlineAtMillis ?: return null
        return ((deadline - nowMillis).coerceAtLeast(0L) / MILLIS_PER_SECOND).toInt()
    }

    fun isCurrentActorAi(): Boolean = state is TurnTimerState.AiThinking

    fun snapshot(): MatchTurnTimerSnapshot? {
        return state.snapshot
    }

    companion object {
        const val HUMAN_TURN_DURATION_MS = 15_000L
        const val FIRST_TURN_DURATION_MS = 25_000L
        const val AI_DELAY_MIN_MS = 2_000L
        const val AI_DELAY_MAX_MS = 3_500L
    }
}

data class MatchTurnTimerSnapshot(
    val startedAtMillis: Long,
    val deadlineAtMillis: Long,
    val isAiDrivenTurn: Boolean,
)

private sealed interface TurnTimerState {
    data object Idle : TurnTimerState

    data class HumanTurn(
        val startedAtMillis: Long,
        val deadlineAtMillis: Long,
    ) : TurnTimerState

    data class AiThinking(
        val startedAtMillis: Long,
        val deadlineAtMillis: Long,
    ) : TurnTimerState
}

private val TurnTimerState.deadlineAtMillis: Long?
    get() = when (this) {
        TurnTimerState.Idle -> null
        is TurnTimerState.HumanTurn -> deadlineAtMillis
        is TurnTimerState.AiThinking -> deadlineAtMillis
    }

private val TurnTimerState.snapshot: MatchTurnTimerSnapshot?
    get() = when (this) {
        TurnTimerState.Idle -> null
        is TurnTimerState.HumanTurn -> MatchTurnTimerSnapshot(
            startedAtMillis = startedAtMillis,
            deadlineAtMillis = deadlineAtMillis,
            isAiDrivenTurn = false,
        )
        is TurnTimerState.AiThinking -> MatchTurnTimerSnapshot(
            startedAtMillis = startedAtMillis,
            deadlineAtMillis = deadlineAtMillis,
            isAiDrivenTurn = true,
        )
    }
