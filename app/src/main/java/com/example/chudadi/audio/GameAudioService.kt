package com.example.chudadi.audio

import kotlinx.coroutines.flow.StateFlow

/** 音频服务 Facade 接口 */
interface GameAudioService {
    suspend fun preloadSfx()
    fun playSfx(event: AudioEvent.Sfx)
    fun playBgm(event: AudioEvent.Bgm)
    fun stopBgm()
    fun pauseAll()
    fun resumeAll()
    fun release()

    val soundEnabled: StateFlow<Boolean>
    val bgmEnabled: StateFlow<Boolean>

    fun setSoundEnabled(enabled: Boolean)
    fun setBgmEnabled(enabled: Boolean)
}
