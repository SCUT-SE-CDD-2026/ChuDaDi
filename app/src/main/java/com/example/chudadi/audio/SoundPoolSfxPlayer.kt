package com.example.chudadi.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * 基于 Android SoundPool 的音效播放器实现。
 *
 * 职责：
 * - 预加载 SFX 资源到 SoundPool
 * - 按 volume 播放指定音效
 * - 管理生命周期（释放 SoundPool）
 */
class SoundPoolSfxPlayer(private val context: Context) : SfxPlayer {

    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(MAX_STREAMS)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    /** 音效枚举 → SoundPool 内部 soundId */
    private val effectToSoundId: MutableMap<AudioEvent.Sfx, Int> = mutableMapOf()

    /** 已加载完成的 soundId 集合 */
    private val loadedSoundIds: MutableSet<Int> = mutableSetOf()

    /** 等待加载完成的 continuations，按 soundId 索引 */
    private val pendingContinuations: MutableMap<Int, (Unit) -> Unit> = mutableMapOf()

    init {
        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) {
                loadedSoundIds.add(sampleId)
                pendingContinuations.remove(sampleId)?.invoke(Unit)
            }
        }
    }

    override suspend fun preload(effects: Collection<AudioEvent.Sfx>) {
        for (effect in effects) {
            val soundId = soundPool.load(context, effect.rawResId, PRIORITY_DEFAULT)
            effectToSoundId[effect] = soundId

            // 如果已经加载完成（同步回调场景），直接跳过等待
            if (soundId in loadedSoundIds) continue

            // 等待该 soundId 加载完成
            suspendCancellableCoroutine { cont ->
                // double-check: 可能在 suspendCancellableCoroutine 构建期间已完成
                if (soundId in loadedSoundIds) {
                    if (cont.isActive) cont.resume(Unit)
                } else {
                    pendingContinuations[soundId] = { cont.resume(it) }
                    cont.invokeOnCancellation {
                        pendingContinuations.remove(soundId)
                    }
                }
            }
        }
    }

    override fun play(effect: AudioEvent.Sfx, volume: Float) {
        val soundId = effectToSoundId[effect] ?: return
        if (soundId !in loadedSoundIds) return
        soundPool.play(soundId, volume, volume, PRIORITY_DEFAULT, NO_LOOP, NORMAL_RATE)
    }

    override fun release() {
        for (soundId in effectToSoundId.values) {
            soundPool.unload(soundId)
        }
        effectToSoundId.clear()
        loadedSoundIds.clear()
        soundPool.release()
    }

    companion object {
        private const val MAX_STREAMS = 6
        private const val PRIORITY_DEFAULT = 1
        private const val NO_LOOP = 0
        private const val NORMAL_RATE = 1.0f
    }
}
