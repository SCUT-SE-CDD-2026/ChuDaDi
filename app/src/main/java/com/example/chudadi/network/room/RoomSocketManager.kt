@file:Suppress(
    "TooManyFunctions",
    "LoopWithTooManyJumpStatements",
)

package com.example.chudadi.network.room

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class RoomConnection(
    val participantId: String,
    val connection: RoomSocketConnection,
    var lastHeartbeatAt: Long = System.currentTimeMillis(),
)

sealed interface RoomSocketEvent {
    data class IncomingConnection(val connection: RoomSocketConnection) : RoomSocketEvent

    data class HostMessage(
        val participantId: String,
        val message: RoomWireMessage,
    ) : RoomSocketEvent

    data class ClientMessage(val message: RoomWireMessage) : RoomSocketEvent

    data class ParticipantDisconnected(val participantId: String) : RoomSocketEvent

    data class HostConnectionLost(val reason: String) : RoomSocketEvent
}

class RoomSocketManager(
    private val bluetoothAdapter: BluetoothAdapter?,
    private val frameCodec: RoomFrameCodec,
    private val scope: CoroutineScope,
) {
    private val participantConnections = linkedMapOf<String, RoomConnection>()
    private val _events = MutableSharedFlow<RoomSocketEvent>(extraBufferCapacity = 32)

    private var serverSocket: BluetoothServerSocket? = null
    private var acceptJob: Job? = null
    private var heartbeatJob: Job? = null
    private var clientHeartbeatJob: Job? = null
    private var clientConnection: RoomSocketConnection? = null
    private var lastHostHeartbeatAt: Long = 0L

    val events: SharedFlow<RoomSocketEvent> = _events.asSharedFlow()

    @SuppressLint("MissingPermission")
    fun startServerSynchronously(
        serviceName: String,
        roomUuid: UUID,
    ): Result<Unit> {
        val adapter = bluetoothAdapter ?: return Result.failure(IllegalStateException("当前设备不支持蓝牙"))
        acceptJob?.cancel()
        heartbeatJob?.cancel()
        return try {
            val socket = adapter.listenUsingRfcommWithServiceRecord(serviceName, roomUuid)
            serverSocket = socket
            acceptJob = launchAcceptLoop(socket)
            Result.success(Unit)
        } catch (error: IOException) {
            shutdown()
            Result.failure(error)
        }
    }

    private fun launchAcceptLoop(socket: BluetoothServerSocket): Job {
        return scope.launch {
            while (isActive) {
                try {
                    val clientSocket = socket.accept() ?: continue
                    _events.emit(RoomSocketEvent.IncomingConnection(RoomSocketConnection(clientSocket, frameCodec)))
                } catch (_: IOException) {
                    break
                }
            }
        }
    }

    fun launchHeartbeatLoop(
        heartbeatIntervalMs: Long,
        heartbeatTimeoutMs: Long,
    ): Job {
        heartbeatJob?.cancel()
        return scope.launch {
            while (isActive) {
                delay(heartbeatIntervalMs)
                val expired = participantConnections.values.filter {
                    System.currentTimeMillis() - it.lastHeartbeatAt > heartbeatTimeoutMs
                }
                expired.forEach { connection ->
                    participantConnections.remove(connection.participantId)?.connection?.close()
                    _events.tryEmit(RoomSocketEvent.ParticipantDisconnected(connection.participantId))
                }
                participantConnections.values.forEach { connection ->
                    connection.connection.sendSafely(RoomWireMessage.HeartbeatPing)
                }
            }
        }.also { heartbeatJob = it }
    }

    @SuppressLint("MissingPermission")
    suspend fun connectToHost(
        device: BluetoothDiscoveredDevice,
        roomUuid: UUID,
    ): Result<RoomSocketConnection> {
        val adapter = bluetoothAdapter ?: return Result.failure(IllegalStateException("设备不支持蓝牙"))
        return withContext(Dispatchers.IO) {
            runCatching {
                val remoteDevice = adapter.getRemoteDevice(device.address)
                val socket = remoteDevice.createRfcommSocketToServiceRecord(roomUuid)
                socket.connect()
                RoomSocketConnection(socket = socket, frameCodec = frameCodec).also {
                    clientConnection = it
                    lastHostHeartbeatAt = System.currentTimeMillis()
                }
            }
        }
    }

    fun attachHostReadLoop(participantId: String, connection: RoomSocketConnection) {
        participantConnections[participantId] = RoomConnection(
            participantId = participantId,
            connection = connection,
        )
        scope.launch {
            try {
                while (isActive) {
                    when (val message = connection.read()) {
                        RoomWireMessage.HeartbeatPong -> {
                            participantConnections[participantId]?.lastHeartbeatAt = System.currentTimeMillis()
                        }

                        else -> _events.emit(RoomSocketEvent.HostMessage(participantId, message))
                    }
                }
            } catch (_: IOException) {
                participantConnections.remove(participantId)?.connection?.close()
                _events.tryEmit(RoomSocketEvent.ParticipantDisconnected(participantId))
            }
        }
    }

    fun attachClientReadLoop(connection: RoomSocketConnection) {
        clientConnection = connection
        scope.launch {
            try {
                while (isActive) {
                    when (val message = connection.read()) {
                        RoomWireMessage.HeartbeatPing -> {
                            lastHostHeartbeatAt = System.currentTimeMillis()
                            connection.sendSafely(RoomWireMessage.HeartbeatPong)
                        }

                        else -> _events.emit(RoomSocketEvent.ClientMessage(message))
                    }
                }
            } catch (_: IOException) {
                _events.tryEmit(RoomSocketEvent.HostConnectionLost("与房主连接中断，房间已关闭"))
            }
        }
    }

    fun startClientHeartbeatWatchdog(
        heartbeatTimeoutMs: Long,
        clientHeartbeatCheckIntervalMs: Long,
    ) {
        clientHeartbeatJob?.cancel()
        clientHeartbeatJob = scope.launch {
            while (isActive) {
                delay(clientHeartbeatCheckIntervalMs)
                if (clientConnection == null) {
                    break
                }
                if (lastHostHeartbeatAt == 0L) {
                    continue
                }
                if (System.currentTimeMillis() - lastHostHeartbeatAt > heartbeatTimeoutMs) {
                    _events.tryEmit(RoomSocketEvent.HostConnectionLost("房主连接已断开，房间已关闭"))
                    break
                }
            }
        }
    }

    fun replaceHostConnection(participantId: String, connection: RoomSocketConnection) {
        participantConnections.remove(participantId)?.connection?.close()
        attachHostReadLoop(participantId = participantId, connection = connection)
    }

    fun sendToParticipant(participantId: String, message: RoomWireMessage) {
        participantConnections[participantId]?.connection?.sendSafely(message)
    }

    fun sendToHost(message: RoomWireMessage) {
        clientConnection?.sendSafely(message)
    }

    fun broadcast(message: RoomWireMessage) {
        participantConnections.values.forEach { connection ->
            connection.connection.sendSafely(message)
        }
    }

    fun disconnectParticipant(participantId: String) {
        participantConnections.remove(participantId)?.connection?.close()
    }

    fun participantConnection(participantId: String): RoomSocketConnection? {
        return participantConnections[participantId]?.connection
    }

    fun closeClientConnection() {
        clientConnection?.close()
        clientConnection = null
    }

    fun shutdown() {
        acceptJob?.cancel()
        heartbeatJob?.cancel()
        clientHeartbeatJob?.cancel()
        serverSocket?.closeSafely()
        serverSocket = null
        participantConnections.values.forEach { it.connection.close() }
        participantConnections.clear()
        closeClientConnection()
        lastHostHeartbeatAt = 0L
    }
}

class RoomSocketConnection(
    socket: BluetoothSocket,
    private val frameCodec: RoomFrameCodec,
) {
    private val input = DataInputStream(socket.inputStream)
    private val output = DataOutputStream(socket.outputStream)
    private val bluetoothSocket = socket
    val remoteAddress: String = socket.remoteDevice?.address.orEmpty()

    suspend fun awaitJoinAccepted(
        playerName: String,
        avatarResId: Int?,
        resumeParticipantId: String? = null,
    ): RoomWireMessage.JoinRoomAccepted {
        send(
            RoomWireMessage.JoinRoomRequest(
                playerName = playerName,
                avatarResId = avatarResId,
                resumeParticipantId = resumeParticipantId,
            ),
        )
        return when (val response = read()) {
            is RoomWireMessage.JoinRoomAccepted -> response
            is RoomWireMessage.JoinRoomRejected -> throw IOException(response.reason)
            else -> throw IOException("Unexpected join response")
        }
    }

    suspend fun send(message: RoomWireMessage) {
        withContext(Dispatchers.IO) {
            frameCodec.writeMessage(output, message)
        }
    }

    fun sendSafely(message: RoomWireMessage) {
        try {
            frameCodec.writeMessage(output, message)
        } catch (_: IOException) {
            close()
        }
    }

    suspend fun read(): RoomWireMessage {
        return withContext(Dispatchers.IO) {
            frameCodec.readMessage(input)
        }
    }

    fun close() {
        try {
            bluetoothSocket.close()
        } catch (_: IOException) {
            // ignore close failure
        }
    }
}

private fun BluetoothServerSocket.closeSafely() {
    try {
        close()
    } catch (_: IOException) {
        // ignore close failure
    }
}
