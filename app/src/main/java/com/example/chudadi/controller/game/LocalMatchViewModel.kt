package com.example.chudadi.controller.game

import androidx.lifecycle.ViewModel
import com.example.chudadi.ai.rulebased.AiDecision
import com.example.chudadi.ai.rulebased.RuleBasedAiPlayer
import com.example.chudadi.controller.client.LocalPlayerController
import com.example.chudadi.controller.server.LocalAuthoritativeController
import com.example.chudadi.model.game.engine.GameEngine
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
            is LocalGameAction.StartLocalMatch ->
                controller.onRequestStartLocalMatch(action.seatConfigs, action.localSeatId)
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
