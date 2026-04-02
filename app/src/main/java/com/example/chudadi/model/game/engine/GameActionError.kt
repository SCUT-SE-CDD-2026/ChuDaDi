package com.example.chudadi.model.game.engine

enum class GameActionError {
    MATCH_FINISHED,
    NOT_CURRENT_TURN,
    INVALID_PLAY_TYPE,
    OPENING_MOVE_MUST_CONTAIN_DIAMOND_THREE,
    PLAY_DOES_NOT_BEAT_CURRENT,
    CANNOT_PASS_LEAD_TURN,
    MUST_BEAT_IF_POSSIBLE,
}
