package com.example.chudadi.navigation

import com.example.chudadi.model.game.entity.MatchPhase
import com.example.chudadi.model.game.snapshot.MatchUiState
import com.example.chudadi.ui.room.RoomUiState

enum class AppFlowRoute(val route: String) {
    HOME("home"),
    SETTINGS("settings"),
    BLUETOOTH_SEARCH("bluetooth-search"),
    ROOM("room"),
    GAME("game"),
    RESULT("result"),
}

data class AppFlowState(
    val currentRoute: AppFlowRoute = AppFlowRoute.HOME,
    val activeMatchUiState: MatchUiState = MatchUiState(),
    val useNetworkMatch: Boolean = false,
)

data class AppFlowNavigationEvent(
    val route: AppFlowRoute,
    val popUpTo: AppFlowRoute? = null,
    val inclusive: Boolean = false,
)

fun buildAppFlowState(
    requestedRoute: AppFlowRoute,
    localMatchUiState: MatchUiState,
    roomUiState: RoomUiState,
    networkMatchUiState: MatchUiState,
): AppFlowState {
    val useNetworkMatch = networkMatchUiState.phase != MatchPhase.NOT_STARTED
    val activeMatchUiState = if (useNetworkMatch) networkMatchUiState else localMatchUiState
    val hasLocalSeat = roomUiState.slots.any { it.isLocalPlayer && it.occupantType != null }
    val roomStillActive = !roomUiState.removedFromRoom && !roomUiState.roomClosedByHost
    val currentRoute = when {
        activeMatchUiState.phase == MatchPhase.FINISHED -> AppFlowRoute.RESULT
        activeMatchUiState.phase != MatchPhase.NOT_STARTED -> AppFlowRoute.GAME
        requestedRoute == AppFlowRoute.BLUETOOTH_SEARCH && hasLocalSeat && roomStillActive -> AppFlowRoute.ROOM
        else -> requestedRoute
    }
    return AppFlowState(
        currentRoute = currentRoute,
        activeMatchUiState = activeMatchUiState,
        useNetworkMatch = useNetworkMatch,
    )
}
