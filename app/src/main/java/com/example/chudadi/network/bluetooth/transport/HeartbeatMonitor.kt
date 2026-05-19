@file:Suppress("TooManyFunctions")

package com.example.chudadi.network.bluetooth.transport

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Transport heartbeat signals only.
 *
 * Reconnect identity recovery, room membership changes, and UI wording are handled by callers that
 * consume these events.
 */
sealed interface HeartbeatEvent {
    data class ParticipantTimedOut(val participantId: String) : HeartbeatEvent
    data object HostTimedOut : HeartbeatEvent
}

/**
 * Owns transport heartbeat loops and heartbeat timestamps without handling reconnect or room membership rules.
 */
class HeartbeatMonitor(
    private val scope: CoroutineScope,
    private val nowProvider: () -> Long = System::currentTimeMillis,
) {
    private val _events = MutableSharedFlow<HeartbeatEvent>(extraBufferCapacity = 32)
    val events: SharedFlow<HeartbeatEvent> = _events.asSharedFlow()

    private val participantHeartbeats = linkedMapOf<String, Long>()
    private var hostHeartbeatJob: Job? = null
    private var clientHeartbeatJob: Job? = null
    private var lastHostHeartbeatAt: Long = 0L

    @Synchronized
    fun trackParticipant(participantId: String) {
        participantHeartbeats[participantId] = nowProvider()
    }

    @Synchronized
    fun removeParticipant(participantId: String) {
        participantHeartbeats.remove(participantId)
    }

    @Synchronized
    fun clearParticipants() {
        participantHeartbeats.clear()
    }

    fun startHostHeartbeat(
        heartbeatIntervalMs: Long,
        heartbeatTimeoutMs: Long,
        sendHeartbeat: suspend (String) -> Unit,
    ): Job {
        hostHeartbeatJob?.cancel()
        val heartbeatJob = scope.launch {
            while (isActive) {
                delay(heartbeatIntervalMs)
                val expiredIds = expiredParticipantIds(heartbeatTimeoutMs)
                expiredIds.forEach { participantId ->
                    removeParticipant(participantId)
                    _events.emit(HeartbeatEvent.ParticipantTimedOut(participantId))
                }
                trackedParticipantIds().forEach { participantId ->
                    sendHeartbeat(participantId)
                }
            }
        }
        hostHeartbeatJob = heartbeatJob
        return heartbeatJob
    }

    fun startClientWatchdog(
        heartbeatTimeoutMs: Long,
        checkIntervalMs: Long,
    ): Job {
        clientHeartbeatJob?.cancel()
        val watchdogJob = scope.launch {
            while (isActive) {
                delay(checkIntervalMs)
                if (isHostHeartbeatTimedOut(heartbeatTimeoutMs)) {
                    _events.emit(HeartbeatEvent.HostTimedOut)
                    clearHostHeartbeat()
                    stopClientWatchdog()
                }
            }
        }
        clientHeartbeatJob = watchdogJob
        return watchdogJob
    }

    fun stop() {
        stopHostHeartbeat()
        stopClientWatchdog()
        clearParticipants()
        clearHostHeartbeat()
    }

    fun stopHostHeartbeat() {
        hostHeartbeatJob?.cancel()
        hostHeartbeatJob = null
    }

    fun stopClientWatchdog() {
        clientHeartbeatJob?.cancel()
        clientHeartbeatJob = null
    }

    @Synchronized
    fun markHeartbeatReceived(participantId: String?) {
        if (participantId == null) {
            lastHostHeartbeatAt = nowProvider()
        } else {
            participantHeartbeats[participantId] = nowProvider()
        }
    }

    @Synchronized
    fun markHostConnected() {
        lastHostHeartbeatAt = nowProvider()
    }

    @Synchronized
    fun clearHostHeartbeat() {
        lastHostHeartbeatAt = 0L
    }

    @Synchronized
    private fun trackedParticipantIds(): List<String> = participantHeartbeats.keys.toList()

    @Synchronized
    private fun expiredParticipantIds(heartbeatTimeoutMs: Long): List<String> {
        val now = nowProvider()
        return participantHeartbeats
            .filterValues { lastHeartbeatAt -> now - lastHeartbeatAt > heartbeatTimeoutMs }
            .keys
            .toList()
    }

    @Synchronized
    private fun isHostHeartbeatTimedOut(heartbeatTimeoutMs: Long): Boolean {
        return lastHostHeartbeatAt != 0L && nowProvider() - lastHostHeartbeatAt > heartbeatTimeoutMs
    }
}
