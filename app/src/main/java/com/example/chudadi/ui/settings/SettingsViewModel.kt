package com.example.chudadi.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.chudadi.data.repository.PlayerPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val playerName: String = "",
    val avatarResId: Int = 0,
    val editingName: String = "",
    val isEditingName: Boolean = false,
    val nameError: String? = null,
)

class SettingsViewModel(
    private val repository: PlayerPreferencesRepository,
) : ViewModel() {

    val playerName: StateFlow<String> = repository.playerName
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = "默认玩家",
        )

    val avatarResId: StateFlow<Int> = repository.avatarResId
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0,
        )

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun onEnterEditName() {
        _uiState.update {
            it.copy(
                isEditingName = true,
                editingName = playerName.value,
                nameError = null,
            )
        }
    }

    fun onNameChanged(name: String) {
        _uiState.update {
            it.copy(
                editingName = name,
                nameError = validateName(name),
            )
        }
    }

    fun onConfirmName() {
        val name = _uiState.value.editingName
        val error = validateName(name)
        if (error != null) {
            _uiState.update { it.copy(nameError = error) }
            return
        }
        viewModelScope.launch {
            repository.updatePlayerName(name)
            _uiState.update {
                it.copy(isEditingName = false, nameError = null)
            }
        }
    }

    fun onCancelEditName() {
        _uiState.update {
            it.copy(isEditingName = false, editingName = "", nameError = null)
        }
    }

    private fun validateName(name: String): String? {
        return when {
            name.isBlank() -> "名称不能为空"
            name.length > PlayerPreferencesRepository.MAX_NAME_LENGTH ->
                "名称不能超过${PlayerPreferencesRepository.MAX_NAME_LENGTH}个字符"
            name != name.trim() -> "名称不能包含首尾空格"
            else -> null
        }
    }

    companion object {
        fun factory(repository: PlayerPreferencesRepository): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return SettingsViewModel(repository) as T
                }
            }
        }
    }
}
