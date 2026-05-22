package com.example.chudadi.controller.game

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.chudadi.ai.AIFactory
import com.example.chudadi.ai.base.AIDecision
import com.example.chudadi.ai.base.AIDifficulty
import com.example.chudadi.ai.base.AIPlayerController
import com.example.chudadi.ai.onnx.OnnxTimeoutException
import com.example.chudadi.ai.onnx.OnnxInferenceException
import com.example.chudadi.ai.rulebased.RuleBasedAIAdapter
import com.example.chudadi.controller.server.LocalAuthoritativeController
import com.example.chudadi.model.game.engine.ActionResult
import com.example.chudadi.model.game.engine.GameEngine
import com.example.chudadi.model.game.entity.Match
import com.example.chudadi.model.game.entity.MatchPhase
import com.example.chudadi.model.game.entity.SeatControllerType
import com.example.chudadi.model.game.rule.GameRuleSet
import com.example.chudadi.model.game.snapshot.MatchUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
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

    private val _uiState = MutableStateFlow(MatchUiState())
    val uiState: StateFlow<MatchUiState> = _uiState.asStateFlow()

    private val _errorState = MutableStateFlow<AIErrorState?>(null)
    val errorState: StateFlow<AIErrorState?> = _errorState.asStateFlow()

    private var currentMatch: Match? = null
    private var selectedCardIds: Set<String> = emptySet()
    private var lastActionMessage: String? = null
    private var localSeatId: Int = DEFAULT_LOCAL_SEAT_ID
    private var lastSeatConfigs: List<SeatConfig>? = null
    private var aiMoveDelayMillis: Long = 0L
    private var lastAiMoveDelayMillis: Long = 0L
    private val inferenceFailureCountBySeat: MutableMap<Int, Int> = mutableMapOf()
    private val lastInferenceErrorReportAtBySeat: MutableMap<Int, Long> = mutableMapOf()
    private var aiTurnJob: Job? = null
    private var humanTurnLoopJob: Job? = null
    private val turnTimer = MatchTurnTimer()

    init {
        AIFactory.preloadModels(context)
    }

    fun onRequestStartLocalMatch(
        seatConfigs: List<SeatConfig>? = null,
        localSeatId: Int = DEFAULT_LOCAL_SEAT_ID,
        ruleSet: GameRuleSet = GameRuleSet.SOUTHERN,
        aiMoveDelayMillis: Long = 0L,
    ) {
        currentRuleSet = ruleSet
        this.localSeatId = localSeatId
        this.lastSeatConfigs = seatConfigs
        this.aiMoveDelayMillis = aiMoveDelayMillis.coerceAtLeast(0L)
        this.lastAiMoveDelayMillis = this.aiMoveDelayMillis
        inferenceFailureCountBySeat.clear()
        lastInferenceErrorReportAtBySeat.clear()

        // 同步重置 UI 状态，避免旧 FINISHED phase 残留导致导航竞态
        currentMatch = null
        selectedCardIds = emptySet()
        lastActionMessage = null
        turnTimer.reset()
        pushUiState()

        val aiSeatConfigs = seatConfigs
            ?.filter { it.controllerType != SeatControllerType.HUMAN }
            ?.sortedBy { it.seatId }
            ?: buildDefaultAiSeatConfigs(localSeatId)

        viewModelScope.launch {
            var loadError: AIErrorState? = null

            // 增量复用：只释放/创建变化的座位，复用不变的 AI 控制器
            aiControllersBySeatId = try {
                rebuildControllers(aiSeatConfigs)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to rebuild AI controllers", e)
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
                    seatConfigs = seatConfigs.map { Triple(it.seatId, it.name, it.controllerType) },
                )
            } else {
                serverController.startLocalMatch(ruleSet = currentRuleSet)
            }

            selectedCardIds = emptySet()
            lastActionMessage = loadError?.message
            scheduleCurrentTurn()
            pushUiState()
            startHumanTurnLoop()
            maybeResolveAiTurns()
        }
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
            scheduleCurrentTurn()
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
            scheduleCurrentTurn()
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
            aiMoveDelayMillis = lastAiMoveDelayMillis,
        )
    }

    fun clearErrorState() {
        _errorState.value = null
    }

    /**
     * 从结果页返回房间。清理游戏状态但保留 AI 控制器，以便再来一局时复用。
     */
    fun onReturnToRoom() {
        currentMatch = null
        selectedCardIds = emptySet()
        lastActionMessage = null
        _errorState.value = null
        humanTurnLoopJob?.cancel()
        aiTurnJob?.cancel()
        inferenceFailureCountBySeat.clear()
        lastInferenceErrorReportAtBySeat.clear()
        turnTimer.reset()
        pushUiState()
    }

    /**
     * 真正退出房间/应用。清理游戏状态并释放所有 AI 控制器。
     */
    fun onExitToHome() {
        currentMatch = null
        selectedCardIds = emptySet()
        lastActionMessage = null
        _errorState.value = null
        humanTurnLoopJob?.cancel()
        releaseAiControllers()
        inferenceFailureCountBySeat.clear()
        lastInferenceErrorReportAtBySeat.clear()
        turnTimer.reset()
        pushUiState()
    }

    private fun maybeResolveAiTurns() {
        aiTurnJob?.cancel()
        aiTurnJob = aiScope.launch(Dispatchers.Default) {
            var chainCount = 0
            val maxChain = if (localSeatId == MatchUiStateMapper.NO_LOCAL_SEAT_ID) {
                MAX_FULL_AI_CHAIN
            } else {
                MAX_AI_CHAIN
            }
            while (chainCount < maxChain) {
                val match = currentMatch ?: break
                if (match.phase == MatchPhase.FINISHED) break
                if (match.activeSeatIndex == localSeatId) break

                if (aiMoveDelayMillis > 0L) {
                    delay(aiMoveDelayMillis)
                }

                val latestMatch = currentMatch ?: break
                if (latestMatch.phase == MatchPhase.FINISHED) break
                if (latestMatch.activeSeatIndex == localSeatId) break

                val activeSeat = latestMatch.activeSeatIndex
                val aiPlayer = aiControllersBySeatId[activeSeat]
                var aiErrorMessage: String? = null
                var usedRuleBasedFallback = false
                val decision = if (aiPlayer != null) {
                    try {
                        aiPlayer.requestDecision(latestMatch, currentRuleSet).also { onInferenceSuccess(activeSeat) }
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
                    } catch (e: Error) {
                        throw e
                    } catch (e: Exception) {
                        android.util.Log.e("OnnxMatchViewModel", "Unexpected AI error, attempting fallback", e)
                        aiErrorMessage =
                            "Seat $activeSeat AI unexpected failure: ${e.localizedMessage ?: "unknown error"}"
                        null
                    }
                } else {
                    null
                }

                val result = when (decision) {
                    is AIDecision.PlayCards -> {
                        serverController.handleCommand(
                            match = latestMatch,
                            seatIndex = activeSeat,
                            command = com.example.chudadi.network.protocol.PlayCardCommand(
                                decision.cards.map { it.id }.toSet(),
                            ),
                        )
                    }

                    AIDecision.Pass -> {
                        serverController.handleCommand(
                            match = latestMatch,
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
                        usedRuleBasedFallback = true
                        executeRuleBasedFallback(match = latestMatch, seatIndex = activeSeat)
                    }

                    else -> {
                        usedRuleBasedFallback = true
                        executeRuleBasedFallback(match = latestMatch, seatIndex = activeSeat)
                    }
                }

                val resolvedResult = if (result.success) {
                    result
                } else {
                    if (usedRuleBasedFallback) {
                        val fallbackErrorMessage = formatActionMessage(
                            message = null,
                            error = result.error,
                        )
                        val criticalMessage = "Seat $activeSeat fallback failed: $fallbackErrorMessage"
                        _errorState.value = AIErrorState.CriticalError(message = criticalMessage)
                        currentMatch = result.match
                        lastActionMessage = criticalMessage
                        selectedCardIds = emptySet()
                        launch(Dispatchers.Main) {
                            pushUiState()
                        }
                        break
                    } else {
                        android.util.Log.w(
                            "OnnxMatchViewModel",
                            "Seat $activeSeat action failed, retrying with rule-based fallback. error=${result.error}",
                        )
                        aiErrorMessage = aiErrorMessage
                            ?: "Seat $activeSeat AI action invalid, fallback to rule-based AI"
                        usedRuleBasedFallback = true
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
                }

                currentMatch = resolvedResult.match
                lastActionMessage = aiErrorMessage
                    ?: resolvedResult.message
                    ?: com.example.chudadi.controller.client.GameActionMessageFormatter.format(resolvedResult.error)
                selectedCardIds = emptySet()
                scheduleCurrentTurn()

                launch(Dispatchers.Main) {
                    pushUiState()
                }

                chainCount++
            }
        }
    }

    private suspend fun executeRuleBasedFallback(
        match: Match,
        seatIndex: Int,
    ): ActionResult {
        // 使用独立的 RuleBasedAIAdapter，不复用 aiControllersBySeatId 中的 ONNX 控制器
        val fallbackController = RuleBasedAIAdapter(
            seatIndex = seatIndex,
            difficulty = aiControllersBySeatId[seatIndex]?.difficulty ?: AIDifficulty.NORMAL,
        )
        return try {
            when (val decision = fallbackController.requestDecision(match, currentRuleSet)) {
                is AIDecision.PlayCards -> serverController.handleCommand(
                    match = match,
                    seatIndex = seatIndex,
                    command = com.example.chudadi.network.protocol.PlayCardCommand(
                        decision.cards.map { it.id }.toSet(),
                    ),
                )
                AIDecision.Pass -> serverController.handleCommand(
                    match = match,
                    seatIndex = seatIndex,
                    command = com.example.chudadi.network.protocol.PassCommand,
                )
                is AIDecision.Error -> {
                    android.util.Log.e(
                        TAG,
                        "Fallback decision error for seat $seatIndex: ${decision.reason}",
                        decision.exception,
                    )
                    serverController.handleCommand(
                        match = match,
                        seatIndex = seatIndex,
                        command = com.example.chudadi.network.protocol.PassCommand,
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Fallback requestDecision failed for seat $seatIndex", e)
            serverController.handleCommand(
                match = match,
                seatIndex = seatIndex,
                command = com.example.chudadi.network.protocol.PassCommand,
            )
        }
    }

    private fun buildDefaultAiSeatConfigs(localSeatId: Int): List<SeatConfig> {
        return (0 until TOTAL_SEATS)
            .asSequence()
            .filter { it != localSeatId }
            .map { seatId ->
                SeatConfig(
                    seatId = seatId,
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

    /**
     * 增量重建 AI 控制器。
     *
     * 对比新旧座位配置：复用 controllerType + difficulty 不变的控制器，
     * 只释放不再需要的、创建新增或变化的。
     */
    private suspend fun rebuildControllers(
        newAiSeatConfigs: List<SeatConfig>,
    ): Map<Int, AIPlayerController> {
        val oldControllers = aiControllersBySeatId
        val reused = mutableMapOf<Int, AIPlayerController>()
        val toRelease = mutableListOf<AIPlayerController>()
        val toCreate = mutableListOf<SeatConfig>()

        // 1. 对旧控制器分类：复用 or 释放
        for ((seatId, controller) in oldControllers) {
            val newConfig = newAiSeatConfigs.firstOrNull { it.seatId == seatId }
            if (newConfig != null && isCompatibleConfig(controller, newConfig)) {
                reused[seatId] = controller
            } else {
                toRelease += controller
            }
        }

        // 2. 找出需要新建的座位
        for (config in newAiSeatConfigs) {
            if (config.seatId !in reused) {
                toCreate += config
            }
        }

        // 3. 释放不再需要的控制器
        for (controller in toRelease) {
            try {
                controller.close()
            } catch (e: Exception) {
                android.util.Log.w(TAG, "Failed to close controller for seat ${controller.seatIndex}", e)
            }
        }

        // 4. 创建新控制器
        val created = if (toCreate.isNotEmpty()) {
            val creationResults = toCreate.associate { config ->
                val result = AIFactory.createAIPlayerWithStatus(
                    context = context,
                    seatIndex = config.seatId,
                    difficulty = config.aiDifficulty ?: AIDifficulty.NORMAL,
                    isOnnxAI = config.controllerType == SeatControllerType.ONNX_RL_AI,
                )
                config.seatId to result
            }
            val fallbackEntries = creationResults.filterValues { it.isFallback }
            if (fallbackEntries.isNotEmpty()) {
                val seatIds = fallbackEntries.keys.sorted()
                val errorMessages = fallbackEntries.values.mapNotNull { it.errorMessage }.distinct()
                android.util.Log.w(TAG, "AI fallback seats: $seatIds, errors: $errorMessages")
            }
            creationResults.mapValues { it.value.controller }
        } else {
            emptyMap()
        }

        if (toRelease.isNotEmpty() || toCreate.isNotEmpty()) {
            android.util.Log.i(
                TAG,
                "Controllers rebuilt: reused=${reused.keys.sorted()}, " +
                    "released=${toRelease.map { it.seatIndex }.sorted()}, " +
                    "created=${created.keys.sorted()}",
            )
        }

        return reused + created
    }

    /**
     * 判断现有控制器是否与新配置兼容（可复用）。
     *
     * 条件：controllerType 一致、difficulty 一致。
     */
    private fun isCompatibleConfig(controller: AIPlayerController, config: SeatConfig): Boolean {
        val isOnnxController = controller is com.example.chudadi.ai.onnx.OnnxAIPlayerController
        val expectedOnnx = config.controllerType == SeatControllerType.ONNX_RL_AI
        val expectedDifficulty = config.aiDifficulty ?: AIDifficulty.NORMAL
        return isOnnxController == expectedOnnx && controller.difficulty == expectedDifficulty
    }

    private fun releaseAiControllers() {
        aiScope.coroutineContext.cancelChildren()
        aiControllersBySeatId.values.forEach { it.close() }
        aiControllersBySeatId = emptyMap()
    }

    private fun pushUiState() {
        _uiState.value = mapper.map(
            match = currentMatch,
            selectedCardIds = selectedCardIds,
            lastActionMessage = lastActionMessage,
            localSeatId = localSeatId,
        ).copy(remainingTurnSeconds = turnTimer.remainingTurnSeconds())
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

    private fun startHumanTurnLoop() {
        humanTurnLoopJob?.cancel()
        humanTurnLoopJob = viewModelScope.launch {
            while (isActive) {
                delay(HUMAN_TURN_LOOP_INTERVAL_MS)
                if (!tickHumanTurnLoop()) {
                    break
                }
            }
        }
    }

    private fun tickHumanTurnLoop(): Boolean {
        val match = currentMatch ?: return false
        val shouldContinue = when {
            match.phase == MatchPhase.FINISHED -> {
                turnTimer.reset()
                false
            }

            match.activeSeatIndex != localSeatId -> true

            turnTimer.isExpired() -> {
                resolveHumanTimeout(match)
                currentMatch?.phase != MatchPhase.FINISHED
            }

            else -> true
        }
        pushUiState()
        return shouldContinue
    }

    private fun resolveHumanTimeout(match: Match) {
        val seatName = match.seats.first { it.seatId == localSeatId }.displayName
        val result = if (serverController.canPass(match = match, seatIndex = localSeatId)) {
            serverController.handleCommand(
                match = match,
                seatIndex = localSeatId,
                command = com.example.chudadi.network.protocol.PassCommand,
            ).also {
                lastActionMessage = if (it.success) "$seatName 超时过牌" else {
                    it.message ?: com.example.chudadi.controller.client.GameActionMessageFormatter.format(it.error)
                }
            }
        } else {
            val fallbackController = RuleBasedAIAdapter(
                seatIndex = localSeatId,
                difficulty = com.example.chudadi.ai.base.AIDifficulty.NORMAL,
            )
            val decision = runCatching {
                kotlinx.coroutines.runBlocking {
                    fallbackController.requestDecision(match, currentRuleSet)
                }
            }.getOrDefault(AIDecision.Pass)
            when (decision) {
                is AIDecision.PlayCards -> serverController.handleCommand(
                    match = match,
                    seatIndex = localSeatId,
                    command = com.example.chudadi.network.protocol.PlayCardCommand(
                        decision.cards.map { it.id }.toSet(),
                    ),
                )
                else -> serverController.handleCommand(
                    match = match,
                    seatIndex = localSeatId,
                    command = com.example.chudadi.network.protocol.PassCommand,
                )
            }.also {
                lastActionMessage = if (it.success) "$seatName 超时，系统已代出" else {
                    it.message ?: com.example.chudadi.controller.client.GameActionMessageFormatter.format(it.error)
                }
            }
        }

        currentMatch = result.match
        selectedCardIds = emptySet()
        if (result.success) {
            scheduleCurrentTurn()
            maybeResolveAiTurns()
        } else if (currentMatch?.phase == MatchPhase.FINISHED) {
            turnTimer.reset()
        }
    }

    override fun onCleared() {
        releaseAiControllers()
        aiScope.coroutineContext.cancel()
        super.onCleared()
    }

    companion object {
        private const val TAG = "OnnxMatchViewModel"
        private const val DEFAULT_LOCAL_SEAT_ID = 0
        private const val TOTAL_SEATS = 4
        private const val MAX_AI_CHAIN = 32
        private const val MAX_FULL_AI_CHAIN = 512
        private const val INFERENCE_ERROR_REPORT_INTERVAL_MS = 3000L
        private const val HUMAN_TURN_LOOP_INTERVAL_MS = 250L
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
