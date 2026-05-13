@file:Suppress("TooManyFunctions")

package com.example.chudadi.network.bluetooth.transport

import android.bluetooth.BluetoothAdapter
import com.example.chudadi.network.room.BluetoothDiscoveredDevice
import com.example.chudadi.network.room.RoomFrameCodec
import com.example.chudadi.network.room.RoomSocketConnection
import com.example.chudadi.network.room.RoomSocketEvent
import com.example.chudadi.network.room.RoomSocketManager
import com.example.chudadi.network.room.RoomWireMessage
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * Classic bluetooth RFCOMM transport coordinator backed by socket, connection, and heartbeat components.
 *
 * This class translates RoomTransport calls to lower-level bluetooth components; it does not apply
 * membership, seating, game, reconnect, or UI rules. Keep this class as the UML implementation node
 * for RoomTransport, with RoomSocketManager, HostConnectionRegistry, ClientConnectionHolder, and
 * HeartbeatMonitor as its collaborators.
 */
class ClassicBluetoothTransport private constructor(
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

    override fun startHost(config: HostTransportConfig): Result<Unit> {
        return socketManager.startServerSynchronously(
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
            hostConnectionRegistry.find(participantId)?.sendSafely(RoomWireMessage.HeartbeatPing)
        }
    }

    override suspend fun connectToHost(
        device: BluetoothDiscoveredDevice,
        roomUuid: UUID,
    ): Result<RoomSocketConnection> {
        return socketManager.connectToHost(device = device, roomUuid = roomUuid)
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

    override fun attachClientReadLoop(connection: RoomSocketConnection) {
        socketManager.attachClientReadLoop(connection)
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

    override fun sendToHost(message: RoomWireMessage) {
        clientConnectionHolder.current()?.sendSafely(message)
    }

    override fun sendToParticipant(
        participantId: String,
        message: RoomWireMessage,
    ) {
        hostConnectionRegistry.find(participantId)?.sendSafely(message)
    }

    /**
     * Sends one room message to every currently registered host-side participant connection.
     */
    override fun broadcast(message: RoomWireMessage) {
        hostConnectionRegistry.broadcastTargets().forEach { connection ->
            connection.sendSafely(message)
        }
    }

    override fun disconnectParticipant(participantId: String) {
        socketManager.disconnectParticipant(participantId)
    }

    override fun shutdown() {
        socketManager.shutdown()
        _events.tryEmit(RoomTransportEvent.Closed)
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
}

private class TransportComponents(
    scope: CoroutineScope,
) {
    val hostConnectionRegistry = HostConnectionRegistry()
    val clientConnectionHolder = ClientConnectionHolder()
    val heartbeatMonitor = HeartbeatMonitor(scope)
}
