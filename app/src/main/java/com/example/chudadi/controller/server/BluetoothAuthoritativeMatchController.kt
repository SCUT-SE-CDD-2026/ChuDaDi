@file:Suppress("TooManyFunctions", "MaxLineLength")

package com.example.chudadi.controller.server

import com.example.chudadi.ai.rulebased.AiDecision
import com.example.chudadi.ai.rulebased.RuleBasedAiPlayer
import com.example.chudadi.controller.client.GameActionMessageFormatter
import com.example.chudadi.controller.game.MatchUiStateMapper
import com.example.chudadi.model.game.engine.GameEngine
import com.example.chudadi.model.game.entity.Match
import com.example.chudadi.model.game.entity.MatchPhase
import com.example.chudadi.model.game.entity.RoundScore
import com.example.chudadi.model.game.entity.SeatControllerType
import com.example.chudadi.model.game.rule.GameRuleSet
import com.example.chudadi.model.game.snapshot.MatchUiState
import com.example.chudadi.network.game.GameWireMessage
import com.example.chudadi.network.game.toRemoteMatchSnapshot
import com.example.chudadi.network.protocol.PassCommand
import com.example.chudadi.network.protocol.PlayCardCommand
import kotlin.random.Random

private const val MILLIS_PER_SECOND = 1000L

class BluetoothAuthoritativeMatchController(
    private val engine: GameEngine,
    private val mapper: MatchUiStateMapper,
    private val aiPlayer: RuleBasedAiPlayer,
) {
    private var currentMatch: Match? = null
    private var turnTimerState: TurnTimerState = TurnTimerState.Idle
    private var disconnectedSeatIds: Set<Int> = emptySet()

    fun startMatch(
        seatConfigs: List<Triple<Int, String, SeatControllerType>>,
        ruleSet: GameRuleSet,
    ): Match {
        val match = engine.startLocalMatch(ruleSet = ruleSet, seatConfigs = seatConfigs)
        currentMatch = match
        disconnectedSeatIds = emptySet()
        scheduleCurrentTurn(match)
        return match
    }

    fun currentMatch(): Match? = currentMatch

    fun buildSnapshotForSeat(localSeatId: Int, lastActionMessage: String? = null): MatchUiState {
        val match = currentMatch ?: return MatchUiState()
        val base = mapper.map(
            match = match,
            selectedCardIds = emptySet(),
            lastActionMessage = lastActionMessage,
            localSeatId = localSeatId,
        )
        return base.copy(
            remainingTurnSeconds = remainingTurnSeconds(),
            isLocalDisconnected = localSeatId in disconnectedSeatIds,
            opponentSummaries = base.opponentSummaries.map { opponent ->
                opponent.copy(isDisconnected = opponent.authoritySeatId in disconnectedSeatIds)
            },
        )
    }

    fun handlePlayRequest(seatId: Int, selectedCardIds: Set<String>): MatchActionEnvelope {
        val match = currentMatch ?: return MatchActionEnvelope.empty()
        val result = PlayCardCommand(selectedCardIds).execute(match = match, seatIndex = seatId, engine = engine)
        currentMatch = result.match
        if (result.success) {
            scheduleCurrentTurn(result.match)
        }
        return MatchActionEnvelope(
            match = result.match,
            message = result.message ?: GameActionMessageFormatter.format(result.error),
            success = result.success,
            roundScores = result.match.result?.scoreSummary?.roundScores.orEmpty(),
        )
    }

    fun handlePassRequest(seatId: Int): MatchActionEnvelope {
        val match = currentMatch ?: return MatchActionEnvelope.empty()
        val result = PassCommand.execute(match = match, seatIndex = seatId, engine = engine)
        currentMatch = result.match
        if (result.success) {
            scheduleCurrentTurn(result.match)
        }
        return MatchActionEnvelope(
            match = result.match,
            message = result.message ?: GameActionMessageFormatter.format(result.error),
            success = result.success,
            roundScores = result.match.result?.scoreSummary?.roundScores.orEmpty(),
        )
    }

    fun resolveCurrentAiTurn(lastMessage: String?): MatchActionEnvelope {
        val match = currentMatch
        val result = when {
            match == null -> MatchActionEnvelope.empty()
            match.phase == MatchPhase.FINISHED -> MatchActionEnvelope(
                match = match,
                message = lastMessage,
                success = false,
                roundScores = match.result?.scoreSummary?.roundScores.orEmpty(),
            )
            else -> resolveAiAction(match = match, seatId = match.activeSeatIndex, allowHumanSeatProxy = false, lastMessage = lastMessage)
        }
        return result
    }

    fun resolveCurrentSeatByAi(lastMessage: String?): MatchActionEnvelope {
        val match = currentMatch
        val result = when {
            match == null -> MatchActionEnvelope.empty()
            match.phase == MatchPhase.FINISHED -> MatchActionEnvelope(
                match = match,
                message = lastMessage,
                success = false,
                roundScores = match.result?.scoreSummary?.roundScores.orEmpty(),
            )
            else -> resolveAiAction(match = match, seatId = match.activeSeatIndex, allowHumanSeatProxy = true, lastMessage = lastMessage)
        }
        return result
    }

    fun markDisconnected(seatId: Int, disconnected: Boolean) {
        disconnectedSeatIds = if (disconnected) disconnectedSeatIds + seatId else disconnectedSeatIds - seatId
        val match = currentMatch ?: return
        if (match.phase != MatchPhase.FINISHED && match.activeSeatIndex == seatId) {
            scheduleCurrentTurn(match)
        }
    }

    fun onSeatReconnected(seatId: Int) {
        markDisconnected(seatId, disconnected = false)
        val match = currentMatch ?: return
        if (match.phase != MatchPhase.FINISHED && match.activeSeatIndex == seatId) {
            scheduleCurrentTurn(match, forceHumanWindow = true)
        }
    }

    fun isCurrentTurnExpired(nowMillis: Long = System.currentTimeMillis()): Boolean {
        return turnTimerState.deadlineAtMillis?.let { nowMillis >= it } ?: false
    }

    fun remainingTurnSeconds(nowMillis: Long = System.currentTimeMillis()): Int? {
        val deadline = turnTimerState.deadlineAtMillis ?: return null
        return ((deadline - nowMillis).coerceAtLeast(0L) / MILLIS_PER_SECOND).toInt()
    }

    fun isCurrentActorAi(): Boolean {
        return turnTimerState is TurnTimerState.AiThinking
    }

    fun canCurrentSeatPass(): Boolean {
        val match = currentMatch ?: return false
        return engine.canPass(match = match, seatIndex = match.activeSeatIndex)
    }

    fun buildMatchStartedMessage(localSeatId: Int): GameWireMessage.MatchStarted {
        val match = currentMatch ?: error("Match not started")
        val snapshot = buildSnapshotForSeat(localSeatId).toRemoteMatchSnapshot(match.matchId)
        return GameWireMessage.MatchStarted(localSeatId = localSeatId, snapshot = snapshot)
    }

    fun buildSnapshotMessage(localSeatId: Int, lastActionMessage: String?): GameWireMessage.MatchSnapshotMessage {
        val match = currentMatch ?: error("Match not started")
        val snapshot = buildSnapshotForSeat(localSeatId, lastActionMessage).toRemoteMatchSnapshot(match.matchId)
        return GameWireMessage.MatchSnapshotMessage(snapshot)
    }

    fun closeMatch(reason: String): GameWireMessage.MatchClosed {
        currentMatch = null
        turnTimerState = TurnTimerState.Idle
        disconnectedSeatIds = emptySet()
        return GameWireMessage.MatchClosed(reason)
    }

    fun clearCurrentMatch() {
        currentMatch = null
        turnTimerState = TurnTimerState.Idle
        disconnectedSeatIds = emptySet()
    }

    private fun scheduleCurrentTurn(match: Match, forceHumanWindow: Boolean = false) {
        if (match.phase == MatchPhase.FINISHED) {
            turnTimerState = TurnTimerState.Idle
            return
        }
        val activeSeat = match.seats.first { it.seatId == match.activeSeatIndex }
        val isAiDrivenTurn = !forceHumanWindow && isAiDrivenSeat(activeSeat.seatId, activeSeat.controllerType)
        val deadlineAtMillis = System.currentTimeMillis() + if (isAiDrivenTurn) {
            aiDelayMillis()
        } else {
            HUMAN_TURN_DURATION_MS
        }
        turnTimerState = if (isAiDrivenTurn) {
            TurnTimerState.AiThinking(deadlineAtMillis)
        } else {
            TurnTimerState.HumanTurn(deadlineAtMillis)
        }
    }

    private fun isAiDrivenSeat(seatId: Int, controllerType: SeatControllerType): Boolean {
        return controllerType == SeatControllerType.RULE_BASED_AI || seatId in disconnectedSeatIds
    }

    private fun resolveAiAction(
        match: Match,
        seatId: Int,
        allowHumanSeatProxy: Boolean,
        lastMessage: String?,
    ): MatchActionEnvelope {
        val seat = match.seats.first { it.seatId == seatId }
        val canUseAiProxy = seat.controllerType == SeatControllerType.RULE_BASED_AI || allowHumanSeatProxy
        if (!canUseAiProxy) {
            return MatchActionEnvelope(match, lastMessage, success = false, roundScores = emptyList())
        }
        val decision = aiPlayer.decideAction(match, seatId)
        val action = when (decision) {
            is AiDecision.Play -> PlayCardCommand(decision.cardIds).execute(match, seatId, engine)
            AiDecision.Pass -> PassCommand.execute(match, seatId, engine)
        }
        currentMatch = action.match
        if (action.success) {
            scheduleCurrentTurn(action.match)
        }
        return MatchActionEnvelope(
            match = action.match,
            message = action.message ?: action.error?.let(GameActionMessageFormatter::format) ?: lastMessage,
            success = action.success,
            roundScores = action.match.result?.scoreSummary?.roundScores.orEmpty(),
        )
    }

    private fun aiDelayMillis(): Long = Random.nextLong(AI_DELAY_MIN_MS, AI_DELAY_MAX_MS + 1)

    companion object {
        private const val HUMAN_TURN_DURATION_MS = 15_000L
        private const val AI_DELAY_MIN_MS = 2_000L
        private const val AI_DELAY_MAX_MS = 3_500L
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

data class MatchActionEnvelope(
    val match: Match?,
    val message: String?,
    val success: Boolean,
    val roundScores: List<RoundScore>,
) {
    companion object {
        fun empty(): MatchActionEnvelope {
            return MatchActionEnvelope(
                match = null,
                message = null,
                success = false,
                roundScores = emptyList(),
            )
        }
    }
}
