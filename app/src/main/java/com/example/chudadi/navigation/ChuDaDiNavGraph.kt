package com.example.chudadi.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.chudadi.controller.game.LocalGameAction
import com.example.chudadi.controller.game.LocalMatchViewModel
import com.example.chudadi.model.game.entity.MatchPhase
import com.example.chudadi.model.game.entity.SeatControllerType
import com.example.chudadi.model.game.snapshot.MatchUiState
import com.example.chudadi.ui.game.GameScreen
import com.example.chudadi.ui.game.GameScreenActions
import com.example.chudadi.ui.home.HomeScreen
import com.example.chudadi.ui.result.ResultScreen
import com.example.chudadi.ui.room.RoomAction
import com.example.chudadi.ui.room.RoomScreen
import com.example.chudadi.ui.room.RoomUiState
import com.example.chudadi.ui.room.RoomViewModel
import com.example.chudadi.ui.room.MemberConnectionStatus
import com.example.chudadi.ui.room.SlotOccupantType

private const val HOME_ROUTE = "home"
private const val ROOM_ROUTE = "room"
private const val GAME_ROUTE = "game"
private const val RESULT_ROUTE = "result"

@Composable
fun ChuDaDiNavGraph(
    viewModel: LocalMatchViewModel = viewModel(),
    roomViewModel: RoomViewModel = viewModel(),
) {
    val navController = rememberNavController()
    val uiState = viewModel.uiState.collectAsStateWithLifecycle().value
    val roomUiState = roomViewModel.uiState.collectAsStateWithLifecycle().value

    HandlePhaseNavigation(navController = navController, phase = uiState.phase)

    NavHost(
        navController = navController,
        startDestination = HOME_ROUTE,
    ) {
        composable(HOME_ROUTE) {
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
        composable(ROOM_ROUTE) {
            RoomScreen(
                uiState = roomUiState,
                onAction = { action ->
                    when (action) {
                        is RoomAction.StartGame -> {
                            val startAction = buildStartMatchAction(roomUiState)
                            if (startAction != null) {
                                viewModel.dispatch(startAction)
                            }
                        }
                        is RoomAction.ExitRoom -> {
                            navController.popBackStack()
                        }
                        else -> roomViewModel.dispatch(action)
                    }
                },
                onNavigateBack = {
                    roomViewModel.dispatch(RoomAction.ResetRoom)
                    navController.popBackStack()
                },
            )
        }
        composable(GAME_ROUTE) {
            GameScreenRoute(viewModel = viewModel, uiState = uiState)
        }
        composable(RESULT_ROUTE) {
            val roundScores = uiState.resultSummary?.roundScores.orEmpty()
            LaunchedEffect(roundScores) {
                if (roundScores.isNotEmpty()) {
                    roomViewModel.dispatch(RoomAction.AccumulateScores(roundScores))
                }
            }
            ResultScreen(
                uiState = uiState,
                onReturnToRoom = {
                    viewModel.dispatch(LocalGameAction.ExitToHome)
                    navController.navigate(ROOM_ROUTE) {
                        popUpTo(HOME_ROUTE)
                    }
                },
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

private fun buildStartMatchAction(roomUiState: RoomUiState): LocalGameAction.StartLocalMatch? {
    if (!roomUiState.canStartMatch()) {
        return null
    }

    val seatConfigs = roomUiState.slots
        .map { slot ->
            val name = slot.displayName.ifEmpty { "玩家${slot.slotIndex + 1}" }
            val controllerType = if (slot.occupantType == SlotOccupantType.AI) {
                SeatControllerType.RULE_BASED_AI
            } else {
                SeatControllerType.HUMAN
            }
            Triple(slot.seatId, name, controllerType)
        }
    val localSeatId = roomUiState.slots.firstOrNull { it.isLocalPlayer }?.seatId ?: return null
    return LocalGameAction.StartLocalMatch(seatConfigs, localSeatId)
}

private fun RoomUiState.canStartMatch(): Boolean {
    val allFilled = slots.all { it.occupantType != null }
    val allReady = slots.all { it.connectionStatus == MemberConnectionStatus.READY }
    val slotIndexesStable = slots.withIndex().all { (index, slot) -> slot.slotIndex == index }
    val seatIdsDistinct = slots.map { it.seatId }.distinct().size == slots.size
    val localPlayerExists = slots.any { it.isLocalPlayer }
    return allFilled && allReady && slotIndexesStable && seatIdsDistinct && localPlayerExists
}

@Composable
private fun HandlePhaseNavigation(    navController: NavHostController,
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
