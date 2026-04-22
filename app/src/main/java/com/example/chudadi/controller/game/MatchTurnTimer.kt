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

    fun scheduleTurn(isAiDrivenTurn: Boolean, nowMillis: Long = System.currentTimeMillis()) {
        val deadlineAtMillis = nowMillis + if (isAiDrivenTurn) {
            random.nextLong(AI_DELAY_MIN_MS, AI_DELAY_MAX_MS + 1)
        } else {
            HUMAN_TURN_DURATION_MS
        }
        state = if (isAiDrivenTurn) {
            TurnTimerState.AiThinking(deadlineAtMillis)
        } else {
            TurnTimerState.HumanTurn(deadlineAtMillis)
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

    companion object {
        const val HUMAN_TURN_DURATION_MS = 15_000L
        const val AI_DELAY_MIN_MS = 2_000L
        const val AI_DELAY_MAX_MS = 3_500L
    }
}

private sealed interface TurnTimerState {
    data object Idle : TurnTimerState

    data class HumanTurn(val deadlineAtMillis: Long) : TurnTimerState

    data class AiThinking(val deadlineAtMillis: Long) : TurnTimerState
}

private val TurnTimerState.deadlineAtMillis: Long?
    get() = when (this) {
        TurnTimerState.Idle -> null
        is TurnTimerState.HumanTurn -> deadlineAtMillis
        is TurnTimerState.AiThinking -> deadlineAtMillis
    }
