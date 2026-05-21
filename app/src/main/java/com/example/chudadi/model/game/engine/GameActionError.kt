package com.example.chudadi.model.game.engine

enum class GameActionError {
    MATCH_FINISHED,
    NOT_CURRENT_TURN,
    INVALID_PLAY_TYPE,
    OPENING_MOVE_MUST_CONTAIN_DIAMOND_THREE,
    /** 当前打出的牌型点数或花色不足以压过上一手 */
    PLAY_TOO_SMALL,
    /** 当前规则下不满足炸弹的使用条件（如北方规则有同类型响应时不可先出炸弹） */
    BOMB_USAGE_RESTRICTED,
    CANNOT_PASS_LEAD_TURN,
    MUST_BEAT_IF_POSSIBLE,
}
