package com.example.chudadi.audio

/** 背景音乐播放器接口（Strategy: MediaPlayer 实现） */
interface BgmPlayer {
    fun play(track: AudioEvent.Bgm, loop: Boolean = true)
    fun stop()
    fun pause()
    fun resume()
    fun release()
    val isPlaying: Boolean
}
