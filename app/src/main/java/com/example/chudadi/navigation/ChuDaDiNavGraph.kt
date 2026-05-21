package com.example.chudadi.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.runtime.mutableStateOf
import com.example.chudadi.controller.game.LocalGameAction
import com.example.chudadi.controller.game.LocalMatchViewModel
import com.example.chudadi.controller.game.OnnxMatchViewModel
import com.example.chudadi.controller.game.SeatConfig
import com.example.chudadi.data.repository.PlayerPreferencesRepository
import com.example.chudadi.model.game.entity.MatchPhase
import com.example.chudadi.model.game.entity.SeatControllerType
import com.example.chudadi.model.game.rule.GameRuleSet
import com.example.chudadi.model.game.snapshot.MatchUiState
import com.example.chudadi.ui.game.GameScreen
import com.example.chudadi.ui.game.GameScreenActions
import com.example.chudadi.ui.home.HomeScreen
import com.example.chudadi.ui.result.ResultScreen
import com.example.chudadi.ui.room.AiPlaySpeed
import com.example.chudadi.ui.room.BluetoothSearchScreen
import com.example.chudadi.ui.room.BluetoothSearchState
import com.example.chudadi.ui.room.RoomAction
import com.example.chudadi.ui.room.RoomScreen
import com.example.chudadi.ui.room.RoomUiState
import com.example.chudadi.ui.room.RoomViewModel
import com.example.chudadi.ui.settings.SettingsScreen
import com.example.chudadi.ui.settings.SettingsViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class GameViewModelType { LOCAL, ONNX }

private data class AutoRoundState(
    val isActive: Boolean = false,
    val roundsRemaining: Int = 0,
)

private data class PendingOnnxStart(
    val seatConfigs: List<SeatConfig>,
    val localSeatId: Int,
    val ruleSet: GameRuleSet,
    val aiMoveDelayMillis: Long,
)

private fun hasOnnxAiSeats(seatConfigs: List<SeatConfig>): Boolean {
    return seatConfigs.any { it.controllerType == SeatControllerType.ONNX_RL_AI }
}

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
    var requestedRoute by rememberSaveable { mutableStateOf(AppFlowRoute.HOME) }
    var gameViewModelType by remember { mutableStateOf(GameViewModelType.LOCAL) }
    var autoRoundState by remember { mutableStateOf(AutoRoundState()) }
    var pendingAiPlaySpeed by remember { mutableStateOf<AiPlaySpeed?>(null) }
    var pendingOnnxStart by remember { mutableStateOf<PendingOnnxStart?>(null) }
    var isGameStarting by remember { mutableStateOf(false) }
    val uiState = viewModel.uiState.collectAsStateWithLifecycle().value
    val roomUiState = roomViewModel.uiState.collectAsStateWithLifecycle().value
    val networkMatchUiState = roomViewModel.matchUiState.collectAsStateWithLifecycle().value
    // Collect ONNX state for route-aware navigation
    val onnxViewModel: OnnxMatchViewModel = viewModel()
    val onnxUiState by onnxViewModel.uiState.collectAsStateWithLifecycle()
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
                    val seatConfigs = event.seatConfigs
                    if (hasOnnxAiSeats(seatConfigs)) {
                        gameViewModelType = GameViewModelType.ONNX
                        autoRoundState = AutoRoundState(
                            isActive = seatConfigs.all {
                                it.controllerType != SeatControllerType.HUMAN
                            } && roomUiState.aiPlaySpeed == AiPlaySpeed.DEBUG_100_ROUNDS,
                            roundsRemaining = AiPlaySpeed.DEBUG_100_ROUNDS.autoRounds,
                        )
                        pendingAiPlaySpeed = roomUiState.aiPlaySpeed
                        isGameStarting = true
                        pendingOnnxStart = PendingOnnxStart(
                            seatConfigs = seatConfigs,
                            localSeatId = event.localSeatId,
                            ruleSet = event.ruleSet,
                            aiMoveDelayMillis = event.aiMoveDelayMillis,
                        )
                    } else {
                        gameViewModelType = GameViewModelType.LOCAL
                        pendingOnnxStart = null
                        viewModel.dispatch(
                            LocalGameAction.StartLocalMatch(
                                seatConfigs = event.seatConfigs,
                                localSeatId = event.localSeatId,
                                ruleSet = event.ruleSet,
                                aiMoveDelayMillis = event.aiMoveDelayMillis,
                            ),
                        )
                    }
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
                        scope.launch {
                            val created = roomViewModel.startHostListening(localDeviceName)
                            if (created) {
                                requestedRoute = AppFlowRoute.ROOM
                            }
                        }
                    }
                }
            }
        }
    }
    // Auto-navigate for ONNX game state changes (GAME → RESULT → ROOM)
    LaunchedEffect(onnxUiState.phase, gameViewModelType) {
        if (gameViewModelType == GameViewModelType.ONNX && !appFlowState.useNetworkMatch) {
            when (onnxUiState.phase) {
                MatchPhase.FINISHED -> {
                    if (requestedRoute == AppFlowRoute.GAME) {
                        requestedRoute = AppFlowRoute.RESULT
                    }
                }
                MatchPhase.NOT_STARTED -> {
                    if (requestedRoute == AppFlowRoute.RESULT || requestedRoute == AppFlowRoute.GAME) {
                        // Only reset if not in auto-round mode and not starting a new game
                        if (!autoRoundState.isActive && !isGameStarting) {
                            requestedRoute = AppFlowRoute.ROOM
                        }
                    }
                }
                else -> {
                    // When game successfully starts (phase changes from NOT_STARTED to DEALING/PLAYER_TURN),
                    // clear the starting flag so that future NOT_STARTED transitions will navigate properly
                    if (isGameStarting) {
                        isGameStarting = false
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
            val cancelPendingConnection = {
                roomViewModel.dispatch(RoomAction.CancelPendingConnection)
            }
            val leaveBluetoothSearch = {
                cancelPendingConnection()
                roomViewModel.dispatch(RoomAction.ConsumeJoinError)
                requestedRoute = AppFlowRoute.HOME
            }
            BackHandler {
                leaveBluetoothSearch()
            }
            DisposableEffect(Unit) {
                onDispose {
                    roomViewModel.dispatch(RoomAction.StopBluetoothDiscovery)
                    roomViewModel.dispatch(RoomAction.CancelPendingConnectionIfNotJoined)
                }
            }
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
                    leaveBluetoothSearch()
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
            val activeUiState = when {
                appFlowState.useNetworkMatch -> appFlowState.activeMatchUiState
                gameViewModelType == GameViewModelType.ONNX -> onnxUiState
                else -> appFlowState.activeMatchUiState
            }
            // Dispatch ONNX start when pending params are available
            val startParams = pendingOnnxStart
            if (startParams != null && gameViewModelType == GameViewModelType.ONNX) {
                LaunchedEffect(startParams) {
                    onnxViewModel.onRequestStartLocalMatch(
                        seatConfigs = startParams.seatConfigs,
                        localSeatId = startParams.localSeatId,
                        ruleSet = startParams.ruleSet,
                        aiMoveDelayMillis = startParams.aiMoveDelayMillis,
                    )
                    pendingOnnxStart = null
                    isGameStarting = false
                }
            }
            GameScreenRoute(
                localViewModel = viewModel,
                onnxViewModel = onnxViewModel,
                roomViewModel = roomViewModel,
                uiState = activeUiState,
                useNetworkMatch = appFlowState.useNetworkMatch,
                gameViewModelType = gameViewModelType,
            )
        }
        composable(AppFlowRoute.RESULT.route) {
            val resultUiState = when {
                appFlowState.useNetworkMatch -> appFlowState.activeMatchUiState
                gameViewModelType == GameViewModelType.ONNX -> onnxUiState
                else -> appFlowState.activeMatchUiState
            }
            val roundScores = resultUiState.resultSummary?.roundScores.orEmpty()
            LaunchedEffect(roundScores, appFlowState.useNetworkMatch) {
                if (!appFlowState.useNetworkMatch && roundScores.isNotEmpty()) {
                    roomViewModel.dispatch(RoomAction.AccumulateScores(roundScores))
                }
            }
            // Auto-round loop for DEBUG_100_ROUNDS mode (all-AI seats only)
            if (autoRoundState.isActive && gameViewModelType == GameViewModelType.ONNX) {
                LaunchedEffect(resultUiState.phase) {
                    if (resultUiState.phase == MatchPhase.FINISHED && autoRoundState.roundsRemaining > 0) {
                        delay(AUTO_ROUND_DELAY_MS)
                        val remaining = autoRoundState.roundsRemaining - 1
                        autoRoundState = autoRoundState.copy(roundsRemaining = remaining)
                        if (remaining > 0) {
                            onnxViewModel.onRequestRestartMatch()
                            requestedRoute = AppFlowRoute.GAME
                        } else {
                            autoRoundState = autoRoundState.copy(isActive = false)
                            if (gameViewModelType == GameViewModelType.ONNX) {
                                onnxViewModel.onExitToHome()
                            } else {
                                viewModel.dispatch(LocalGameAction.ExitToHome)
                            }
                            requestedRoute = AppFlowRoute.ROOM
                        }
                    }
                }
            }
            ResultScreen(
                uiState = resultUiState,
                onReturnToRoom = {
                    autoRoundState = autoRoundState.copy(isActive = false)
                    if (appFlowState.useNetworkMatch) {
                        roomViewModel.dispatchGameAction(LocalGameAction.ExitToHome)
                    } else if (gameViewModelType == GameViewModelType.ONNX) {
                        onnxViewModel.onExitToHome()
                    } else {
                        viewModel.dispatch(LocalGameAction.ExitToHome)
                    }
                    requestedRoute = AppFlowRoute.ROOM
                },
            )
        }
    }
}

@Suppress("LongParameterList")
@Composable
private fun GameScreenRoute(
    localViewModel: LocalMatchViewModel,
    onnxViewModel: OnnxMatchViewModel,
    roomViewModel: RoomViewModel,
    uiState: MatchUiState,
    useNetworkMatch: Boolean,
    gameViewModelType: GameViewModelType,
) {
    GameScreen(
        uiState = uiState,
        actions = GameScreenActions(
            onToggleCardSelection = { cardId ->
                when {
                    useNetworkMatch -> roomViewModel.dispatchGameAction(
                        LocalGameAction.ToggleCardSelection(cardId),
                    )
                    gameViewModelType == GameViewModelType.ONNX -> onnxViewModel.onToggleCardSelection(cardId)
                    else -> localViewModel.dispatch(LocalGameAction.ToggleCardSelection(cardId))
                }
            },
            onClearSelection = {
                when {
                    useNetworkMatch -> roomViewModel.dispatchGameAction(LocalGameAction.ClearSelection)
                    gameViewModelType == GameViewModelType.ONNX -> onnxViewModel.onClearSelection()
                    else -> localViewModel.dispatch(LocalGameAction.ClearSelection)
                }
            },
            onSubmitSelectedCards = {
                when {
                    useNetworkMatch -> roomViewModel.dispatchGameAction(LocalGameAction.SubmitSelectedCards)
                    gameViewModelType == GameViewModelType.ONNX -> onnxViewModel.onRequestPlayCards()
                    else -> localViewModel.dispatch(LocalGameAction.SubmitSelectedCards)
                }
            },
            onPassTurn = {
                when {
                    useNetworkMatch -> roomViewModel.dispatchGameAction(LocalGameAction.PassTurn)
                    gameViewModelType == GameViewModelType.ONNX -> onnxViewModel.onRequestPass()
                    else -> localViewModel.dispatch(LocalGameAction.PassTurn)
                }
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

private const val AUTO_ROUND_DELAY_MS = 300L
