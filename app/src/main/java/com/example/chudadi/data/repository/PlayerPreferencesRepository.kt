package com.example.chudadi.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "player_preferences")

/**
 * 玩家偏好设置仓库
 * 管理本地玩家的持久化设置，包括：
 * - 显示名称
 * - 头像资源 ID（预留扩展）
 */
class PlayerPreferencesRepository(private val context: Context) {

    val playerName: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_PLAYER_NAME] ?: DEFAULT_PLAYER_NAME
    }

    val avatarResId: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[KEY_AVATAR_RES_ID] ?: DEFAULT_AVATAR_RES_ID
    }

    val nightMode: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_NIGHT_MODE] ?: DEFAULT_NIGHT_MODE
    }

    suspend fun updatePlayerName(name: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_PLAYER_NAME] = name.take(MAX_NAME_LENGTH).trim()
        }
    }

    suspend fun updateAvatarResId(resId: Int) {
        context.dataStore.edit { preferences ->
            preferences[KEY_AVATAR_RES_ID] = resId
        }
    }

    suspend fun updateNightMode(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_NIGHT_MODE] = enabled
        }
    }

    companion object {
        private val KEY_PLAYER_NAME = stringPreferencesKey("player_name")
        private val KEY_AVATAR_RES_ID = intPreferencesKey("avatar_res_id")
        private val KEY_NIGHT_MODE = booleanPreferencesKey("night_mode")

        private const val DEFAULT_PLAYER_NAME = "默认玩家"
        private const val DEFAULT_AVATAR_RES_ID = 0 // 0 表示使用默认头像
        private const val DEFAULT_NIGHT_MODE = false
        const val MAX_NAME_LENGTH = 8
    }
}
