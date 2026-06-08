package com.example.chudadi.audio

import com.example.chudadi.R

/**
 * 音频事件类型。游戏状态变化时由 AudioEventDetector 检测并派发。
 * BGM 停止由 GameAudioService.stopBgm() 直接处理，不放入此 sealed class。
 */
sealed class AudioEvent {
    /** 短音效（SoundPool 播放） */
    sealed class Sfx(val rawResId: Int) : AudioEvent() {
        data object CardPlay : Sfx(R.raw.sfx_card_play)
        data object YourTurn : Sfx(R.raw.sfx_your_turn)
        data object TrickWon : Sfx(R.raw.sfx_trick_won)
        data object GameWin : Sfx(R.raw.sfx_game_win)
        data object GameLose : Sfx(R.raw.sfx_game_lose)
    }

    /** 背景音乐（MediaPlayer 播放） */
    sealed class Bgm(val rawResId: Int) : AudioEvent() {
        data object Menu : Bgm(R.raw.bgm_menu)
        data object Game : Bgm(R.raw.bgm_game)
    }
}
