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
import com.example.chudadi.ui.game.GameScreenActions
import com.example.chudadi.ui.game.GameScreen
import com.example.chudadi.ui.home.HomeScreen
import com.example.chudadi.ui.result.ResultScreen

private const val HOME_ROUTE = "home"
private const val GAME_ROUTE = "game"
private const val RESULT_ROUTE = "result"

@Composable
fun ChuDaDiNavGraph(
    viewModel: LocalMatchViewModel = viewModel(),
) {
    val navController = rememberNavController()
    val uiState = viewModel.uiState.collectAsStateWithLifecycle().value

    HandlePhaseNavigation(navController = navController, phase = uiState.phase)

    NavHost(
        navController = navController,
        startDestination = HOME_ROUTE,
    ) {
        composable(HOME_ROUTE) {
            HomeScreen(
                onStartLocalMatch = {
                    viewModel.dispatch(LocalGameAction.StartLocalMatch)
                },
            )
        }
        composable(GAME_ROUTE) {
            GameScreen(
                uiState = uiState,
                actions =
                    GameScreenActions(
                        onToggleCardSelection = { cardId ->
                            viewModel.dispatch(LocalGameAction.ToggleCardSelection(cardId))
                        },
                        onClearSelection = {
                            viewModel.dispatch(LocalGameAction.ClearSelection)
                        },
                        onSubmitSelectedCards = {
                            viewModel.dispatch(LocalGameAction.SubmitSelectedCards)
                        },
                        onPassTurn = {
                            viewModel.dispatch(LocalGameAction.PassTurn)
                        },
                    ),
            )
        }
        composable(RESULT_ROUTE) {
            ResultScreen(
                uiState = uiState,
                onRestartMatch = {
                    viewModel.dispatch(LocalGameAction.RestartMatch)
                },
                onExitToHome = {
                    viewModel.dispatch(LocalGameAction.ExitToHome)
                },
            )
        }
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
                if (navController.currentDestination?.route != HOME_ROUTE) {
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
