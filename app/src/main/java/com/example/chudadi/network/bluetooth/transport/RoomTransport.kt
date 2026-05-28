@file:Suppress("TooManyFunctions")

package com.example.chudadi.network.bluetooth.transport

import com.example.chudadi.network.room.BluetoothDiscoveredDevice
import com.example.chudadi.network.room.RoomClientConnection
import com.example.chudadi.network.room.RoomSocketConnection
import com.example.chudadi.network.room.RoomWireMessage
import kotlinx.coroutines.flow.Flow
import java.io.IOException
import java.util.UUID

data class BroadcastResult(
    val failedTargets: List<String> = emptyList(),
) {
    val isSuccess: Boolean get() = failedTargets.isEmpty()
}

class BroadcastSendException(
    val failedTargets: List<String>,
    cause: Throwable?,
) : IOException("Bluetooth broadcast failed for targets: ${failedTargets.joinToString()}", cause)

/**
 * Defines the bluetooth room transport boundary used by room orchestration code.
 *
 * Some methods still expose RoomSocketConnection and read-loop timing because the current join handshake
 * is owned by room membership code. Keep those compatibility entry points narrow until that ownership is
 * moved in a later transport/session cleanup.
 */
interface RoomTransport {
    val events: Flow<RoomTransportEvent>

    suspend fun startHost(config: HostTransportConfig): Result<Unit>

    fun launchHostHeartbeat(
        heartbeatIntervalMs: Long,
        heartbeatTimeoutMs: Long,
    )

    suspend fun connectToHost(
        device: BluetoothDiscoveredDevice,
        roomUuid: UUID,
    ): Result<RoomClientConnection>

    /**
     * Compatibility entry point: membership code still decides when a host-side connection is accepted.
     */
    fun attachHostReadLoop(
        participantId: String,
        connection: RoomSocketConnection,
    )

    /**
     * Compatibility entry point: repository code still completes the join handshake before client reads start.
     */
    fun attachClientReadLoop(connection: RoomClientConnection)

    /**
     * Compatibility entry point for the current client heartbeat watchdog timing.
     */
    fun startClientHeartbeatWatchdog(
        heartbeatTimeoutMs: Long,
        clientHeartbeatCheckIntervalMs: Long,
    )

    /**
     * Compatibility entry point used by reconnect handling to replace a participant socket after validation.
     */
    fun replaceHostConnection(
        participantId: String,
        connection: RoomSocketConnection,
    )

    fun sendToHost(message: RoomWireMessage): Result<Unit>

    fun sendToParticipant(
        participantId: String,
        message: RoomWireMessage,
    ): Result<Unit>

    fun broadcast(message: RoomWireMessage): Result<BroadcastResult>

    fun disconnectParticipant(participantId: String)

    fun shutdown()

    fun closeNow()

    fun clearClientConnection()
}
