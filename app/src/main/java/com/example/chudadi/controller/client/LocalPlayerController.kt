package com.example.chudadi.controller.client

import com.example.chudadi.ai.rulebased.AiDecision
import com.example.chudadi.ai.rulebased.RuleBasedAiPlayer
import com.example.chudadi.controller.game.MatchUiStateMapper
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private var aiTurnJob: Job? = null
    private var localSeatId: Int = MatchUiStateMapper.DEFAULT_LOCAL_SEAT_ID

    fun onRequestStartLocalMatch(
        seatConfigs: List<Triple<Int, String, SeatControllerType>>? = null,
        localSeatId: Int = 0,
        ruleSet: GameRuleSet = GameRuleSet.SOUTHERN,
    ) {
        aiTurnJob?.cancel()
        this.localSeatId = localSeatId
        currentMatch = if (seatConfigs != null) {
            serverController.startLocalMatch(
                ruleSet = ruleSet,
                seatConfigs = seatConfigs,
            )
        } else {
            serverController.startLocalMatch(ruleSet)
        }
        selectedCardIds = emptySet()
        lastActionMessage = null
        pushUiState()
        maybeResolveAiTurns()
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
        }
        pushUiState()
        if (result.success) {
            maybeResolveAiTurns()
        }
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
        }
        pushUiState()
        if (result.success) {
            maybeResolveAiTurns()
        }
    }

    fun onRequestRestartMatch() {
        onRequestStartLocalMatch()
    }

    fun onExitToHome() {
        aiTurnJob?.cancel()
        currentMatch = null
        selectedCardIds = emptySet()
        lastActionMessage = null
        pushUiState()
    }

    fun dispose() {
        aiTurnJob?.cancel()
    }

    private fun maybeResolveAiTurns() {
        aiTurnJob?.cancel()
        aiTurnJob =
            scope.launch {
                repeat(MAX_AI_CHAIN) {
                    val match = currentMatch ?: return@launch
                    if (match.phase == MatchPhase.FINISHED || match.activeSeatIndex == localSeatId) {
                        return@launch
                    }

                    val decision = aiPlayer.decideAction(match, match.activeSeatIndex)
                    val result =
                        when (decision) {
                            is AiDecision.Play ->
                                serverController.handleCommand(
                                    match = match,
                                    seatIndex = match.activeSeatIndex,
                                    command = PlayCardCommand(decision.cardIds),
                                )

                            AiDecision.Pass ->
                                serverController.handleCommand(
                                    match = match,
                                    seatIndex = match.activeSeatIndex,
                                    command = PassCommand,
                                )
                        }
                    currentMatch = result.match
                    lastActionMessage = result.message ?: GameActionMessageFormatter.format(result.error)
                    selectedCardIds = emptySet()
                    pushUiState()

                    if (!result.success) {
                        return@launch
                    }
                }
            }
    }

    private fun pushUiState() {
        _uiState.value = mapper.map(
            match = currentMatch,
            selectedCardIds = selectedCardIds,
            lastActionMessage = lastActionMessage,
            localSeatId = localSeatId,
        )
    }

    companion object {
        private const val MAX_AI_CHAIN = 32
    }
}
