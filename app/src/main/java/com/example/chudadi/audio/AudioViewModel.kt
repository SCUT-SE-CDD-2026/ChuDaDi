package com.example.chudadi.audio

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.chudadi.data.repository.PlayerPreferencesRepository

/**
 * 持有 [GameAudioService] 实例的 ViewModel。
 * 配置变更（屏幕旋转）时存活，不重建音频服务。
 */
class AudioViewModel(
    application: Application,
    prefsRepository: PlayerPreferencesRepository,
) : AndroidViewModel(application) {

    val audioService: GameAudioService = GameAudioServiceImpl(
        context = application,
        prefsRepository = prefsRepository,
    )

    override fun onCleared() {
        super.onCleared()
        audioService.release()
    }

    companion object {
        fun factory(prefsRepository: PlayerPreferencesRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                    val app = extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application
                    return AudioViewModel(app, prefsRepository) as T
                }
            }
    }
}
