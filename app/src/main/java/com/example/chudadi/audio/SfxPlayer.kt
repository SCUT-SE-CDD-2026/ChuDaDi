package com.example.chudadi.audio

/** 音效播放器接口（Strategy: SoundPool 实现） */
interface SfxPlayer {
    suspend fun preload(effects: Collection<AudioEvent.Sfx>)
    fun play(effect: AudioEvent.Sfx, volume: Float)
    fun release()
}
