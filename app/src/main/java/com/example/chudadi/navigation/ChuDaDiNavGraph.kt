package com.example.chudadi.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.chudadi.controller.game.LocalGameAction
import com.example.chudadi.controller.game.LocalMatchViewModel
import com.example.chudadi.data.repository.PlayerPreferencesRepository
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
import com.example.chudadi.ui.settings.SettingsScreen
import com.example.chudadi.ui.settings.SettingsViewModel
import kotlinx.coroutines.launch

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
    var requestedRoute by rememberSaveable { androidx.compose.runtime.mutableStateOf(AppFlowRoute.HOME) }
    val uiState = viewModel.uiState.collectAsStateWithLifecycle().value
    val roomUiState = roomViewModel.uiState.collectAsStateWithLifecycle().value
    val networkMatchUiState = roomViewModel.matchUiState.collectAsStateWithLifecycle().value
    val appFlowState = remember(requestedRoute, uiState, roomUiState, networkMatchUiState) {
        buildAppFlowState(
            requestedRoute = requestedRoute,
            localMatchUiState = uiState,
            roomUiState = roomUiState,
            networkMatchUiState = networkMatchUiState,
        )
    }
    val playerName = roomViewModel.playerName.collectAsStateWithLifecycle().value
    val runJoinFlow: () -> Unit = {
        scope.launch {
            val reconnected = roomViewModel.tryReconnectLastSession()
            if (reconnected) {
                requestedRoute = AppFlowRoute.ROOM
            } else {
                roomViewModel.loadJoinableDevices()
                requestedRoute = AppFlowRoute.BLUETOOTH_SEARCH
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
            roomViewModel.showJoinError(
                message = "当前设备不支持蓝牙",
                title = "无法搜索房间",
            )
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
                                roomViewModel.showJoinError(
                                    message = "未开启蓝牙，无法搜索房间",
                                    title = "无法搜索房间",
                                )
                            }
                        }
                    }
                } else {
                    roomViewModel.showJoinError(
                        message = "缺少蓝牙权限，无法搜索房间",
                        title = "无法搜索房间",
                    )
                }
            }
        } else if (!roomViewModel.isBluetoothEnabled()) {
            onRequestBluetoothEnable {
                if (roomViewModel.isBluetoothEnabled()) {
                    onReady()
                } else {
                    roomViewModel.showJoinError(
                        message = "未开启蓝牙，无法搜索房间",
                        title = "无法搜索房间",
                    )
                }
            }
        } else {
            onReady()
        }
    }

    LaunchedEffect(appFlowState.currentRoute) {
        navigateToFlowRoute(navController, AppFlowNavigationEvent(route = appFlowState.currentRoute))
    }
    LaunchedEffect(roomViewModel) {
        roomViewModel.appFlowEvents.collect { event ->
            requestedRoute = event.route
            navigateToFlowRoute(navController, event)
        }
    }
    LaunchedEffect(roomViewModel) {
        roomViewModel.gameLaunchEvents.collect { event ->
            when (event) {
                is com.example.chudadi.ui.room.RoomGameLaunchEvent.StartLocalMatch -> {
                    viewModel.dispatch(
                        LocalGameAction.StartLocalMatch(
                            seatConfigs = event.seatConfigs,
                            localSeatId = event.localSeatId,
                            ruleSet = event.ruleSet,
                        ),
                    )
                    requestedRoute = AppFlowRoute.GAME
                }
            }
        }
    }
    LaunchedEffect(roomViewModel) {
        roomViewModel.externalEvents.collect { event ->
            when (event) {
                com.example.chudadi.ui.room.RoomExternalEvent.RequestStartHostListening -> {
                    requestBluetoothHostReady {
                        val created = roomViewModel.startHostListening(localDeviceName)
                        if (created) {
                            requestedRoute = AppFlowRoute.ROOM
                        }
                    }
                }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = AppFlowRoute.HOME.route,
    ) {
        composable(AppFlowRoute.HOME.route) {
            HomeScreen(
                playerName = playerName,
                noticeMessage = roomUiState.homeNoticeMessage,
                onDismissNotice = { roomViewModel.dispatch(RoomAction.ConsumeHomeNotice) },
                onCreateRoom = {
                    roomViewModel.createLocalRoom(localDeviceName)
                    requestedRoute = AppFlowRoute.ROOM
                },
                onJoinRoom = {
                    requestBluetoothConnectReady(runJoinFlow)
                },
                onSettings = {
                    requestedRoute = AppFlowRoute.SETTINGS
                },
            )
        }
        composable(AppFlowRoute.SETTINGS.route) {
            val settingsViewModel: SettingsViewModel = viewModel(
                factory = SettingsViewModel.factory(playerPreferencesRepository),
            )
            SettingsScreen(
                viewModel = settingsViewModel,
                onNavigateBack = { requestedRoute = AppFlowRoute.HOME },
            )
        }
        composable(AppFlowRoute.BLUETOOTH_SEARCH.route) {
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
                    requestedRoute = AppFlowRoute.HOME
                },
                onDismissJoinError = {
                    roomViewModel.dispatch(RoomAction.ConsumeJoinError)
                },
            )
        }
        composable(AppFlowRoute.ROOM.route) {
            RoomScreen(
                uiState = roomUiState,
                onAction = { action ->
                    when (action) {
                        is RoomAction.StartGame -> {
                            roomViewModel.dispatch(action)
                        }

                        is RoomAction.ExitRoom -> {
                            roomViewModel.dispatch(action)
                            requestedRoute = AppFlowRoute.HOME
                        }

                        else -> roomViewModel.dispatch(action)
                    }
                },
                onNavigateBack = {
                    roomViewModel.dispatch(RoomAction.ResetRoom)
                    requestedRoute = AppFlowRoute.HOME
                },
            )
        }
        composable(AppFlowRoute.GAME.route) {
            GameScreenRoute(
                localViewModel = viewModel,
                roomViewModel = roomViewModel,
                uiState = appFlowState.activeMatchUiState,
                useNetworkMatch = appFlowState.useNetworkMatch,
            )
        }
        composable(AppFlowRoute.RESULT.route) {
            val roundScores = appFlowState.activeMatchUiState.resultSummary?.roundScores.orEmpty()
            LaunchedEffect(roundScores, appFlowState.useNetworkMatch) {
                if (!appFlowState.useNetworkMatch && roundScores.isNotEmpty()) {
                    roomViewModel.dispatch(RoomAction.AccumulateScores(roundScores))
                }
            }
            ResultScreen(
                uiState = appFlowState.activeMatchUiState,
                onReturnToRoom = {
                    if (appFlowState.useNetworkMatch) {
                        roomViewModel.dispatchGameAction(LocalGameAction.ExitToHome)
                    } else {
                        viewModel.dispatch(LocalGameAction.ExitToHome)
                    }
                    requestedRoute = AppFlowRoute.ROOM
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

private fun navigateToFlowRoute(
    navController: androidx.navigation.NavHostController,
    event: AppFlowNavigationEvent,
) {
    if (navController.currentDestination?.route == event.route.route &&
        event.popUpTo == null
    ) {
        return
    }
    navController.navigate(event.route.route) {
        event.popUpTo?.let { target ->
            popUpTo(target.route) {
                inclusive = event.inclusive
            }
        }
        launchSingleTop = true
    }
}
