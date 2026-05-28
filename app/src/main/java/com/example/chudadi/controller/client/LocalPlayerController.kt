@file:Suppress("TooManyFunctions")

package com.example.chudadi.controller.client

import com.example.chudadi.ai.base.AIDecision
import com.example.chudadi.ai.rulebased.RuleBasedAiPlayer
import com.example.chudadi.controller.game.MatchTurnTimer
import com.example.chudadi.controller.game.MatchUiStateMapper
import com.example.chudadi.controller.game.SeatConfig
import com.example.chudadi.controller.server.LocalAuthoritativeController
import com.example.chudadi.model.game.entity.Match
import com.example.chudadi.model.game.entity.MatchPhase
import com.example.chudadi.model.game.entity.SeatControllerType
import com.example.chudadi.model.game.rule.GameRuleSet
import com.example.chudadi.model.game.snapshot.MatchUiState
import com.example.chudadi.network.protocol.PassCommand
import com.example.chudadi.network.protocol.PlayCardCommand
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class LocalPlayerController(
    private val serverController: LocalAuthoritativeController,
    private val aiPlayer: RuleBasedAiPlayer,
    private val mapper: MatchUiStateMapper,
    private val scope: CoroutineScope,
) {
    private val _uiState = MutableStateFlow(MatchUiState())
    val uiState: StateFlow<MatchUiState> = _uiState.asStateFlow()

    private var currentMatch: Match? = null
    private var selectedCardIds: Set<String> = emptySet()
    private var lastActionMessage: String? = null
    private var turnLoopJob: Job? = null
    private var localSeatId: Int = MatchUiStateMapper.DEFAULT_LOCAL_SEAT_ID
    private val turnTimer = MatchTurnTimer()

    private var lastSeatConfigs: List<SeatConfig>? = null
    private var lastLocalSeatId: Int = MatchUiStateMapper.DEFAULT_LOCAL_SEAT_ID
    private var lastRuleSet: GameRuleSet = GameRuleSet.SOUTHERN
    private var lastAiMoveDelayMillis: Long = 0L
    private var aiMoveDelayMillis: Long = 0L

    fun onRequestStartLocalMatch(
        seatConfigs: List<SeatConfig>? = null,
        localSeatId: Int = 0,
        ruleSet: GameRuleSet = GameRuleSet.SOUTHERN,
        aiMoveDelayMillis: Long = 0L,
    ) {
        turnLoopJob?.cancel()

        val effectiveSeatConfigs: List<SeatConfig>
        val effectiveLocalSeatId: Int
        val effectiveRuleSet: GameRuleSet

        if (seatConfigs != null) {
            lastSeatConfigs = seatConfigs
            lastLocalSeatId = localSeatId
            lastRuleSet = ruleSet
            lastAiMoveDelayMillis = aiMoveDelayMillis
            effectiveSeatConfigs = seatConfigs
            effectiveLocalSeatId = localSeatId
            effectiveRuleSet = ruleSet
        } else {
            effectiveSeatConfigs = lastSeatConfigs ?: emptyList()
            effectiveLocalSeatId = lastLocalSeatId
            effectiveRuleSet = lastRuleSet
        }

        this.localSeatId = effectiveLocalSeatId
        this.aiMoveDelayMillis = effectiveSeatConfigs.let { lastAiMoveDelayMillis }

        currentMatch = serverController.startLocalMatch(
            ruleSet = effectiveRuleSet,
            seatConfigs = effectiveSeatConfigs.map { Triple(it.seatId, it.name, it.controllerType) },
        )
        selectedCardIds = emptySet()
        lastActionMessage = null
        scheduleCurrentTurn()
        pushUiState()
        startTurnLoop()
    }

    fun onToggleCardSelection(cardId: String) {
        if (!_uiState.value.isHumanTurn) {
            return
        }

        selectedCardIds =
            if (cardId in selectedCardIds) {
                selectedCardIds - cardId
            } else {
                selectedCardIds + cardId
            }
        pushUiState()
    }

    fun onClearSelection() {
        if (selectedCardIds.isEmpty()) {
            return
        }
        selectedCardIds = emptySet()
        pushUiState()
    }

    fun onRequestPlayCards() {
        val match = currentMatch ?: return
        val result = serverController.handleCommand(
            match = match,
            seatIndex = localSeatId,
            command = PlayCardCommand(selectedCardIds),
        )
        currentMatch = result.match
        lastActionMessage = result.message ?: GameActionMessageFormatter.format(result.error)
        if (result.success) {
            selectedCardIds = emptySet()
            scheduleCurrentTurn()
        }
        pushUiState()
    }

    fun onRequestPass() {
        val match = currentMatch ?: return
        val result = serverController.handleCommand(
            match = match,
            seatIndex = localSeatId,
            command = PassCommand,
        )
        currentMatch = result.match
        lastActionMessage = result.message ?: GameActionMessageFormatter.format(result.error)
        if (result.success) {
            selectedCardIds = emptySet()
            scheduleCurrentTurn()
        }
        pushUiState()
    }

    fun onRequestRestartMatch() {
        onRequestStartLocalMatch()
    }

    fun onExitToHome() {
        turnLoopJob?.cancel()
        currentMatch = null
        selectedCardIds = emptySet()
        lastActionMessage = null
        turnTimer.reset()
        pushUiState()
    }

    fun dispose() {
        turnLoopJob?.cancel()
    }

    private fun startTurnLoop() {
        turnLoopJob?.cancel()
        turnLoopJob = scope.launch {
            while (isActive) {
                delay(TURN_LOOP_INTERVAL_MS)
                if (!tickTurnLoop()) {
                    break
                }
            }
        }
    }

    private fun tickTurnLoop(): Boolean {
        val match = currentMatch ?: return false
        val shouldContinue = when {
            match.phase == MatchPhase.FINISHED -> {
                turnTimer.reset()
                false
            }

            !turnTimer.isExpired() -> true

            else -> {
                resolveExpiredTurn(match)
                currentMatch != null
            }
        }
        pushUiState()
        return shouldContinue
    }

    private fun resolveExpiredTurn(match: Match) {
        val seatId = match.activeSeatIndex
        val seatName = match.seats.first { it.seatId == seatId }.displayName
        val result = when {
            turnTimer.isCurrentActorAi() -> resolveAiAction(
                match = match,
                seatId = seatId,
                lastMessage = "$seatName 思考中",
            )

            serverController.canPass(match = match, seatIndex = seatId) -> {
                serverController.handleCommand(
                    match = match,
                    seatIndex = seatId,
                    command = PassCommand,
                ).also {
                    lastActionMessage = if (it.success) "$seatName 超时过牌" else {
                        it.message ?: GameActionMessageFormatter.format(it.error)
                    }
                }
            }

            else -> resolveAiAction(
                match = match,
                seatId = seatId,
                lastMessage = "$seatName 超时，系统已代出",
            )
        }

        currentMatch = result.match
        selectedCardIds = emptySet()
        if (result.success) {
            scheduleCurrentTurn()
        } else if (currentMatch?.phase == MatchPhase.FINISHED) {
            turnTimer.reset()
        }
    }

    private fun resolveAiAction(
        match: Match,
        seatId: Int,
        lastMessage: String?,
    ) = when (val decision = aiPlayer.decideAction(match, seatId)) {
        is AIDecision.PlayCards -> serverController.handleCommand(
            match = match,
            seatIndex = seatId,
            command = PlayCardCommand(decision.cards.map { it.id }.toSet()),
        )
        AIDecision.Pass -> serverController.handleCommand(
            match = match,
            seatIndex = seatId,
            command = PassCommand,
        )
        is AIDecision.Error -> serverController.handleCommand(
            match = match,
            seatIndex = seatId,
            command = PassCommand,
        )
    }.also { result ->
        lastActionMessage = result.message ?: GameActionMessageFormatter.format(result.error) ?: lastMessage
    }

    private fun scheduleCurrentTurn() {
        val match = currentMatch
        if (match == null || match.phase == MatchPhase.FINISHED) {
            turnTimer.reset()
            return
        }
        val activeSeat = match.seats.first { it.seatId == match.activeSeatIndex }
        val isAiDrivenTurn = activeSeat.controllerType == SeatControllerType.RULE_BASED_AI ||
            activeSeat.controllerType == SeatControllerType.ONNX_RL_AI
        val isLeadingTurn = match.trickState.currentCombination == null
        turnTimer.scheduleTurn(
            isAiDrivenTurn = isAiDrivenTurn,
            isLeadingTurn = isLeadingTurn,
            aiDelayMillis = if (isAiDrivenTurn) aiMoveDelayMillis else 0L,
        )
    }

    private fun pushUiState() {
        _uiState.value = mapper.map(
            match = currentMatch,
            selectedCardIds = selectedCardIds,
            lastActionMessage = lastActionMessage,
            localSeatId = localSeatId,
        ).copy(remainingTurnSeconds = turnTimer.remainingTurnSeconds())
    }

    companion object {
        private const val TURN_LOOP_INTERVAL_MS = 250L
    }
}
