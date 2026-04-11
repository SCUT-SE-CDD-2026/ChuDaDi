package com.example.chudadi.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.chudadi.BuildConfig
import com.example.chudadi.ai.base.AIDifficulty
import com.example.chudadi.controller.game.LocalGameAction
import com.example.chudadi.controller.game.LocalMatchViewModel
import com.example.chudadi.controller.game.MatchUiStateMapper
import com.example.chudadi.controller.game.OnnxMatchViewModel
import com.example.chudadi.controller.game.SeatConfig
import com.example.chudadi.model.game.entity.MatchPhase
import com.example.chudadi.model.game.entity.SeatControllerType
import com.example.chudadi.model.game.rule.GameRuleSet
import com.example.chudadi.model.game.snapshot.MatchUiState
import com.example.chudadi.ui.game.GameScreen
import com.example.chudadi.ui.game.GameScreenActions
import com.example.chudadi.ui.home.HomeScreen
import com.example.chudadi.ui.result.ResultScreen
import com.example.chudadi.ui.room.AIType
import com.example.chudadi.ui.room.GameRuleDisplay
import com.example.chudadi.ui.room.RoomAction
import com.example.chudadi.ui.room.RoomAiDifficulty
import com.example.chudadi.ui.room.AiPlaySpeed
import com.example.chudadi.ui.room.RoomScreen
import com.example.chudadi.ui.room.RoomUiState
import com.example.chudadi.ui.room.RoomViewModel
import com.example.chudadi.ui.room.SlotOccupantType

private const val HOME_ROUTE = "home"
private const val ROOM_ROUTE = "room"
private const val GAME_ROUTE = "game"
private const val RESULT_ROUTE = "result"
private const val TOTAL_AUTO_ROUNDS = 100

private data class AutoRoundState(
    val isActive: Boolean = false,
    val roundsRemaining: Int = 0,
    val isStartPending: Boolean = false,
)

private enum class GameViewModelType {
    LOCAL,
    ONNX,
}

private data class MatchViewModelContext(
    val type: GameViewModelType,
    val localViewModel: LocalMatchViewModel?,
    val onnxViewModel: OnnxMatchViewModel?,
)

private data class RoomRouteContext(
    val navController: NavHostController,
    val roomViewModel: RoomViewModel,
    val roomUiState: RoomUiState,
    val matchContext: MatchViewModelContext,
    val autoRoundState: AutoRoundState,
    val onAutoRoundStateChange: (AutoRoundState) -> Unit,
)

private data class ResultRouteContext(
    val navController: NavHostController,
    val roomViewModel: RoomViewModel,
    val matchContext: MatchViewModelContext,
    val uiState: MatchUiState,
    val autoRoundState: AutoRoundState,
    val onAutoRoundStateChange: (AutoRoundState) -> Unit,
)

@Composable
fun ChuDaDiNavGraph(
    roomViewModel: RoomViewModel = viewModel(),
) {
    val navController = rememberNavController()
    val roomUiState by roomViewModel.uiState.collectAsStateWithLifecycle()
    var autoRoundState by remember { mutableStateOf(AutoRoundState()) }

    val hasOnnxAI = roomUiState.slots.any { it.aiType == AIType.ONNX_RL }
    val viewModelType = if (hasOnnxAI) GameViewModelType.ONNX else GameViewModelType.LOCAL

    val localViewModel = if (viewModelType == GameViewModelType.LOCAL) {
        viewModel<LocalMatchViewModel>()
    } else {
        null
    }

    val onnxViewModel = if (viewModelType == GameViewModelType.ONNX) {
        viewModel<OnnxMatchViewModel>()
    } else {
        null
    }

    val matchContext = MatchViewModelContext(
        type = viewModelType,
        localViewModel = localViewModel,
        onnxViewModel = onnxViewModel,
    )

    val uiState by when (matchContext.type) {
        GameViewModelType.LOCAL -> matchContext.localViewModel!!.uiState.collectAsStateWithLifecycle()
        GameViewModelType.ONNX -> matchContext.onnxViewModel!!.uiState.collectAsStateWithLifecycle()
    }

    HandlePhaseNavigation(
        navController = navController,
        phase = uiState.phase,
    )

    NavHost(
        navController = navController,
        startDestination = HOME_ROUTE,
    ) {
        composable(HOME_ROUTE) {
            HomeRoute(
                navController = navController,
                roomViewModel = roomViewModel,
            )
        }

        composable(ROOM_ROUTE) {
            RoomRoute(
                RoomRouteContext(
                    navController = navController,
                    roomViewModel = roomViewModel,
                    roomUiState = roomUiState,
                    matchContext = matchContext,
                    autoRoundState = autoRoundState,
                    onAutoRoundStateChange = { autoRoundState = it },
                ),
            )
        }

        composable(GAME_ROUTE) {
            GameRoute(
                matchContext = matchContext,
                uiState = uiState,
            )
        }

        composable(RESULT_ROUTE) {
            ResultRoute(
                ResultRouteContext(
                    navController = navController,
                    roomViewModel = roomViewModel,
                    matchContext = matchContext,
                    uiState = uiState,
                    autoRoundState = autoRoundState,
                    onAutoRoundStateChange = { autoRoundState = it },
                ),
            )
        }
    }
}

@Composable
private fun HomeRoute(
    navController: NavHostController,
    roomViewModel: RoomViewModel,
) {
    HomeScreen(
        onCreateRoom = {
            roomViewModel.dispatch(RoomAction.ResetRoom)
            navController.navigate(ROOM_ROUTE)
        },
        onJoinRoom = {
            navController.navigate(ROOM_ROUTE)
        },
    )
}

@Composable
private fun RoomRoute(
    context: RoomRouteContext,
) {
    val navController = context.navController
    val roomViewModel = context.roomViewModel
    val roomUiState = context.roomUiState
    val matchContext = context.matchContext
    val autoRoundState = context.autoRoundState
    val onAutoRoundStateChange = context.onAutoRoundStateChange

    LaunchedEffect(autoRoundState.isStartPending, roomUiState.canStartGame) {
        if (autoRoundState.isStartPending && roomUiState.canStartGame) {
            onAutoRoundStateChange(autoRoundState.copy(isStartPending = false))
            startMatchFromRoom(
                roomUiState = roomUiState,
                matchContext = matchContext,
            )
        }
    }

    RoomScreen(
        uiState = roomUiState,
        onAction = { roomAction ->
            when (roomAction) {
                RoomAction.StartGame -> {
                    val enableAutoRoundMode = shouldEnableAutoRoundMode(roomUiState)
                    if (enableAutoRoundMode) {
                        onAutoRoundStateChange(
                            AutoRoundState(
                                isActive = true,
                                roundsRemaining = TOTAL_AUTO_ROUNDS - 1,
                                isStartPending = false,
                            ),
                        )
                    } else if (autoRoundState.isActive ||
                        autoRoundState.roundsRemaining != 0 ||
                        autoRoundState.isStartPending
                    ) {
                        onAutoRoundStateChange(AutoRoundState())
                    }
                    startMatchFromRoom(
                        roomUiState = roomUiState,
                        matchContext = matchContext,
                    )
                }

                RoomAction.ExitRoom -> {
                    onAutoRoundStateChange(AutoRoundState())
                    if (roomUiState.isHost) {
                        roomViewModel.dispatch(RoomAction.ResetRoom)
                    }
                    navController.popBackStack()
                }

                else -> roomViewModel.dispatch(roomAction)
            }
        },
        onNavigateBack = {
            onAutoRoundStateChange(AutoRoundState())
            if (roomUiState.isHost) {
                roomViewModel.dispatch(RoomAction.ResetRoom)
            }
            navController.popBackStack()
        },
    )
}

@Composable
private fun GameRoute(
    matchContext: MatchViewModelContext,
    uiState: MatchUiState,
) {
    when (matchContext.type) {
        GameViewModelType.LOCAL -> {
            GameScreenRoute(viewModel = matchContext.localViewModel!!, uiState = uiState)
        }

        GameViewModelType.ONNX -> {
            OnnxGameScreenRoute(viewModel = matchContext.onnxViewModel!!, uiState = uiState)
        }
    }
}

@Composable
private fun ResultRoute(
    context: ResultRouteContext,
) {
    val navController = context.navController
    val roomViewModel = context.roomViewModel
    val matchContext = context.matchContext
    val uiState = context.uiState
    val autoRoundState = context.autoRoundState
    val onAutoRoundStateChange = context.onAutoRoundStateChange

    fun returnToRoom() {
        when (matchContext.type) {
            GameViewModelType.LOCAL -> {
                matchContext.localViewModel?.dispatch(LocalGameAction.ExitToHome)
            }

            GameViewModelType.ONNX -> {
                matchContext.onnxViewModel?.onExitToHome()
            }
        }

        navController.navigate(ROOM_ROUTE) {
            popUpTo(HOME_ROUTE)
        }
    }

    val roundScores = uiState.resultSummary?.roundScores.orEmpty()
    LaunchedEffect(uiState.phase, roundScores) {
        if (roundScores.isNotEmpty()) {
            roomViewModel.dispatch(RoomAction.AccumulateScores(roundScores))
        }

        if (uiState.phase == MatchPhase.FINISHED && autoRoundState.isActive) {
            if (autoRoundState.roundsRemaining > 0) {
                onAutoRoundStateChange(
                    autoRoundState.copy(
                        roundsRemaining = autoRoundState.roundsRemaining - 1,
                        isStartPending = true,
                    ),
                )
            } else {
                onAutoRoundStateChange(AutoRoundState())
            }
            returnToRoom()
        }
    }

    ResultScreen(
        uiState = uiState,
        onReturnToRoom = {
            onAutoRoundStateChange(AutoRoundState())
            returnToRoom()
        },
    )
}

private fun startMatchFromRoom(
    roomUiState: RoomUiState,
    matchContext: MatchViewModelContext,
) {
    if (!roomUiState.canStartGame) return

    val payload = buildStartMatchPayload(roomUiState)
    when (matchContext.type) {
        GameViewModelType.LOCAL -> {
            matchContext.localViewModel?.dispatch(
                LocalGameAction.StartLocalMatch(
                    seatConfigs = payload.seatConfigs,
                    localSeatId = payload.localSeatId,
                    ruleSet = payload.ruleSet,
                    aiMoveDelayMillis = payload.aiMoveDelayMillis,
                ),
            )
        }

        GameViewModelType.ONNX -> {
            matchContext.onnxViewModel?.onRequestStartLocalMatch(
                seatConfigs = payload.seatConfigs,
                localSeatId = payload.localSeatId,
                ruleSet = payload.ruleSet,
                aiMoveDelayMillis = payload.aiMoveDelayMillis,
            )
        }
    }
}

@Composable
private fun GameScreenRoute(viewModel: LocalMatchViewModel, uiState: MatchUiState) {
    GameScreen(
        uiState = uiState,
        actions = GameScreenActions(
            onToggleCardSelection = { cardId ->
                viewModel.dispatch(LocalGameAction.ToggleCardSelection(cardId))
            },
            onClearSelection = { viewModel.dispatch(LocalGameAction.ClearSelection) },
            onSubmitSelectedCards = { viewModel.dispatch(LocalGameAction.SubmitSelectedCards) },
            onPassTurn = { viewModel.dispatch(LocalGameAction.PassTurn) },
        ),
    )
}

@Composable
private fun OnnxGameScreenRoute(viewModel: OnnxMatchViewModel, uiState: MatchUiState) {
    GameScreen(
        uiState = uiState,
        actions = GameScreenActions(
            onToggleCardSelection = { cardId ->
                viewModel.onToggleCardSelection(cardId)
            },
            onClearSelection = { viewModel.onClearSelection() },
            onSubmitSelectedCards = { viewModel.onRequestPlayCards() },
            onPassTurn = { viewModel.onRequestPass() },
        ),
    )
}

private data class StartMatchPayload(
    val seatConfigs: List<SeatConfig>,
    val localSeatId: Int,
    val ruleSet: GameRuleSet,
    val aiMoveDelayMillis: Long,
)

private fun buildStartMatchPayload(roomUiState: RoomUiState): StartMatchPayload {
    val seatConfigs = roomUiState.slots
        .sortedBy { it.slotIndex }
        .map { slot ->
            val name = slot.displayName.ifEmpty { "Player${slot.slotIndex + 1}" }
            val controllerType = when (slot.occupantType) {
                SlotOccupantType.AI -> {
                    if (slot.aiType == AIType.ONNX_RL) {
                        SeatControllerType.ONNX_RL_AI
                    } else {
                        SeatControllerType.RULE_BASED_AI
                    }
                }

                else -> SeatControllerType.HUMAN
            }

            SeatConfig(
                seatId = slot.seatId,
                name = name,
                controllerType = controllerType,
                aiDifficulty = slot.aiDifficulty?.toModelDifficulty(),
            )
        }

    val localSeatId = roomUiState.slots
        .firstOrNull { it.isLocalPlayer }
        ?.seatId
        ?: MatchUiStateMapper.NO_LOCAL_SEAT_ID
    val ruleSet = when (roomUiState.currentRule) {
        GameRuleDisplay.SOUTHERN -> GameRuleSet.SOUTHERN
        GameRuleDisplay.NORTHERN -> GameRuleSet.NORTHERN
    }
    return StartMatchPayload(
        seatConfigs = seatConfigs,
        localSeatId = localSeatId,
        ruleSet = ruleSet,
        aiMoveDelayMillis = roomUiState.aiPlaySpeed.delayMillis,
    )
}

private fun shouldEnableAutoRoundMode(roomUiState: RoomUiState): Boolean {
    return BuildConfig.DEBUG &&
        roomUiState.aiPlaySpeed == AiPlaySpeed.DEBUG_100_ROUNDS &&
        roomUiState.slots.size == 4 &&
        roomUiState.slots.all { it.occupantType == SlotOccupantType.AI }
}

private fun RoomAiDifficulty.toModelDifficulty(): AIDifficulty {
    return when (this) {
        RoomAiDifficulty.RULE_EASY,
        RoomAiDifficulty.ONNX_EASY,
        -> AIDifficulty.EASY

        RoomAiDifficulty.RULE_NORMAL,
        RoomAiDifficulty.ONNX_NORMAL,
        -> AIDifficulty.NORMAL

        RoomAiDifficulty.RULE_HARD,
        RoomAiDifficulty.ONNX_HARD,
        -> AIDifficulty.HARD
    }
}

@Composable
private fun HandlePhaseNavigation(
    navController: NavHostController,
    phase: MatchPhase,
) {
    LaunchedEffect(phase) {
        when (phase) {
            MatchPhase.NOT_STARTED -> {
                if (navController.currentDestination?.route != HOME_ROUTE &&
                    navController.currentDestination?.route != ROOM_ROUTE
                ) {
                    navController.navigate(HOME_ROUTE) {
                        popUpTo(navController.graph.startDestinationId) {
                            inclusive = true
                        }
                        launchSingleTop = true
                    }
                }
            }

            MatchPhase.FINISHED -> {
                if (navController.currentDestination?.route != RESULT_ROUTE) {
                    navController.navigate(RESULT_ROUTE) {
                        launchSingleTop = true
                    }
                }
            }

            else -> {
                if (navController.currentDestination?.route != GAME_ROUTE) {
                    navController.navigate(GAME_ROUTE) {
                        launchSingleTop = true
                    }
                }
            }
        }
    }
}
