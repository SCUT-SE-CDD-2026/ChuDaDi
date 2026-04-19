package com.example.chudadi.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.chudadi.controller.game.LocalGameAction
import com.example.chudadi.controller.game.LocalMatchViewModel
import com.example.chudadi.data.repository.PlayerPreferencesRepository
import com.example.chudadi.model.game.entity.MatchPhase
import com.example.chudadi.model.game.rule.GameRuleSet
import com.example.chudadi.model.game.snapshot.MatchUiState
import com.example.chudadi.ui.game.GameScreen
import com.example.chudadi.ui.game.GameScreenActions
import com.example.chudadi.ui.home.HomeScreen
import com.example.chudadi.ui.result.ResultScreen
import com.example.chudadi.ui.room.BluetoothSearchScreen
import com.example.chudadi.ui.room.BluetoothSearchState
import com.example.chudadi.ui.room.RoomAction
import com.example.chudadi.ui.room.RoomScreen
import com.example.chudadi.ui.room.RoomUiState
import com.example.chudadi.ui.room.RoomViewModel
import com.example.chudadi.ui.room.MemberConnectionStatus
import com.example.chudadi.ui.room.SlotOccupantType
import com.example.chudadi.ui.settings.SettingsScreen
import com.example.chudadi.ui.settings.SettingsViewModel
import kotlinx.coroutines.launch

private const val HOME_ROUTE = "home"
private const val SETTINGS_ROUTE = "settings"
private const val BLUETOOTH_SEARCH_ROUTE = "bluetooth-search"
private const val ROOM_ROUTE = "room"
private const val GAME_ROUTE = "game"
private const val RESULT_ROUTE = "result"

@Suppress("LongMethod", "LongParameterList", "CyclomaticComplexMethod")
@Composable
fun ChuDaDiNavGraph(
    viewModel: LocalMatchViewModel = viewModel(),
    roomViewModel: RoomViewModel,
    playerPreferencesRepository: PlayerPreferencesRepository,
    localDeviceName: String,
    onRequestBluetoothEnable: (onComplete: () -> Unit) -> Unit,
    onRequestBluetoothPermissions: (onComplete: () -> Unit) -> Unit,
) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val uiState = viewModel.uiState.collectAsStateWithLifecycle().value
    val roomUiState = roomViewModel.uiState.collectAsStateWithLifecycle().value
    val networkMatchUiState = roomViewModel.matchUiState.collectAsStateWithLifecycle().value
    val playerName = roomViewModel.playerName.collectAsStateWithLifecycle().value
    val activeMatchUiState = if (networkMatchUiState.phase != MatchPhase.NOT_STARTED) networkMatchUiState else uiState
    val runJoinFlow: () -> Unit = {
        scope.launch {
            val reconnected = roomViewModel.tryReconnectLastSession()
            if (reconnected) {
                navController.navigate(ROOM_ROUTE) {
                    launchSingleTop = true
                }
            } else {
                roomViewModel.loadJoinableDevices()
                navController.navigate(BLUETOOTH_SEARCH_ROUTE)
            }
        }
    }
    val requestBluetoothConnectReady: ((() -> Unit) -> Unit) = { onReady ->
        if (!roomViewModel.isBluetoothSupported()) {
            roomViewModel.showHomeNotice("当前设备不支持蓝牙")
        } else if (!roomViewModel.hasBluetoothConnectPermission()) {
            onRequestBluetoothPermissions {
                if (roomViewModel.hasBluetoothConnectPermission()) {
                    if (roomViewModel.isBluetoothEnabled()) {
                        onReady()
                    } else {
                        onRequestBluetoothEnable {
                            if (roomViewModel.isBluetoothEnabled()) {
                                onReady()
                            } else {
                                roomViewModel.showHomeNotice("未开启蓝牙，无法加入房间")
                            }
                        }
                    }
                } else {
                    roomViewModel.showHomeNotice("缺少蓝牙连接权限，无法加入房间")
                }
            }
        } else if (!roomViewModel.isBluetoothEnabled()) {
            onRequestBluetoothEnable {
                if (roomViewModel.isBluetoothEnabled()) {
                    onReady()
                } else {
                    roomViewModel.showHomeNotice("未开启蓝牙，无法加入房间")
                }
            }
        } else {
            onReady()
        }
    }
    val requestBluetoothHostReady: ((() -> Unit) -> Unit) = { onReady ->
        if (!roomViewModel.isBluetoothSupported()) {
            roomViewModel.showHomeNotice("当前设备不支持蓝牙，无法创建房间")
        } else if (!roomViewModel.hasBluetoothConnectPermission()) {
            onRequestBluetoothPermissions {
                if (!roomViewModel.hasBluetoothConnectPermission()) {
                    roomViewModel.showHomeNotice("缺少蓝牙连接权限，无法创建房间")
                } else if (roomViewModel.isBluetoothEnabled()) {
                    onReady()
                } else {
                    onRequestBluetoothEnable {
                        if (roomViewModel.isBluetoothEnabled()) {
                            onReady()
                        } else {
                            roomViewModel.showHomeNotice("未开启蓝牙，无法创建房间")
                        }
                    }
                }
            }
        } else if (!roomViewModel.isBluetoothEnabled()) {
            onRequestBluetoothEnable {
                if (roomViewModel.isBluetoothEnabled()) {
                    onReady()
                } else {
                    roomViewModel.showHomeNotice("未开启蓝牙，无法创建房间")
                }
            }
        } else {
            onReady()
        }
    }
    val requestBluetoothDiscoveryReady: ((() -> Unit) -> Unit) = { onReady ->
        if (!roomViewModel.isBluetoothSupported()) {
            roomViewModel.showJoinError("当前设备不支持蓝牙")
        } else if (!roomViewModel.hasBluetoothConnectPermission() || !roomViewModel.hasBluetoothScanPermission()) {
            onRequestBluetoothPermissions {
                if (roomViewModel.hasBluetoothConnectPermission() && roomViewModel.hasBluetoothScanPermission()) {
                    if (roomViewModel.isBluetoothEnabled()) {
                        onReady()
                    } else {
                        onRequestBluetoothEnable {
                            if (roomViewModel.isBluetoothEnabled()) {
                                onReady()
                            } else {
                                roomViewModel.showJoinError("未开启蓝牙，无法搜索房间")
                            }
                        }
                    }
                } else {
                    roomViewModel.showJoinError("缺少蓝牙权限，无法搜索房间")
                }
            }
        } else if (!roomViewModel.isBluetoothEnabled()) {
            onRequestBluetoothEnable {
                if (roomViewModel.isBluetoothEnabled()) {
                    onReady()
                } else {
                    roomViewModel.showJoinError("未开启蓝牙，无法搜索房间")
                }
            }
        } else {
            onReady()
        }
    }

    HandlePhaseNavigation(navController = navController, phase = activeMatchUiState.phase)
    LaunchedEffect(
        roomUiState.isHost,
        roomUiState.slots,
        roomUiState.removedFromRoom,
        roomUiState.roomClosedByHost,
        navController.currentDestination?.route,
    ) {
        val hasLocalSeat = roomUiState.slots.any { it.isLocalPlayer && it.occupantType != null }
        val roomStillActive = !roomUiState.removedFromRoom && !roomUiState.roomClosedByHost
        if (hasLocalSeat && roomStillActive && navController.currentDestination?.route == BLUETOOTH_SEARCH_ROUTE) {
            navController.navigate(ROOM_ROUTE) {
                popUpTo(BLUETOOTH_SEARCH_ROUTE) { inclusive = true }
                launchSingleTop = true
            }
        }
    }
    LaunchedEffect(roomUiState.removedFromRoom) {
        if (roomUiState.removedFromRoom) {
            navController.navigate(HOME_ROUTE) {
                popUpTo(HOME_ROUTE) { inclusive = true }
                launchSingleTop = true
            }
            roomViewModel.dispatch(RoomAction.ConsumeRoomExitNotice)
        }
    }
    LaunchedEffect(roomUiState.roomClosedByHost) {
        if (roomUiState.roomClosedByHost) {
            navController.navigate(HOME_ROUTE) {
                popUpTo(HOME_ROUTE) { inclusive = true }
                launchSingleTop = true
            }
            roomViewModel.dispatch(RoomAction.ConsumeRoomExitNotice)
        }
    }

    NavHost(
        navController = navController,
        startDestination = HOME_ROUTE,
    ) {
        composable(HOME_ROUTE) {
            HomeScreen(
                playerName = playerName,
                noticeMessage = roomUiState.homeNoticeMessage,
                onDismissNotice = { roomViewModel.dispatch(RoomAction.ConsumeHomeNotice) },
                onCreateRoom = {
                    requestBluetoothHostReady {
                        val created = roomViewModel.createHostRoom(localDeviceName)
                        if (created) {
                            navController.navigate(ROOM_ROUTE)
                        }
                    }
                },
                onJoinRoom = {
                    requestBluetoothConnectReady(runJoinFlow)
                },
                onSettings = {
                    navController.navigate(SETTINGS_ROUTE)
                },
            )
        }
        composable(SETTINGS_ROUTE) {
            val settingsViewModel: SettingsViewModel = viewModel(
                factory = SettingsViewModel.factory(playerPreferencesRepository),
            )
            SettingsScreen(
                viewModel = settingsViewModel,
                onNavigateBack = { navController.popBackStack() },
            )
        }
        composable(BLUETOOTH_SEARCH_ROUTE) {
            BluetoothSearchScreen(
                uiState = roomUiState,
                onAction = { action ->
                    when (action) {
                        RoomAction.StartBluetoothDiscovery -> {
                            requestBluetoothDiscoveryReady {
                                roomViewModel.dispatch(action)
                            }
                        }

                        is RoomAction.ConnectToBluetoothDevice -> roomViewModel.dispatch(action)

                        else -> roomViewModel.dispatch(action)
                    }
                },
                onNavigateBack = {
                    roomViewModel.dispatch(RoomAction.ConsumeJoinError)
                    navController.popBackStack()
                },
            )
        }
        composable(ROOM_ROUTE) {
            RoomScreen(
                uiState = roomUiState,
                onAction = { action ->
                    when (action) {
                        is RoomAction.StartGame -> {
                            roomViewModel.dispatch(action)
                        }

                        is RoomAction.ExitRoom -> {
                            roomViewModel.dispatch(action)
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
            GameScreenRoute(
                localViewModel = viewModel,
                roomViewModel = roomViewModel,
                uiState = activeMatchUiState,
                useNetworkMatch = networkMatchUiState.phase != MatchPhase.NOT_STARTED,
            )
        }
        composable(RESULT_ROUTE) {
            val roundScores = activeMatchUiState.resultSummary?.roundScores.orEmpty()
            LaunchedEffect(roundScores) {
                if (roundScores.isNotEmpty()) {
                    roomViewModel.dispatch(RoomAction.AccumulateScores(roundScores))
                }
            }
            ResultScreen(
                uiState = activeMatchUiState,
                onReturnToRoom = {
                    if (networkMatchUiState.phase != MatchPhase.NOT_STARTED) {
                        roomViewModel.dispatchGameAction(LocalGameAction.ExitToHome)
                    } else {
                        viewModel.dispatch(LocalGameAction.ExitToHome)
                    }
                    navController.navigate(ROOM_ROUTE) {
                        popUpTo(HOME_ROUTE)
                    }
                },
            )
        }
    }
}

@Composable
private fun GameScreenRoute(
    localViewModel: LocalMatchViewModel,
    roomViewModel: RoomViewModel,
    uiState: MatchUiState,
    useNetworkMatch: Boolean,
) {
    GameScreen(
        uiState = uiState,
        actions = GameScreenActions(
            onToggleCardSelection = { cardId ->
                if (useNetworkMatch) {
                    roomViewModel.dispatchGameAction(LocalGameAction.ToggleCardSelection(cardId))
                } else {
                    localViewModel.dispatch(LocalGameAction.ToggleCardSelection(cardId))
                }
            },
            onClearSelection = {
                if (useNetworkMatch) roomViewModel.dispatchGameAction(LocalGameAction.ClearSelection)
                else localViewModel.dispatch(LocalGameAction.ClearSelection)
            },
            onSubmitSelectedCards = {
                if (useNetworkMatch) roomViewModel.dispatchGameAction(LocalGameAction.SubmitSelectedCards)
                else localViewModel.dispatch(LocalGameAction.SubmitSelectedCards)
            },
            onPassTurn = {
                if (useNetworkMatch) roomViewModel.dispatchGameAction(LocalGameAction.PassTurn)
                else localViewModel.dispatch(LocalGameAction.PassTurn)
            },
        ),
    )
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
