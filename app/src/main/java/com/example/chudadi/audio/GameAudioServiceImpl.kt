package com.example.chudadi.audio

import android.content.Context
import android.media.AudioManager
import com.example.chudadi.data.repository.PlayerPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * [GameAudioService] 的默认实现。
 *
 * 职责：
 * - 持有 [SfxPlayer] 和 [BgmPlayer] 实例
 * - 从 [PlayerPreferencesRepository] 同步音效/BGM 开关状态
 * - 根据开关状态决定是否真正播放
 * - 计算 SFX 音量（基于系统 STREAM_MUSIC 音量）
 * - 管理 isReleased 防止重复释放
 */
class GameAudioServiceImpl(
    private val context: Context,
    private val prefsRepository: PlayerPreferencesRepository,
) : GameAudioService {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val audioManager: AudioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val sfxPlayerLazy = lazy { SoundPoolSfxPlayer(context) }
    private val bgmPlayerLazy = lazy { MediaPlayerBgmPlayer(context) }
    private val sfxPlayer: SfxPlayer by sfxPlayerLazy
    private val bgmPlayer: BgmPlayer by bgmPlayerLazy

    private val _soundEnabled = MutableStateFlow(true)
    override val soundEnabled: StateFlow<Boolean> = _soundEnabled.asStateFlow()

    private val _bgmEnabled = MutableStateFlow(true)
    override val bgmEnabled: StateFlow<Boolean> = _bgmEnabled.asStateFlow()

    private var isReleased = false

    init {
        scope.launch {
            prefsRepository.soundEnabled.collect { enabled ->
                _soundEnabled.value = enabled
            }
        }
        scope.launch {
            prefsRepository.bgmEnabled.collect { enabled ->
                _bgmEnabled.value = enabled
                if (!enabled) stopBgm()
            }
        }
    }

    override suspend fun preloadSfx() {
        if (isReleased) return
        sfxPlayer.preload(
            listOf(
                AudioEvent.Sfx.CardPlay,
                AudioEvent.Sfx.YourTurn,
                AudioEvent.Sfx.TrickWon,
                AudioEvent.Sfx.GameWin,
                AudioEvent.Sfx.GameLose,
            ),
        )
    }

    override fun playSfx(event: AudioEvent.Sfx) {
        if (isReleased) return
        if (!soundEnabled.value) return
        sfxPlayer.play(event, currentVolume())
    }

    override fun playBgm(event: AudioEvent.Bgm) {
        if (isReleased) return
        if (!bgmEnabled.value) return
        bgmPlayer.play(event)
    }

    override fun stopBgm() {
        if (isReleased) return
        bgmPlayer.stop()
    }

    override fun pauseAll() {
        if (isReleased) return
        bgmPlayer.pause()
    }

    override fun resumeAll() {
        if (isReleased) return
        bgmPlayer.resume()
    }

    override fun release() {
        if (isReleased) return
        isReleased = true
        scope.cancel()
        if (sfxPlayerLazy.isInitialized()) sfxPlayer.release()
        if (bgmPlayerLazy.isInitialized()) bgmPlayer.release()
    }

    override fun setSoundEnabled(enabled: Boolean) {
        if (isReleased) return
        _soundEnabled.value = enabled
        scope.launch {
            prefsRepository.updateSoundEnabled(enabled)
        }
    }

    override fun setBgmEnabled(enabled: Boolean) {
        if (isReleased) return
        _bgmEnabled.value = enabled
        scope.launch {
            prefsRepository.updateBgmEnabled(enabled)
        }
    }

    /** 根据 STREAM_MUSIC 系统音量计算 0..1 的音量比例 */
    private fun currentVolume(): Float {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (max <= 0) return 0f
        return audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) / max.toFloat()
    }
}
