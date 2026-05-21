package com.example.chudadi.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.reconnectDataStore: DataStore<Preferences> by preferencesDataStore(name = "reconnect_session")

data class ReconnectSession(
    val hostAddress: String,
    val hostDeviceName: String,
    val participantId: String,
    val roomName: String,
    val savedAtMillis: Long,
)

class ReconnectSessionRepository(private val context: Context) {

    val session: Flow<ReconnectSession?> = context.reconnectDataStore.data.map { preferences ->
        val hostAddress = preferences[KEY_HOST_ADDRESS] ?: return@map null
        val hostDeviceName = preferences[KEY_HOST_DEVICE_NAME] ?: return@map null
        val participantId = preferences[KEY_PARTICIPANT_ID] ?: return@map null
        val roomName = preferences[KEY_ROOM_NAME] ?: return@map null
        val savedAtMillis = preferences[KEY_SAVED_AT_MILLIS] ?: 0L
        ReconnectSession(
            hostAddress = hostAddress,
            hostDeviceName = hostDeviceName,
            participantId = participantId,
            roomName = roomName,
            savedAtMillis = savedAtMillis,
        )
    }

    suspend fun updateSession(session: ReconnectSession) {
        context.reconnectDataStore.edit { preferences ->
            preferences[KEY_HOST_ADDRESS] = session.hostAddress
            preferences[KEY_HOST_DEVICE_NAME] = session.hostDeviceName
            preferences[KEY_PARTICIPANT_ID] = session.participantId
            preferences[KEY_ROOM_NAME] = session.roomName
            preferences[KEY_SAVED_AT_MILLIS] = session.savedAtMillis
        }
    }

    suspend fun clearSession() {
        context.reconnectDataStore.edit { preferences ->
            preferences.remove(KEY_HOST_ADDRESS)
            preferences.remove(KEY_HOST_DEVICE_NAME)
            preferences.remove(KEY_PARTICIPANT_ID)
            preferences.remove(KEY_ROOM_NAME)
            preferences.remove(KEY_SAVED_AT_MILLIS)
        }
    }

    companion object {
        private val KEY_HOST_ADDRESS = stringPreferencesKey("host_address")
        private val KEY_HOST_DEVICE_NAME = stringPreferencesKey("host_device_name")
        private val KEY_PARTICIPANT_ID = stringPreferencesKey("participant_id")
        private val KEY_ROOM_NAME = stringPreferencesKey("room_name")
        private val KEY_SAVED_AT_MILLIS = longPreferencesKey("saved_at_millis")
    }
}
