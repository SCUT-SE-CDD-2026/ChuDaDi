package com.example.chudadi.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build

/**
 * 基于 Android MediaPlayer 的背景音乐播放器实现。
 *
 * 职责：
 * - 播放/暂停/恢复/停止 BGM
 * - 管理 AudioFocus（获取/释放）
 * - MediaPlayer 生命周期管理
 */
@Suppress("TooManyFunctions")
class MediaPlayerBgmPlayer(private val context: Context) : BgmPlayer {

    private var mediaPlayer: MediaPlayer? = null
    private var currentTrack: AudioEvent.Bgm? = null

    /** 记录音频焦点临时丢失时是否正在播放，用于焦点恢复后决定是否 resume */
    private var wasPlayingBeforeFocusLoss = false

    private val audioManager: AudioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var audioFocusRequest: AudioFocusRequest? = null

    private val onFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        onAudioFocusChange(focusChange)
    }

    private fun onAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                wasPlayingBeforeFocusLoss = isPlaying
                stop()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                wasPlayingBeforeFocusLoss = isPlaying
                pause()
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (wasPlayingBeforeFocusLoss) {
                    resume()
                    wasPlayingBeforeFocusLoss = false
                }
            }
        }
    }

    @Suppress("ReturnCount")
    override fun play(track: AudioEvent.Bgm, loop: Boolean) {
        // 同一首曲目且正在播放 → 不重复创建
        val existing = mediaPlayer
        if (existing != null && currentTrack == track && existing.isPlaying) return

        // 不同曲目 → 释放旧 MediaPlayer
        if (currentTrack != null && currentTrack != track) {
            releasePlayer()
        }

        // 请求 AudioFocus
        if (!requestFocus()) return

        val mp = MediaPlayer.create(context, track.rawResId, BGM_AUDIO_ATTRIBUTES, 0)
            ?: return
        mp.isLooping = loop
        mp.start()
        mediaPlayer = mp
        currentTrack = track
    }

    override fun stop() {
        mediaPlayer?.let {
            if (it.isPlaying) it.stop()
        }
        abandonFocus()
    }

    override fun pause() {
        mediaPlayer?.takeIf { it.isPlaying }?.pause()
    }

    override fun resume() {
        mediaPlayer?.let {
            if (!it.isPlaying) {
                it.start()
            }
        }
    }

    override fun release() {
        releasePlayer()
    }

    override val isPlaying: Boolean
        get() = mediaPlayer?.isPlaying == true

    private fun releasePlayer() {
        mediaPlayer?.apply {
            try {
                if (isPlaying) stop()
            } finally {
                release()
            }
        }
        mediaPlayer = null
        currentTrack = null
        abandonFocus()
    }

    /**
     * 请求音频焦点。
     */
    private fun requestFocus(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = getOrCreateFocusRequest()
            audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                onFocusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN,
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    /**
     * 释放音频焦点。
     */
    private fun abandonFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(onFocusChangeListener)
        }
    }

    /**
     * 获取或创建 API 26+ 的 [AudioFocusRequest]。仅在 API 26+ 设备上调用。
     * 调用方 [requestFocus] 已有 `Build.VERSION.SDK_INT >= O` 守卫。
     */
    @Suppress("NewApi")
    private fun getOrCreateFocusRequest(): AudioFocusRequest {
        return audioFocusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(BGM_AUDIO_ATTRIBUTES)
            .setOnAudioFocusChangeListener(::onAudioFocusChange)
            .build()
            .also { audioFocusRequest = it }
    }

    companion object {
        /**
         * BGM 统一音频属性：USAGE_GAME + CONTENT_TYPE_MUSIC。
         * 通过 [MediaPlayer.create] 重载在 `prepare()` 之前传入，确保属性生效。
         */
        private val BGM_AUDIO_ATTRIBUTES = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
    }
}
