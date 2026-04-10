package com.example.chudadi.controller.game

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import com.example.chudadi.ai.AIFactory
import com.example.chudadi.ai.base.AIDecision
import com.example.chudadi.ai.base.AIDifficulty
import com.example.chudadi.ai.base.AIPlayerController
import com.example.chudadi.ai.onnx.OnnxAIPlayerController
import com.example.chudadi.ai.onnx.OnnxInferenceException
import com.example.chudadi.ai.onnx.OnnxTimeoutException
import com.example.chudadi.ai.rulebased.AiDecision as RuleBasedDecision
import com.example.chudadi.ai.rulebased.RuleBasedAiPlayer
import com.example.chudadi.controller.server.LocalAuthoritativeController
import com.example.chudadi.model.game.engine.ActionResult
import com.example.chudadi.model.game.engine.GameEngine
import com.example.chudadi.model.game.entity.Match
import com.example.chudadi.model.game.entity.MatchPhase
import com.example.chudadi.model.game.entity.SeatControllerType
import com.example.chudadi.model.game.rule.GameRuleSet
import com.example.chudadi.model.game.snapshot.MatchUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class OnnxMatchViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val engine: GameEngine = GameEngine()
    private var currentRuleSet: GameRuleSet = GameRuleSet.SOUTHERN
    private val mapper: MatchUiStateMapper = MatchUiStateMapper(engine)
    private val context: Context get() = getApplication()

    private val aiScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val serverController = LocalAuthoritativeController(engine)

    private var aiControllersBySeatId: Map<Int, AIPlayerController> = emptyMap()
    private val fallbackRuleBasedAi = RuleBasedAiPlayer()

    private val _uiState = MutableStateFlow(MatchUiState())
    val uiState: StateFlow<MatchUiState> = _uiState.asStateFlow()

    private val _errorState = MutableStateFlow<AIErrorState?>(null)
    val errorState: StateFlow<AIErrorState?> = _errorState.asStateFlow()

    private var currentMatch: Match? = null
    private var selectedCardIds: Set<String> = emptySet()
    private var lastActionMessage: String? = null
    private var localSeatId: Int = DEFAULT_LOCAL_SEAT_ID
    private var lastSeatConfigs: List<SeatConfig>? = null
    private val inferenceFailureCountBySeat: MutableMap<Int, Int> = mutableMapOf()
    private val lastInferenceErrorReportAtBySeat: MutableMap<Int, Long> = mutableMapOf()

    init {
        AIFactory.preloadModels(context)
    }

    fun onRequestStartLocalMatch(
        seatConfigs: List<SeatConfig>? = null,
        localSeatId: Int = DEFAULT_LOCAL_SEAT_ID,
        ruleSet: GameRuleSet = GameRuleSet.SOUTHERN,
    ) {
        releaseAiControllers()
        currentRuleSet = ruleSet
        this.localSeatId = localSeatId
        this.lastSeatConfigs = seatConfigs
        inferenceFailureCountBySeat.clear()
        lastInferenceErrorReportAtBySeat.clear()

        val aiSeatConfigs = seatConfigs
            ?.filter { it.controllerType != SeatControllerType.HUMAN }
            ?.sortedBy { it.seatIndex }
            ?: buildDefaultAiSeatConfigs(localSeatId)

        var loadError: AIErrorState? = null
        aiControllersBySeatId = try {
            val creationResultsBySeat = aiSeatConfigs.associate { config ->
                val result = AIFactory.createAIPlayerWithStatus(
                    context = context,
                    seatIndex = config.seatIndex,
                    difficulty = config.aiDifficulty ?: AIDifficulty.NORMAL,
                    isOnnxAI = config.controllerType == SeatControllerType.ONNX_RL_AI,
                )
                config.seatIndex to result
            }

            val fallbackEntries = creationResultsBySeat.filterValues { it.isFallback }
            if (fallbackEntries.isNotEmpty()) {
                val seatIds = fallbackEntries.keys.sorted()
                val errorMessages = fallbackEntries.values.mapNotNull { it.errorMessage }.distinct()
                loadError = AIErrorState.ModelLoadFailed(
                    message = errorMessages.firstOrNull() ?: "Some AI seats have fallen back to rule-based AI",
                    fallbackToRuleBased = true,
                )
                android.util.Log.w(
                    "OnnxMatchViewModel",
                    "AI fallback seats: $seatIds, errors: $errorMessages",
                )
            }

            creationResultsBySeat.mapValues { it.value.controller }
        } catch (e: Exception) {
            android.util.Log.e("OnnxMatchViewModel", "Failed to create AI players", e)
            loadError = AIErrorState.ModelLoadFailed(
                message = "AI initialization failed: ${e.localizedMessage ?: "unknown error"}",
                fallbackToRuleBased = true,
            )
            emptyMap()
        }

        _errorState.value = loadError

        currentMatch = if (seatConfigs != null) {
            serverController.startLocalMatch(
                ruleSet = currentRuleSet,
                seatConfigs = seatConfigs.map { Triple(it.seatIndex, it.name, it.controllerType) },
            )
        } else {
            serverController.startLocalMatch(ruleSet = currentRuleSet)
        }

        selectedCardIds = emptySet()
        lastActionMessage = loadError?.message
        pushUiState()
        maybeResolveAiTurns()
    }

    fun onToggleCardSelection(cardId: String) {
        if (!_uiState.value.isHumanTurn) return

        selectedCardIds = if (cardId in selectedCardIds) {
            selectedCardIds - cardId
        } else {
            selectedCardIds + cardId
        }
        pushUiState()
    }

    fun onClearSelection() {
        if (selectedCardIds.isEmpty()) return
        selectedCardIds = emptySet()
        pushUiState()
    }

    fun onRequestPlayCards() {
        val match = currentMatch ?: return
        val result = serverController.handleCommand(
            match = match,
            seatIndex = localSeatId,
            command = com.example.chudadi.network.protocol.PlayCardCommand(selectedCardIds),
        )
        currentMatch = result.match
        lastActionMessage = formatActionMessage(result.message, result.error)
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
            command = com.example.chudadi.network.protocol.PassCommand,
        )
        currentMatch = result.match
        lastActionMessage = formatActionMessage(result.message, result.error)
        if (result.success) {
            selectedCardIds = emptySet()
        }
        pushUiState()
        if (result.success) {
            maybeResolveAiTurns()
        }
    }

    fun onRequestRestartMatch() {
        _errorState.value = null
        onRequestStartLocalMatch(
            seatConfigs = lastSeatConfigs,
            localSeatId = localSeatId,
            ruleSet = currentRuleSet,
        )
    }

    fun clearErrorState() {
        _errorState.value = null
    }

    fun onExitToHome() {
        currentMatch = null
        selectedCardIds = emptySet()
        lastActionMessage = null
        _errorState.value = null
        releaseAiControllers()
        inferenceFailureCountBySeat.clear()
        lastInferenceErrorReportAtBySeat.clear()
        pushUiState()
    }

    private fun maybeResolveAiTurns() {
        aiScope.launch(Dispatchers.Default) {
            var chainCount = 0
            while (chainCount < MAX_AI_CHAIN) {
                val match = currentMatch ?: break
                if (match.phase == MatchPhase.FINISHED) break
                if (match.activeSeatIndex == localSeatId) break

                val activeSeat = match.activeSeatIndex
                val aiPlayer = aiControllersBySeatId[activeSeat]
                var aiErrorMessage: String? = null
                val decision = if (aiPlayer != null) {
                    try {
                        aiPlayer.requestDecision(match, currentRuleSet).also { onInferenceSuccess(activeSeat) }
                    } catch (e: OnnxTimeoutException) {
                        android.util.Log.e("OnnxMatchViewModel", "ONNX AI timeout, using fallback", e)
                        aiErrorMessage = "Seat $activeSeat ONNX timeout, fallback to rule-based AI"
                        onInferenceFailed(activeSeat, e)
                        null
                    } catch (e: OnnxInferenceException) {
                        android.util.Log.e("OnnxMatchViewModel", "ONNX AI failed, using fallback", e)
                        aiErrorMessage = "Seat $activeSeat ONNX inference failed, fallback to rule-based AI"
                        onInferenceFailed(activeSeat, e)
                        null
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        android.util.Log.e("OnnxMatchViewModel", "Unexpected AI error, stop AI resolution", e)
                        val criticalMessage =
                            "Seat $activeSeat AI unexpected failure: ${e.localizedMessage ?: "unknown error"}"
                        _errorState.value = AIErrorState.CriticalError(
                            message = criticalMessage,
                            exception = e,
                        )
                        lastActionMessage = criticalMessage
                        launch(Dispatchers.Main) {
                            pushUiState()
                        }
                        return@launch
                    }
                } else {
                    null
                }

                val result = when (decision) {
                    is AIDecision.PlayCards -> {
                        serverController.handleCommand(
                            match = match,
                            seatIndex = activeSeat,
                            command = com.example.chudadi.network.protocol.PlayCardCommand(
                                decision.cards.map { it.id }.toSet(),
                            ),
                        )
                    }

                    AIDecision.Pass -> {
                        serverController.handleCommand(
                            match = match,
                            seatIndex = activeSeat,
                            command = com.example.chudadi.network.protocol.PassCommand,
                        )
                    }

                    is AIDecision.Error -> {
                        android.util.Log.e(
                            "OnnxMatchViewModel",
                            "AI decision error on seat $activeSeat: ${decision.reason}",
                            decision.exception,
                        )
                        _errorState.value = AIErrorState.CriticalError(
                            message = "Seat $activeSeat AI decision failed: ${decision.reason}",
                            exception = decision.exception,
                        )
                        aiErrorMessage = "Seat $activeSeat AI decision failed, fallback to rule-based AI"
                        executeRuleBasedFallback(match = match, seatIndex = activeSeat)
                    }

                    else -> {
                        executeRuleBasedFallback(match = match, seatIndex = activeSeat)
                    }
                }

                val resolvedResult = if (result.success) {
                    result
                } else {
                    android.util.Log.w(
                        "OnnxMatchViewModel",
                        "Seat $activeSeat action failed, retrying with rule-based fallback. error=${result.error}",
                    )
                    aiErrorMessage = aiErrorMessage ?: "Seat $activeSeat AI action invalid, fallback to rule-based AI"
                    val fallbackResult = executeRuleBasedFallback(match = result.match, seatIndex = activeSeat)
                    if (fallbackResult.success) {
                        fallbackResult
                    } else {
                        val fallbackErrorMessage = formatActionMessage(
                            message = null,
                            error = fallbackResult.error,
                        )
                        val criticalMessage = "Seat $activeSeat fallback failed: $fallbackErrorMessage"
                        _errorState.value = AIErrorState.CriticalError(message = criticalMessage)
                        currentMatch = fallbackResult.match
                        lastActionMessage = criticalMessage
                        selectedCardIds = emptySet()
                        launch(Dispatchers.Main) {
                            pushUiState()
                        }
                        break
                    }
                }

                currentMatch = resolvedResult.match
                lastActionMessage = aiErrorMessage
                    ?: resolvedResult.message
                    ?: com.example.chudadi.controller.client.GameActionMessageFormatter.format(resolvedResult.error)
                selectedCardIds = emptySet()

                launch(Dispatchers.Main) {
                    pushUiState()
                }

                chainCount++
            }
        }
    }

    private fun executeRuleBasedFallback(
        match: Match,
        seatIndex: Int,
    ): ActionResult {
        return when (val ruleDecision = fallbackRuleBasedAi.decideAction(match, seatIndex)) {
            is RuleBasedDecision.Play -> {
                serverController.handleCommand(
                    match = match,
                    seatIndex = seatIndex,
                    command = com.example.chudadi.network.protocol.PlayCardCommand(ruleDecision.cardIds),
                )
            }

            RuleBasedDecision.Pass -> {
                serverController.handleCommand(
                    match = match,
                    seatIndex = seatIndex,
                    command = com.example.chudadi.network.protocol.PassCommand,
                )
            }
        }
    }

    private fun buildDefaultAiSeatConfigs(localSeatId: Int): List<SeatConfig> {
        return (0 until TOTAL_SEATS)
            .asSequence()
            .filter { it != localSeatId }
            .map { seatId ->
                SeatConfig(
                    seatIndex = seatId,
                    name = "AIN${seatId + 1}",
                    controllerType = SeatControllerType.RULE_BASED_AI,
                    aiDifficulty = AIDifficulty.NORMAL,
                )
            }
            .toList()
    }

    private fun onInferenceSuccess(seatId: Int) {
        inferenceFailureCountBySeat.remove(seatId)
    }

    private fun onInferenceFailed(seatId: Int, error: Throwable) {
        val failureCount = (inferenceFailureCountBySeat[seatId] ?: 0) + 1
        inferenceFailureCountBySeat[seatId] = failureCount
        val now = System.currentTimeMillis()
        val lastReportAt = lastInferenceErrorReportAtBySeat[seatId] ?: 0L
        if (failureCount == 1 || now - lastReportAt >= INFERENCE_ERROR_REPORT_INTERVAL_MS) {
            _errorState.value = AIErrorState.InferenceFailed(
                message = buildInferenceFailedMessage(seatId, failureCount, error),
                seatIndex = seatId,
            )
            lastInferenceErrorReportAtBySeat[seatId] = now
        }
    }

    private fun formatActionMessage(
        message: String?,
        error: com.example.chudadi.model.game.engine.GameActionError?,
    ): String? {
        return message ?: com.example.chudadi.controller.client.GameActionMessageFormatter.format(error)
    }

    private fun buildInferenceFailedMessage(
        seatId: Int,
        failureCount: Int,
        error: Throwable,
    ): String {
        val reason = error.localizedMessage ?: "unknown error"
        return "Seat $seatId ONNX inference failed ($failureCount): $reason"
    }

    private fun releaseAiControllers() {
        aiScope.coroutineContext.cancelChildren()
        aiControllersBySeatId.values.forEach { (it as? OnnxAIPlayerController)?.release() }
        aiControllersBySeatId = emptyMap()
    }

    private fun pushUiState() {
        _uiState.value = mapper.map(
            match = currentMatch,
            selectedCardIds = selectedCardIds,
            lastActionMessage = lastActionMessage,
            localSeatId = localSeatId,
        )
    }

    override fun onCleared() {
        releaseAiControllers()
        aiScope.coroutineContext.cancel()
        super.onCleared()
    }

    companion object {
        private const val DEFAULT_LOCAL_SEAT_ID = 0
        private const val TOTAL_SEATS = 4
        private const val MAX_AI_CHAIN = 32
        private const val INFERENCE_ERROR_REPORT_INTERVAL_MS = 3000L
    }
}

sealed class AIErrorState {
    abstract val message: String
    abstract val isRecoverable: Boolean

    data class ModelLoadFailed(
        override val message: String,
        val fallbackToRuleBased: Boolean = true,
    ) : AIErrorState() {
        override val isRecoverable: Boolean = true
    }

    data class InferenceFailed(
        override val message: String,
        val seatIndex: Int,
    ) : AIErrorState() {
        override val isRecoverable: Boolean = true
    }

    data class CriticalError(
        override val message: String,
        val exception: Throwable? = null,
    ) : AIErrorState() {
        override val isRecoverable: Boolean = false
    }
}
