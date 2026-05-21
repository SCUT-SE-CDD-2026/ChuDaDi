@file:Suppress("TooManyFunctions", "MaxLineLength")

package com.example.chudadi.controller.server

import com.example.chudadi.ai.rulebased.AiDecision
import com.example.chudadi.ai.rulebased.RuleBasedAiPlayer
import com.example.chudadi.controller.client.GameActionMessageFormatter
import com.example.chudadi.controller.game.MatchTurnTimer
import com.example.chudadi.controller.game.MatchTurnTimerSnapshot
import com.example.chudadi.controller.game.MatchUiStateMapper
import com.example.chudadi.model.game.engine.ActionResult
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

class BluetoothAuthoritativeMatchController private constructor(
    private val engine: GameEngine,
    private val mapper: MatchUiStateMapper,
    private val aiActionResolver: AiActionResolver,
    @Suppress("UNUSED_PARAMETER") private val constructorMarker: Unit,
) {
    constructor(
        engine: GameEngine,
        mapper: MatchUiStateMapper,
        aiPlayer: RuleBasedAiPlayer,
    ) : this(
        engine = engine,
        mapper = mapper,
        aiActionResolver = RuleBasedAiActionResolver(aiPlayer),
        constructorMarker = Unit,
    )

    internal constructor(
        engine: GameEngine,
        mapper: MatchUiStateMapper,
        aiActionResolver: AiActionResolver,
    ) : this(
        engine = engine,
        mapper = mapper,
        aiActionResolver = aiActionResolver,
        constructorMarker = Unit,
    )

    private val lock = Any()
    private var currentMatch: Match? = null
    private val turnTimer = MatchTurnTimer()
    private var disconnectedSeatIds: Set<Int> = emptySet()
    private var aiMoveDelayMillis: Long = MatchTurnTimer.AI_DELAY_MIN_MS

    fun startMatch(
        seatConfigs: List<Triple<Int, String, SeatControllerType>>,
        ruleSet: GameRuleSet,
        aiMoveDelayMillis: Long = MatchTurnTimer.AI_DELAY_MIN_MS,
    ): Match = synchronized(lock) {
        this.aiMoveDelayMillis = aiMoveDelayMillis.coerceAtLeast(0L)
        val match = engine.startLocalMatch(ruleSet = ruleSet, seatConfigs = seatConfigs)
        currentMatch = match
        disconnectedSeatIds = emptySet()
        scheduleCurrentTurnLocked(match)
        match
    }

    fun currentMatch(): Match? = synchronized(lock) {
        currentMatch
    }

    fun buildSnapshotForSeat(localSeatId: Int, lastActionMessage: String? = null): MatchUiState = synchronized(lock) {
        buildSnapshotForSeatLocked(localSeatId = localSeatId, lastActionMessage = lastActionMessage)
    }

    fun handlePlayRequest(
        seatId: Int,
        selectedCardIds: Set<String>,
        expectedTurn: AuthoritativeTurnSnapshot? = null,
    ): MatchActionEnvelope = synchronized(lock) {
        if (expectedTurn != null && !isCurrentTurnLocked(expectedTurn)) {
            return@synchronized MatchActionEnvelope.empty()
        }
        val match = currentMatch ?: return@synchronized MatchActionEnvelope.empty()
        val result = PlayCardCommand(selectedCardIds).execute(match = match, seatIndex = seatId, engine = engine)
        currentMatch = result.match
        if (result.success) {
            scheduleCurrentTurnLocked(result.match)
        }
        MatchActionEnvelope(
            match = result.match,
            message = result.message ?: GameActionMessageFormatter.format(result.error),
            success = result.success,
            roundScores = result.match.result?.scoreSummary?.roundScores.orEmpty(),
        )
    }

    fun handlePassRequest(
        seatId: Int,
        expectedTurn: AuthoritativeTurnSnapshot? = null,
    ): MatchActionEnvelope = synchronized(lock) {
        if (expectedTurn != null && !isCurrentTurnLocked(expectedTurn)) {
            return@synchronized MatchActionEnvelope.empty()
        }
        val match = currentMatch ?: return@synchronized MatchActionEnvelope.empty()
        val result = PassCommand.execute(match = match, seatIndex = seatId, engine = engine)
        currentMatch = result.match
        if (result.success) {
            scheduleCurrentTurnLocked(result.match)
        }
        MatchActionEnvelope(
            match = result.match,
            message = result.message ?: GameActionMessageFormatter.format(result.error),
            success = result.success,
            roundScores = result.match.result?.scoreSummary?.roundScores.orEmpty(),
        )
    }

    fun resolveCurrentAiTurn(
        lastMessage: String?,
        expectedTurn: AuthoritativeTurnSnapshot? = null,
    ): MatchActionEnvelope {
        return resolveCurrentSeatByAi(
            lastMessage = lastMessage,
            allowHumanSeatProxy = false,
            expectedTurn = expectedTurn,
        )
    }

    fun resolveCurrentSeatByAi(
        lastMessage: String?,
        expectedTurn: AuthoritativeTurnSnapshot? = null,
    ): MatchActionEnvelope {
        return resolveCurrentSeatByAi(
            lastMessage = lastMessage,
            allowHumanSeatProxy = true,
            expectedTurn = expectedTurn,
        )
    }

    fun markDisconnected(
        seatId: Int,
        disconnected: Boolean,
        expectedTurn: AuthoritativeTurnSnapshot? = null,
    ): Boolean = synchronized(lock) {
        if (expectedTurn != null && !isCurrentTurnLocked(expectedTurn)) {
            return@synchronized false
        }
        markDisconnectedLocked(seatId = seatId, disconnected = disconnected)
        true
    }

    fun onSeatReconnected(seatId: Int) = synchronized(lock) {
        markDisconnectedLocked(seatId = seatId, disconnected = false)
        val match = currentMatch ?: return@synchronized
        if (match.phase != MatchPhase.FINISHED && match.activeSeatIndex == seatId) {
            scheduleCurrentTurnLocked(match, forceHumanWindow = true)
        }
    }

    fun isCurrentTurnExpired(nowMillis: Long = System.currentTimeMillis()): Boolean = synchronized(lock) {
        turnTimer.isExpired(nowMillis)
    }

    fun isCurrentTurnExpired(
        turnSnapshot: AuthoritativeTurnSnapshot,
        nowMillis: Long = System.currentTimeMillis(),
    ): Boolean = synchronized(lock) {
        isCurrentTurnLocked(turnSnapshot) && turnTimer.isExpired(nowMillis)
    }

    fun remainingTurnSeconds(nowMillis: Long = System.currentTimeMillis()): Int? = synchronized(lock) {
        turnTimer.remainingTurnSeconds(nowMillis)
    }

    fun isCurrentActorAi(): Boolean = synchronized(lock) {
        turnTimer.isCurrentActorAi()
    }

    fun isCurrentActorAi(turnSnapshot: AuthoritativeTurnSnapshot): Boolean = synchronized(lock) {
        isCurrentTurnLocked(turnSnapshot) && turnTimer.isCurrentActorAi()
    }

    fun canCurrentSeatPass(): Boolean = synchronized(lock) {
        val match = currentMatch ?: return@synchronized false
        engine.canPass(match = match, seatIndex = match.activeSeatIndex)
    }

    fun canCurrentSeatPass(turnSnapshot: AuthoritativeTurnSnapshot): Boolean = synchronized(lock) {
        val match = currentMatch ?: return@synchronized false
        isCurrentTurnLocked(turnSnapshot) && engine.canPass(match = match, seatIndex = match.activeSeatIndex)
    }

    fun currentTurnSnapshot(): AuthoritativeTurnSnapshot? = synchronized(lock) {
        currentTurnSnapshotLocked()
    }

    fun buildMatchStartedMessage(localSeatId: Int): GameWireMessage.MatchStarted = synchronized(lock) {
        val match = currentMatch ?: error("Match not started")
        val snapshot = buildSnapshotForSeatLocked(localSeatId).toRemoteMatchSnapshot(match.matchId)
        GameWireMessage.MatchStarted(localSeatId = localSeatId, snapshot = snapshot)
    }

    fun buildSnapshotMessage(
        localSeatId: Int,
        lastActionMessage: String?,
    ): GameWireMessage.MatchSnapshotMessage = synchronized(lock) {
        val match = currentMatch ?: error("Match not started")
        val snapshot = buildSnapshotForSeatLocked(localSeatId, lastActionMessage).toRemoteMatchSnapshot(match.matchId)
        GameWireMessage.MatchSnapshotMessage(snapshot)
    }

    fun closeMatch(reason: String): GameWireMessage.MatchClosed = synchronized(lock) {
        currentMatch = null
        turnTimer.reset()
        disconnectedSeatIds = emptySet()
        GameWireMessage.MatchClosed(reason)
    }

    fun clearCurrentMatch() = synchronized(lock) {
        currentMatch = null
        turnTimer.reset()
        disconnectedSeatIds = emptySet()
    }

    private fun buildSnapshotForSeatLocked(localSeatId: Int, lastActionMessage: String? = null): MatchUiState {
        val match = currentMatch ?: return MatchUiState()
        val base = mapper.map(
            match = match,
            selectedCardIds = emptySet(),
            lastActionMessage = lastActionMessage,
            localSeatId = localSeatId,
        )
        return base.copy(
            remainingTurnSeconds = turnTimer.remainingTurnSeconds(),
            isLocalDisconnected = localSeatId in disconnectedSeatIds,
            opponentSummaries = base.opponentSummaries.map { opponent ->
                opponent.copy(isDisconnected = opponent.authoritySeatId in disconnectedSeatIds)
            },
        )
    }

    private fun markDisconnectedLocked(seatId: Int, disconnected: Boolean) {
        disconnectedSeatIds = if (disconnected) disconnectedSeatIds + seatId else disconnectedSeatIds - seatId
        val match = currentMatch ?: return
        if (match.phase != MatchPhase.FINISHED && match.activeSeatIndex == seatId) {
            scheduleCurrentTurnLocked(match)
        }
    }

    private fun scheduleCurrentTurnLocked(match: Match, forceHumanWindow: Boolean = false) {
        if (match.phase == MatchPhase.FINISHED) {
            turnTimer.reset()
            return
        }
        val activeSeat = match.seats.first { it.seatId == match.activeSeatIndex }
        val isAiDrivenTurn = !forceHumanWindow && isAiDrivenSeatLocked(activeSeat.seatId, activeSeat.controllerType)
        turnTimer.scheduleTurn(
            isAiDrivenTurn = isAiDrivenTurn,
            aiDelayMillis = if (isAiDrivenTurn) aiMoveDelayMillis else 0L,
        )
    }

    private fun isAiDrivenSeatLocked(seatId: Int, controllerType: SeatControllerType): Boolean {
        return controllerType == SeatControllerType.RULE_BASED_AI || seatId in disconnectedSeatIds
    }

    private fun resolveCurrentSeatByAi(
        lastMessage: String?,
        allowHumanSeatProxy: Boolean,
        expectedTurn: AuthoritativeTurnSnapshot?,
    ): MatchActionEnvelope {
        val preparation = synchronized(lock) {
            prepareAiActionLocked(
                lastMessage = lastMessage,
                allowHumanSeatProxy = allowHumanSeatProxy,
                expectedTurn = expectedTurn,
            )
        }
        if (preparation is AiPreparation.Ready) return preparation.envelope

        val pendingAction = (preparation as AiPreparation.Pending).action
        val action = resolveAiAction(pendingAction.match, pendingAction.seatId)
        return synchronized(lock) {
            if (!isCurrentTurnLocked(pendingAction.turnSnapshot)) {
                return@synchronized MatchActionEnvelope.empty()
            }
            currentMatch = action.match
            if (action.success) {
                scheduleCurrentTurnLocked(action.match)
            }
            MatchActionEnvelope(
                match = action.match,
                message = action.message ?: action.error?.let(GameActionMessageFormatter::format) ?: pendingAction.lastMessage,
                success = action.success,
                roundScores = action.match.result?.scoreSummary?.roundScores.orEmpty(),
            )
        }
    }

    private fun prepareAiActionLocked(
        lastMessage: String?,
        allowHumanSeatProxy: Boolean,
        expectedTurn: AuthoritativeTurnSnapshot?,
    ): AiPreparation {
        val match = currentMatch ?: return AiPreparation.Ready(MatchActionEnvelope.empty())
        if (match.phase == MatchPhase.FINISHED) {
            return AiPreparation.Ready(
                MatchActionEnvelope(
                    match = match,
                    message = lastMessage,
                    success = false,
                    roundScores = match.result?.scoreSummary?.roundScores.orEmpty(),
                ),
            )
        }
        val turnSnapshot = currentTurnSnapshotLocked()
        return when {
            turnSnapshot == null -> AiPreparation.Ready(MatchActionEnvelope.empty())
            expectedTurn != null && !isCurrentTurnLocked(expectedTurn) -> {
                AiPreparation.Ready(MatchActionEnvelope.empty())
            }
            else -> prepareActiveAiActionLocked(
                match = match,
                turnSnapshot = turnSnapshot,
                lastMessage = lastMessage,
                allowHumanSeatProxy = allowHumanSeatProxy,
            )
        }
    }

    private fun prepareActiveAiActionLocked(
        match: Match,
        turnSnapshot: AuthoritativeTurnSnapshot,
        lastMessage: String?,
        allowHumanSeatProxy: Boolean,
    ): AiPreparation {
        val seat = match.seats.first { it.seatId == match.activeSeatIndex }
        val canUseAiProxy = seat.controllerType == SeatControllerType.RULE_BASED_AI || allowHumanSeatProxy
        return if (canUseAiProxy) {
            AiPreparation.Pending(
                PendingAiAction(
                    match = match,
                    seatId = match.activeSeatIndex,
                    turnSnapshot = turnSnapshot,
                    lastMessage = lastMessage,
                ),
            )
        } else {
            AiPreparation.Ready(MatchActionEnvelope(match, lastMessage, success = false, roundScores = emptyList()))
        }
    }

    private fun resolveAiAction(match: Match, seatId: Int): ActionResult {
        return aiActionResolver.resolve(match = match, seatId = seatId, engine = engine)
    }

    private fun currentTurnSnapshotLocked(): AuthoritativeTurnSnapshot? {
        val match = currentMatch ?: return null
        val timerSnapshot = turnTimer.snapshot() ?: return null
        return AuthoritativeTurnSnapshot(
            matchId = match.matchId,
            activeSeatIndex = match.activeSeatIndex,
            timerSnapshot = timerSnapshot,
        )
    }

    private fun isCurrentTurnLocked(turnSnapshot: AuthoritativeTurnSnapshot): Boolean {
        val match = currentMatch ?: return false
        return match.matchId == turnSnapshot.matchId &&
            match.activeSeatIndex == turnSnapshot.activeSeatIndex &&
            turnTimer.snapshot() == turnSnapshot.timerSnapshot
    }

    private data class PendingAiAction(
        val match: Match,
        val seatId: Int,
        val turnSnapshot: AuthoritativeTurnSnapshot,
        val lastMessage: String?,
    )

    private sealed interface AiPreparation {
        data class Pending(val action: PendingAiAction) : AiPreparation

        data class Ready(val envelope: MatchActionEnvelope) : AiPreparation
    }
}

internal fun interface AiActionResolver {
    fun resolve(
        match: Match,
        seatId: Int,
        engine: GameEngine,
    ): ActionResult
}

private class RuleBasedAiActionResolver(
    private val aiPlayer: RuleBasedAiPlayer,
) : AiActionResolver {
    override fun resolve(
        match: Match,
        seatId: Int,
        engine: GameEngine,
    ): ActionResult {
        return when (val decision = aiPlayer.decideAction(match, seatId)) {
            is AiDecision.Play -> PlayCardCommand(decision.cardIds).execute(match, seatId, engine)
            AiDecision.Pass -> PassCommand.execute(match, seatId, engine)
        }
    }
}

data class AuthoritativeTurnSnapshot(
    val matchId: String,
    val activeSeatIndex: Int,
    val timerSnapshot: MatchTurnTimerSnapshot,
)

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
