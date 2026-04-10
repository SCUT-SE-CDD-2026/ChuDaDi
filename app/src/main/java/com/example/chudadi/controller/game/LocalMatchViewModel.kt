package com.example.chudadi.controller.game

import androidx.lifecycle.ViewModel
import com.example.chudadi.ai.rulebased.RuleBasedAiPlayer
import com.example.chudadi.controller.client.LocalPlayerController
import com.example.chudadi.controller.server.LocalAuthoritativeController
import com.example.chudadi.model.game.engine.GameEngine
import com.example.chudadi.model.game.entity.SeatControllerType
import com.example.chudadi.model.game.snapshot.MatchUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow

class LocalMatchViewModel(
    private val engine: GameEngine,
    private val aiPlayer: RuleBasedAiPlayer,
    private val mapper: MatchUiStateMapper,
) : ViewModel() {
    constructor() : this(engine = GameEngine())

    constructor(engine: GameEngine) : this(
        engine = engine,
        aiPlayer = RuleBasedAiPlayer(),
        mapper = MatchUiStateMapper(engine),
    )

    private val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val controller =
        LocalPlayerController(
            serverController = LocalAuthoritativeController(engine),
            aiPlayer = aiPlayer,
            mapper = mapper,
            scope = viewModelScope,
        )

    val uiState: StateFlow<MatchUiState> = controller.uiState

    fun dispatch(action: LocalGameAction) {
        when (action) {
            is LocalGameAction.StartLocalMatch -> {
                // 为空的 AI 座位生成显示名（AIN1, AIN2, AIN3）
                val populatedConfigs = action.seatConfigs?.map { config ->
                    if (config.controllerType == SeatControllerType.RULE_BASED_AI &&
                        config.name.isBlank()
                    ) {
                        val seatNumber = config.seatIndex + 1
                        config.copy(name = "AIN$seatNumber")
                    } else {
                        config
                    }
                }

                // 过滤掉 ONNX AI 座位，仅规则型 AI + HUMAN 交给 LocalMatch
                val ruleBasedConfigs = populatedConfigs?.filter {
                    it.controllerType == SeatControllerType.RULE_BASED_AI ||
                        it.controllerType == SeatControllerType.HUMAN
                }
                controller.onRequestStartLocalMatch(
                    seatConfigs = ruleBasedConfigs,
                    localSeatId = action.localSeatId,
                    ruleSet = action.ruleSet,
                )
            }

            LocalGameAction.ClearSelection -> controller.onClearSelection()
            LocalGameAction.SubmitSelectedCards -> controller.onRequestPlayCards()
            LocalGameAction.PassTurn -> controller.onRequestPass()
            LocalGameAction.RestartMatch -> controller.onRequestRestartMatch()
            LocalGameAction.ExitToHome -> controller.onExitToHome()
            is LocalGameAction.ToggleCardSelection -> controller.onToggleCardSelection(action.cardId)
        }
    }

    override fun onCleared() {
        controller.dispose()
        viewModelScope.coroutineContext.cancel()
        super.onCleared()
    }
}
