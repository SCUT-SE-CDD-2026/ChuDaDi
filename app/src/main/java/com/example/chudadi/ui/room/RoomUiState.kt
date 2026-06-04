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
    @get:DrawableRes val avatarResId: Int? = null,
    val connectionStatus: MemberConnectionStatus? = null,
    val aiDifficulty: RoomAiDifficulty? = null,
    val aiType: AIType? = null,
    val cumulativeScore: Int = 0,
    val isLocalPlayer: Boolean = false,
)

enum class RoomAiDifficulty(val label: String, val aiType: AIType, val difficultyLevel: DifficultyLevel) {
    // 规则型 AI
    RULE_EASY("规则型 AI - 简单", AIType.RULE_BASED, DifficultyLevel.EASY),
    RULE_NORMAL("规则型 AI - 普通", AIType.RULE_BASED, DifficultyLevel.NORMAL),
    RULE_HARD("规则型 AI - 困难", AIType.RULE_BASED, DifficultyLevel.HARD),

    // ONNX RL AI
    ONNX_EASY("RL训练 AI - 简单", AIType.ONNX_RL, DifficultyLevel.EASY),
    ONNX_NORMAL("RL训练 AI - 普通", AIType.ONNX_RL, DifficultyLevel.NORMAL),
    ONNX_HARD("RL训练 AI - 困难", AIType.ONNX_RL, DifficultyLevel.HARD),

    // 扩展 RL AI（不区分 UI 难度，固定 HARD / argmax）
    ONNX_V2("RL-V2", AIType.ONNX_RL_V2, DifficultyLevel.HARD),
    ONNX_V3("RL-V3", AIType.ONNX_RL_V3, DifficultyLevel.HARD),
}

enum class AIType {
    RULE_BASED,
    ONNX_RL,
    ONNX_RL_V2,
    ONNX_RL_V3,
}

val AIType.shortLabel: String
    get() = when (this) {
        AIType.RULE_BASED -> "规则"
        AIType.ONNX_RL -> "RL"
        AIType.ONNX_RL_V2 -> "RL2"
        AIType.ONNX_RL_V3 -> "RL3"
    }

val DifficultyLevel.symbol: String
    get() = when (this) {
        DifficultyLevel.EASY -> "E"
        DifficultyLevel.NORMAL -> "N"
        DifficultyLevel.HARD -> "H"
    }

enum class DifficultyLevel {
    EASY,
    NORMAL,
    HARD,
}

enum class GameRuleDisplay(val label: String) {
    SOUTHERN("南方规则"),
    NORTHERN("北方规则"),
}

enum class AiPlaySpeed(
    val label: String,
    val delayMillis: Long,
    val autoRounds: Int = 0,
) {
    SLOW(label = "慢", delayMillis = 3_000L),
    NORMAL(label = "中", delayMillis = 1_000L),
    FAST(label = "快", delayMillis = 500L),
    VFAST(label = "极快", delayMillis = 1L),
    DEBUG_100_ROUNDS(label = "100轮", delayMillis = 0L, autoRounds = 100),
}

data class RoomUiState(
    val isHost: Boolean = true,
    val roomMode: RoomMode = RoomMode.LOCAL,
    val roomName: String = "",
    val hostDeviceName: String = "",
    val totalGamesPlayed: Int = 0,
    val currentRule: GameRuleDisplay = GameRuleDisplay.SOUTHERN,
    val aiPlaySpeed: AiPlaySpeed = AiPlaySpeed.NORMAL,
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
    val joinErrorTitle: String = "无法加入房间",
    val showRoomAiDifficultyDialog: Boolean = false,
    val aiDialogTargetSlot: Int = -1,
    val aiSelectionStep: AiSelectionStep = AiSelectionStep.SELECT_TYPE,
    val selectedAiType: AIType? = null,
    val showSlotActionMenu: Boolean = false,
    val slotActionMenuTarget: Int = -1,
)

enum class AiSelectionStep {
    SELECT_TYPE,
    SELECT_DIFFICULTY,
    SELECT_EXTENDED_AI,
}
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
