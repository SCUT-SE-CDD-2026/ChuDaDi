package com.example.chudadi.ui.room

import androidx.annotation.DrawableRes

enum class SlotOccupantType {
    HUMAN_HOST,
    HUMAN_MEMBER,
    AI,
}

enum class MemberConnectionStatus {
    CONNECTED,
    DISCONNECTED,
    READY,
    NOT_READY,
}

enum class BluetoothSearchState {
    IDLE,
    SCANNING,
    CONNECTING,
    FAILED,
}

data class SlotState(
    val slotIndex: Int,
    val seatId: Int = slotIndex,
    val participantId: String? = null,
    val occupantType: SlotOccupantType? = null,
    val displayName: String = "",
    @DrawableRes val avatarResId: Int? = null,
    val connectionStatus: MemberConnectionStatus? = null,
    val aiDifficulty: AiDifficulty? = null,
    val cumulativeScore: Int = 0,
    val isLocalPlayer: Boolean = false,
)

enum class AiDifficulty(val label: String) {
    RULE_BASED("规则型 AI"),
}

enum class GameRuleDisplay(val label: String) {
    SOUTHERN("南方规则"),
    NORTHERN("北方规则"),
}

data class RoomUiState(
    val isHost: Boolean = true,
    val roomMode: RoomMode = RoomMode.LOCAL,
    val roomName: String = "",
    val hostDeviceName: String = "",
    val currentRule: GameRuleDisplay = GameRuleDisplay.SOUTHERN,
    val slots: List<SlotState> = List(4) { SlotState(slotIndex = it) },
    val bluetoothVisible: Boolean = false,
    val connectionHint: String = "",
    val homeNoticeMessage: String? = null,
    val discoveredDevices: List<DiscoveredDeviceUiState> = emptyList(),
    val searchState: BluetoothSearchState = BluetoothSearchState.IDLE,
    val selectedDeviceAddress: String? = null,
    val canStartGame: Boolean = false,
    val canStartLocalGame: Boolean = false,
    val canStartNetworkGame: Boolean = false,
    val canEnableBroadcast: Boolean = false,
    val canManageAiSeats: Boolean = false,
    val pendingSwapRequest: SwapRequest? = null,
    val removedFromRoom: Boolean = false,
    val roomClosedByHost: Boolean = false,
    val joinErrorMessage: String? = null,
    val showAiDifficultyDialog: Boolean = false,
    val aiDialogTargetSlot: Int = -1,
    val showSlotActionMenu: Boolean = false,
    val slotActionMenuTarget: Int = -1,
)

data class DiscoveredDeviceUiState(
    val name: String,
    val address: String,
    val isBonded: Boolean,
)

data class SwapRequest(
    val requesterSlotIndex: Int,
    val targetSlotIndex: Int,
    val requesterName: String,
)
