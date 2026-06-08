package com.example.chudadi.audio

import com.example.chudadi.model.game.entity.MatchPhase
import com.example.chudadi.model.game.snapshot.MatchUiState

/**
 * 纯 Kotlin 状态变化检测器。对比新旧 MatchUiState，派发音频事件。
 * 无 Android 依赖，可 JVM 单元测试。
 */
class AudioEventDetector {

    fun detect(old: MatchUiState, new: MatchUiState): List<AudioEvent> {
        val events = mutableListOf<AudioEvent>()

        // 新出牌上桌
        if (new.tablePlays.size > old.tablePlays.size) {
            events.add(AudioEvent.Sfx.CardPlay)
        }

        // 轮到人类玩家（仅在 PLAYER_TURN 阶段）
        if (!old.isHumanTurn && new.isHumanTurn &&
            new.phase == MatchPhase.PLAYER_TURN
        ) {
            events.add(AudioEvent.Sfx.YourTurn)
        }

        // 一轮结束
        if (old.phase == MatchPhase.PLAYER_TURN &&
            new.phase == MatchPhase.ROUND_RESET
        ) {
            events.add(AudioEvent.Sfx.TrickWon)
        }

        // 游戏结束
        if (old.phase != MatchPhase.FINISHED &&
            new.phase == MatchPhase.FINISHED
        ) {
            val isWin = isLocalPlayerWinner(new)
            events.add(if (isWin) AudioEvent.Sfx.GameWin else AudioEvent.Sfx.GameLose)
        }

        return events
    }

    private fun isLocalPlayerWinner(state: MatchUiState): Boolean {
        if (state.resultSummary == null) return false
        return state.playerHand.isEmpty()
    }
}
