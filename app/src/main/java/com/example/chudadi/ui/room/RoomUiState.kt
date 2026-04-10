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
    val slotIndex: Int,
    val seatId: Int = slotIndex,
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
}

enum class AIType {
    RULE_BASED,
    ONNX_RL,
}

val AIType.shortLabel: String
    get() = when (this) {
        AIType.RULE_BASED -> "规则"
        AIType.ONNX_RL -> "RL"
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

data class RoomUiState(
    val isHost: Boolean = true,
    val roomName: String = "",
    val hostDeviceName: String = "",
    val currentRule: GameRuleDisplay = GameRuleDisplay.SOUTHERN,
    val slots: List<SlotState> = List(4) { SlotState(slotIndex = it) },
    val bluetoothVisible: Boolean = false,
    val connectionHint: String = "",
    val canStartGame: Boolean = false,
    val pendingSwapRequest: SwapRequest? = null,
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
}

data class SwapRequest(
    val requesterSlotIndex: Int,
    val targetSlotIndex: Int,
    val requesterName: String,
)
