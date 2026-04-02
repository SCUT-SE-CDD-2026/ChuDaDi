package com.example.chudadi.controller.client

import com.example.chudadi.model.game.engine.GameActionError

object GameActionMessageFormatter {
    fun format(error: GameActionError?): String? {
        return when (error) {
            GameActionError.MATCH_FINISHED -> "本局已经结束"
            GameActionError.NOT_CURRENT_TURN -> "当前还没轮到你"
            GameActionError.INVALID_PLAY_TYPE -> "牌型不合法"
            GameActionError.OPENING_MOVE_MUST_CONTAIN_DIAMOND_THREE -> "首轮首手必须包含方块 3"
            GameActionError.PLAY_DOES_NOT_BEAT_CURRENT -> "当前牌不够大"
            GameActionError.CANNOT_PASS_LEAD_TURN -> "本轮领出者必须先出牌"
            GameActionError.MUST_BEAT_IF_POSSIBLE -> "北方规则下有可压牌时不能要不起"
            null -> null
        }
    }
}
