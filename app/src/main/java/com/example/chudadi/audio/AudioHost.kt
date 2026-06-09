package com.example.chudadi.audio

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.LifecycleStartEffect
import com.example.chudadi.model.game.snapshot.MatchUiState
import com.example.chudadi.navigation.AppFlowRoute
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.scan

/**
 * 音频系统 Compose 入口。
 *
 * 职责：
 * - 接收 [MatchUiState] 的 [StateFlow] 和 [AppFlowRoute]，通过 [AudioEventDetector] 检测事件
 * - 直接 collect StateFlow + scan 做 diff（无竞态）
 * - 基于 [currentRoute] 切换 BGM
 * - 监听 Activity 生命周期（前后台暂停/恢复 BGM）
 */
@Composable
fun AudioHost(
    uiStateFlow: StateFlow<MatchUiState>,
    currentRoute: AppFlowRoute,
    audioService: GameAudioService,
) {
    val detector = remember { AudioEventDetector() }

    // BGM 切换：HOME/SETTINGS/BLUETOOTH_SEARCH/ROOM 播放菜单音乐，
    // GAME 播放游戏音乐，RESULT 停止
    LaunchedEffect(currentRoute) {
        when (currentRoute) {
            AppFlowRoute.HOME,
            AppFlowRoute.SETTINGS,
            AppFlowRoute.BLUETOOTH_SEARCH,
            AppFlowRoute.ROOM,
            -> audioService.playBgm(AudioEvent.Bgm.Menu)

            AppFlowRoute.GAME,
            -> audioService.playBgm(AudioEvent.Bgm.Game)

            AppFlowRoute.RESULT,
            -> audioService.stopBgm()
        }
    }

    // SFX 检测：直接 collect StateFlow + scan，保证快速连续状态变化不丢事件
    LaunchedEffect(detector, audioService) {
        uiStateFlow
            .scan(MatchUiState() to MatchUiState()) { (_, old), new ->
                old to new
            }
            .drop(1)
            .collect { (old, new) ->
                val events = detector.detect(old, new)
                events.forEach { event ->
                    when (event) {
                        is AudioEvent.Sfx -> audioService.playSfx(event)
                        is AudioEvent.Bgm -> audioService.playBgm(event)
                    }
                }
            }
    }

    // Activity 生命周期：前后台暂停/恢复 BGM
    LifecycleStartEffect(Unit) {
        audioService.resumeAll()
        onStopOrDispose { audioService.pauseAll() }
    }

    // SFX 预加载：在 AudioService 可用时加载所有音效到 SoundPool
    LaunchedEffect(audioService) {
        audioService.preloadSfx()
    }
}
