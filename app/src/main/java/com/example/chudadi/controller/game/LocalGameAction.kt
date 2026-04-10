package com.example.chudadi.controller.game

import com.example.chudadi.ai.base.AIDifficulty
import com.example.chudadi.model.game.entity.SeatControllerType
import com.example.chudadi.model.game.rule.GameRuleSet

sealed interface LocalGameAction {
    data class StartLocalMatch(
        val seatConfigs: List<SeatConfig>? = null,
        val localSeatId: Int = 0,
        val ruleSet: GameRuleSet = GameRuleSet.SOUTHERN,
    ) : LocalGameAction
    data class ToggleCardSelection(val cardId: String) : LocalGameAction
    data object ClearSelection : LocalGameAction
    data object SubmitSelectedCards : LocalGameAction
    data object PassTurn : LocalGameAction
    data object RestartMatch : LocalGameAction
    data object ExitToHome : LocalGameAction
}

/**
 * 座位配置数据类
 *
 * @param seatIndex 座位索引
 * @param name 玩家/AI名称
 * @param controllerType 控制器类型（人类/规则AI/ONNX AI）
 * @param aiDifficulty AI难度（仅AI类型有效）
 */
data class SeatConfig(
    val seatIndex: Int,
    val name: String,
    val controllerType: SeatControllerType,
    val aiDifficulty: AIDifficulty? = null,
)
