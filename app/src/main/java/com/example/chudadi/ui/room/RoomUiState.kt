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

data class SlotState(
    val seatIndex: Int,
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
    val roomName: String = "",
    val hostDeviceName: String = "",
    val currentRule: GameRuleDisplay = GameRuleDisplay.SOUTHERN,
    val slots: List<SlotState> = List(4) { SlotState(seatIndex = it) },
    val bluetoothVisible: Boolean = false,
    val connectionHint: String = "",
    val canStartGame: Boolean = false,
    val localPlayerSeatIndex: Int = 0,
    val pendingSwapRequest: SwapRequest? = null,
    val showAiDifficultyDialog: Boolean = false,
    val aiDialogTargetSlot: Int = -1,
    val showSlotActionMenu: Boolean = false,
    val slotActionMenuTarget: Int = -1,
)

data class SwapRequest(
    val requesterSeatIndex: Int,
    val targetSeatIndex: Int,
    val requesterName: String,
)
