@file:Suppress("TooManyFunctions")

package com.example.chudadi.network.bluetooth.transport

import android.bluetooth.BluetoothAdapter
import com.example.chudadi.network.room.BluetoothDiscoveredDevice
import com.example.chudadi.network.room.RoomClientConnection
import com.example.chudadi.network.room.RoomFrameCodec
import com.example.chudadi.network.room.RoomSocketConnection
import com.example.chudadi.network.room.RoomSocketEvent
import com.example.chudadi.network.room.RoomSocketManager
import com.example.chudadi.network.room.RoomWireMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.UUID

/**
 * Classic bluetooth RFCOMM transport coordinator backed by socket, connection, and heartbeat components.
 *
 * This class translates RoomTransport calls to lower-level bluetooth components; it does not apply
 * membership, seating, game, reconnect, or UI rules. Keep this class as the UML implementation node
 * for RoomTransport, with RoomSocketManager, HostConnectionRegistry, ClientConnectionHolder, and
 * HeartbeatMonitor as its collaborators.
 */
class ClassicBluetoothTransport internal constructor(
    private val socketManager: RoomSocketManager,
    private val hostConnectionRegistry: HostConnectionRegistry,
    private val clientConnectionHolder: ClientConnectionHolder,
    private val heartbeatMonitor: HeartbeatMonitor,
    scope: CoroutineScope,
) : RoomTransport {
    constructor(
        bluetoothAdapter: BluetoothAdapter?,
        frameCodec: RoomFrameCodec,
        scope: CoroutineScope,
    ) : this(
        bluetoothAdapter = bluetoothAdapter,
        frameCodec = frameCodec,
        scope = scope,
        components = TransportComponents(scope),
    )

    private constructor(
        bluetoothAdapter: BluetoothAdapter?,
        frameCodec: RoomFrameCodec,
        scope: CoroutineScope,
        components: TransportComponents,
    ) : this(
        socketManager = RoomSocketManager(
            bluetoothAdapter = bluetoothAdapter,
            frameCodec = frameCodec,
            scope = scope,
            hostConnectionRegistry = components.hostConnectionRegistry,
            clientConnectionHolder = components.clientConnectionHolder,
            heartbeatMonitor = components.heartbeatMonitor,
        ),
        hostConnectionRegistry = components.hostConnectionRegistry,
        clientConnectionHolder = components.clientConnectionHolder,
        heartbeatMonitor = components.heartbeatMonitor,
        scope = scope,
    )

    private val _events = MutableSharedFlow<RoomTransportEvent>(extraBufferCapacity = 32)
    override val events: SharedFlow<RoomTransportEvent> = _events.asSharedFlow()

    init {
        scope.launch {
            socketManager.events.collect { event ->
                _events.emit(event.toTransportEvent())
            }
        }
    }

    override suspend fun startHost(config: HostTransportConfig): Result<Unit> {
        return socketManager.startServer(
            serviceName = config.serviceName,
            roomUuid = config.serviceUuid,
        ).onSuccess {
            _events.tryEmit(RoomTransportEvent.HostStarted)
        }.onFailure { error ->
            _events.tryEmit(RoomTransportEvent.HostStartFailed(error))
        }
    }

    override fun launchHostHeartbeat(
        heartbeatIntervalMs: Long,
        heartbeatTimeoutMs: Long,
    ) {
        heartbeatMonitor.startHostHeartbeat(
            heartbeatIntervalMs = heartbeatIntervalMs,
            heartbeatTimeoutMs = heartbeatTimeoutMs,
        ) { participantId ->
            sendToParticipant(participantId, RoomWireMessage.HeartbeatPing)
        }
    }

    override suspend fun connectToHost(
        device: BluetoothDiscoveredDevice,
        roomUuid: UUID,
    ): Result<RoomClientConnection> {
        return socketManager.connectToHost(device = device, roomUuid = roomUuid)
            .map { connection -> connection }
            .onSuccess {
                _events.tryEmit(
                    RoomTransportEvent.ClientConnected(
                        deviceName = device.name,
                        deviceAddress = device.address,
                    ),
                )
            }
            .onFailure { error ->
                _events.tryEmit(RoomTransportEvent.TransportError(error))
            }
    }

    override fun attachHostReadLoop(
        participantId: String,
        connection: RoomSocketConnection,
    ) {
        socketManager.attachHostReadLoop(participantId = participantId, connection = connection)
    }

    override fun attachClientReadLoop(connection: RoomClientConnection) {
        socketManager.attachClientReadLoop(connection as RoomSocketConnection)
    }

    override fun startClientHeartbeatWatchdog(
        heartbeatTimeoutMs: Long,
        clientHeartbeatCheckIntervalMs: Long,
    ) {
        heartbeatMonitor.startClientWatchdog(
            heartbeatTimeoutMs = heartbeatTimeoutMs,
            checkIntervalMs = clientHeartbeatCheckIntervalMs,
        )
    }

    override fun replaceHostConnection(
        participantId: String,
        connection: RoomSocketConnection,
    ) {
        socketManager.replaceHostConnection(participantId = participantId, connection = connection)
    }

    override fun sendToHost(message: RoomWireMessage): Result<Unit> {
        val connection = clientConnectionHolder.current()
            ?: return missingConnectionFailure(
                participantId = null,
                message = message,
                targetLabel = "host",
            )
        return connection.sendSafely(message = message, targetId = "host")
            .onFailure { cause ->
                _events.tryEmit(
                    RoomTransportEvent.SendFailed(
                        participantId = null,
                        messageType = message.messageTypeName(),
                        cause = cause,
                    ),
                )
                clientConnectionHolder.clear()
                _events.tryEmit(RoomTransportEvent.ConnectionLost(participantId = null, cause = cause))
            }
    }

    override fun sendToParticipant(
        participantId: String,
        message: RoomWireMessage,
    ): Result<Unit> {
        val connection = hostConnectionRegistry.find(participantId)
            ?: return missingConnectionFailure(
                participantId = participantId,
                message = message,
                targetLabel = "participantId=$participantId",
            )
        return connection.sendSafely(message = message, targetId = "participantId=$participantId")
            .onFailure { cause ->
                _events.tryEmit(
                    RoomTransportEvent.SendFailed(
                        participantId = participantId,
                        messageType = message.messageTypeName(),
                        cause = cause,
                    ),
                )
                socketManager.disconnectParticipant(participantId)
                _events.tryEmit(RoomTransportEvent.ConnectionLost(participantId = participantId, cause = cause))
            }
    }

    /**
     * Sends one room message to every currently registered host-side participant connection.
     */
    override fun broadcast(message: RoomWireMessage): Result<BroadcastResult> {
        val failedTargets = mutableListOf<String>()
        var firstFailure: Throwable? = null
        hostConnectionRegistry.broadcastTargets().forEach { (participantId, connection) ->
            connection.sendSafely(message = message, targetId = "broadcast target participantId=$participantId")
                .onFailure { cause ->
                    failedTargets += participantId
                    if (firstFailure == null) {
                        firstFailure = cause
                    }
                    _events.tryEmit(
                        RoomTransportEvent.SendFailed(
                            participantId = participantId,
                            messageType = message.messageTypeName(),
                            cause = cause,
                        ),
                    )
                    socketManager.disconnectParticipant(participantId)
                    _events.tryEmit(RoomTransportEvent.ConnectionLost(participantId = participantId, cause = cause))
                }
        }
        val result = BroadcastResult(failedTargets = failedTargets)
        return if (result.isSuccess) {
            Result.success(result)
        } else {
            Result.failure(BroadcastSendException(failedTargets, firstFailure))
        }
    }

    override fun disconnectParticipant(participantId: String) {
        socketManager.disconnectParticipant(participantId)
    }

    override fun shutdown() {
        socketManager.shutdown()
        _events.tryEmit(RoomTransportEvent.Closed)
    }

    override fun closeNow() {
        socketManager.closeNow()
        _events.tryEmit(RoomTransportEvent.Closed)
    }

    override fun clearClientConnection() {
        socketManager.clearClientConnection()
    }

    private fun RoomSocketEvent.toTransportEvent(): RoomTransportEvent {
        return when (this) {
            is RoomSocketEvent.IncomingConnection -> RoomTransportEvent.IncomingConnection(connection)
            is RoomSocketEvent.HostMessage -> RoomTransportEvent.MessageReceived(
                fromParticipantId = participantId,
                payload = message,
            )

            is RoomSocketEvent.ClientMessage -> RoomTransportEvent.MessageReceived(
                fromParticipantId = null,
                payload = message,
            )

            is RoomSocketEvent.ParticipantDisconnected -> RoomTransportEvent.ConnectionLost(
                participantId = participantId,
                cause = null,
            )

            is RoomSocketEvent.HostConnectionLost -> RoomTransportEvent.ConnectionLost(
                participantId = null,
                cause = IOException(reason),
            )
        }
    }

    private fun missingConnectionFailure(
        participantId: String?,
        message: RoomWireMessage,
        targetLabel: String,
    ): Result<Unit> {
        val error = IllegalStateException("Bluetooth connection is not available: $targetLabel")
        _events.tryEmit(
            RoomTransportEvent.SendFailed(
                participantId = participantId,
                messageType = message.messageTypeName(),
                cause = error,
            ),
        )
        return Result.failure(error)
    }

    private fun RoomWireMessage.messageTypeName(): String = javaClass.simpleName
}

private class TransportComponents(
    scope: CoroutineScope,
) {
    val hostConnectionRegistry = HostConnectionRegistry()
    val clientConnectionHolder = ClientConnectionHolder()
    val heartbeatMonitor = HeartbeatMonitor(scope)
}
