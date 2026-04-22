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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

data class RoomConnection(
    val participantId: String,
    val connection: RoomSocketConnection,
    val readJob: Job? = null,
    val lastHeartbeatAt: Long = System.currentTimeMillis(),
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

private data class SocketManagerState(
    val participantConnections: Map<String, RoomConnection> = emptyMap(),
    val serverSocket: BluetoothServerSocket? = null,
    val acceptJob: Job? = null,
    val heartbeatJob: Job? = null,
    val clientHeartbeatJob: Job? = null,
    val clientConnection: RoomSocketConnection? = null,
    val clientReadJob: Job? = null,
    val lastHostHeartbeatAt: Long = 0L,
)

private sealed interface SocketCommand {
    data class StartServer(
        val serviceName: String,
        val roomUuid: UUID,
        val result: CompletableDeferred<Result<Unit>>,
    ) : SocketCommand

    data class ServerAccepted(
        val socket: BluetoothSocket,
    ) : SocketCommand

    data class LaunchHeartbeatLoop(
        val heartbeatIntervalMs: Long,
        val heartbeatTimeoutMs: Long,
        val result: CompletableDeferred<Job>,
    ) : SocketCommand

    data class HeartbeatTick(
        val heartbeatTimeoutMs: Long,
    ) : SocketCommand

    data class ConnectToHostSucceeded(
        val connection: RoomSocketConnection,
        val result: CompletableDeferred<Result<RoomSocketConnection>>,
    ) : SocketCommand

    data class ConnectToHostFailed(
        val error: Throwable,
        val result: CompletableDeferred<Result<RoomSocketConnection>>,
    ) : SocketCommand

    data class AttachHostConnection(
        val participantId: String,
        val connection: RoomSocketConnection,
    ) : SocketCommand

    data class HostHeartbeatReceived(
        val participantId: String,
    ) : SocketCommand

    data class HostMessageReceived(
        val participantId: String,
        val message: RoomWireMessage,
    ) : SocketCommand

    data class HostReadFailed(
        val participantId: String,
    ) : SocketCommand

    data class AttachClientConnection(
        val connection: RoomSocketConnection,
    ) : SocketCommand

    data object ClientHeartbeatReceived : SocketCommand

    data class ClientMessageReceived(
        val message: RoomWireMessage,
    ) : SocketCommand

    data class ClientReadFailed(
        val reason: String,
    ) : SocketCommand

    data class StartClientHeartbeatWatchdog(
        val heartbeatTimeoutMs: Long,
        val checkIntervalMs: Long,
    ) : SocketCommand

    data class ClientHeartbeatCheck(
        val heartbeatTimeoutMs: Long,
    ) : SocketCommand

    data class ReplaceHostConnection(
        val participantId: String,
        val connection: RoomSocketConnection,
    ) : SocketCommand

    data class SendToParticipant(
        val participantId: String,
        val message: RoomWireMessage,
    ) : SocketCommand

    data class SendToHost(
        val message: RoomWireMessage,
    ) : SocketCommand

    data class Broadcast(
        val message: RoomWireMessage,
    ) : SocketCommand

    data class DisconnectParticipant(
        val participantId: String,
    ) : SocketCommand

    data object CloseClientConnection : SocketCommand

    data object Shutdown : SocketCommand
}

class RoomSocketManager(
    private val bluetoothAdapter: BluetoothAdapter?,
    private val frameCodec: RoomFrameCodec,
    private val scope: CoroutineScope,
) {
    private val _events = MutableSharedFlow<RoomSocketEvent>(extraBufferCapacity = 32)
    private val commands = Channel<SocketCommand>(capacity = Channel.UNLIMITED)

    init {
        scope.launch { runActorLoop() }
    }

    val events: SharedFlow<RoomSocketEvent> = _events.asSharedFlow()

    @SuppressLint("MissingPermission")
    fun startServerSynchronously(
        serviceName: String,
        roomUuid: UUID,
    ): Result<Unit> {
        val result = CompletableDeferred<Result<Unit>>()
        return runBlocking {
            commands.send(
                SocketCommand.StartServer(
                    serviceName = serviceName,
                    roomUuid = roomUuid,
                    result = result,
                ),
            )
            result.await()
        }
    }

    fun launchHeartbeatLoop(
        heartbeatIntervalMs: Long,
        heartbeatTimeoutMs: Long,
    ): Job {
        val result = CompletableDeferred<Job>()
        return runBlocking {
            commands.send(
                SocketCommand.LaunchHeartbeatLoop(
                    heartbeatIntervalMs = heartbeatIntervalMs,
                    heartbeatTimeoutMs = heartbeatTimeoutMs,
                    result = result,
                ),
            )
            result.await()
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun connectToHost(
        device: BluetoothDiscoveredDevice,
        roomUuid: UUID,
    ): Result<RoomSocketConnection> {
        val adapter = bluetoothAdapter ?: return Result.failure(
            IllegalStateException("Bluetooth not supported"),
        )
        val result = CompletableDeferred<Result<RoomSocketConnection>>()
        scope.launch(Dispatchers.IO) {
            runCatching {
                val remoteDevice = adapter.getRemoteDevice(device.address)
                val socket = remoteDevice.createRfcommSocketToServiceRecord(roomUuid)
                socket.connect()
                RoomSocketConnection(socket = socket, frameCodec = frameCodec)
            }.onSuccess { connection ->
                commands.send(SocketCommand.ConnectToHostSucceeded(connection = connection, result = result))
            }.onFailure { error ->
                commands.send(SocketCommand.ConnectToHostFailed(error = error, result = result))
            }
        }
        return result.await()
    }

    fun attachHostReadLoop(participantId: String, connection: RoomSocketConnection) {
        commands.trySend(
            SocketCommand.AttachHostConnection(
                participantId = participantId,
                connection = connection,
            ),
        )
    }

    fun attachClientReadLoop(connection: RoomSocketConnection) {
        commands.trySend(SocketCommand.AttachClientConnection(connection))
    }

    fun startClientHeartbeatWatchdog(
        heartbeatTimeoutMs: Long,
        clientHeartbeatCheckIntervalMs: Long,
    ) {
        commands.trySend(
            SocketCommand.StartClientHeartbeatWatchdog(
                heartbeatTimeoutMs = heartbeatTimeoutMs,
                checkIntervalMs = clientHeartbeatCheckIntervalMs,
            ),
        )
    }

    fun replaceHostConnection(participantId: String, connection: RoomSocketConnection) {
        commands.trySend(
            SocketCommand.ReplaceHostConnection(
                participantId = participantId,
                connection = connection,
            ),
        )
    }

    fun sendToParticipant(participantId: String, message: RoomWireMessage) {
        commands.trySend(
            SocketCommand.SendToParticipant(
                participantId = participantId,
                message = message,
            ),
        )
    }

    fun sendToHost(message: RoomWireMessage) {
        commands.trySend(SocketCommand.SendToHost(message))
    }

    fun broadcast(message: RoomWireMessage) {
        commands.trySend(SocketCommand.Broadcast(message))
    }

    fun disconnectParticipant(participantId: String) {
        commands.trySend(SocketCommand.DisconnectParticipant(participantId))
    }

    fun closeClientConnection() {
        commands.trySend(SocketCommand.CloseClientConnection)
    }

    fun shutdown() {
        commands.trySend(SocketCommand.Shutdown)
    }

    private suspend fun runActorLoop() {
        var state = SocketManagerState()
        for (command in commands) {
            state = dispatchCommand(state, command)
        }
    }

    private suspend fun dispatchCommand(
        state: SocketManagerState,
        command: SocketCommand,
    ): SocketManagerState {
        return when (command) {
            is SocketCommand.StartServer,
            is SocketCommand.ServerAccepted,
            is SocketCommand.LaunchHeartbeatLoop,
            is SocketCommand.HeartbeatTick,
            is SocketCommand.ConnectToHostSucceeded,
            is SocketCommand.ConnectToHostFailed,
            -> dispatchServerCommand(state, command)

            is SocketCommand.AttachHostConnection,
            is SocketCommand.HostHeartbeatReceived,
            is SocketCommand.HostMessageReceived,
            is SocketCommand.HostReadFailed,
            is SocketCommand.ReplaceHostConnection,
            is SocketCommand.SendToParticipant,
            is SocketCommand.Broadcast,
            is SocketCommand.DisconnectParticipant,
            -> dispatchParticipantCommand(state, command)

            is SocketCommand.AttachClientConnection,
            SocketCommand.ClientHeartbeatReceived,
            is SocketCommand.ClientMessageReceived,
            is SocketCommand.ClientReadFailed,
            is SocketCommand.StartClientHeartbeatWatchdog,
            is SocketCommand.ClientHeartbeatCheck,
            is SocketCommand.SendToHost,
            SocketCommand.CloseClientConnection,
            SocketCommand.Shutdown,
            -> dispatchClientCommand(state, command)
        }
    }

    private suspend fun dispatchServerCommand(
        state: SocketManagerState,
        command: SocketCommand,
    ): SocketManagerState {
        return when (command) {
            is SocketCommand.StartServer -> handleStartServer(state, command)
            is SocketCommand.ServerAccepted -> handleServerAccepted(state, command)
            is SocketCommand.LaunchHeartbeatLoop -> handleLaunchHeartbeatLoop(state, command)
            is SocketCommand.HeartbeatTick -> handleHeartbeatTick(state, command)
            is SocketCommand.ConnectToHostSucceeded -> handleConnectToHostSucceeded(state, command)
            is SocketCommand.ConnectToHostFailed -> handleConnectToHostFailed(state, command)
            else -> state
        }
    }

    private suspend fun dispatchParticipantCommand(
        state: SocketManagerState,
        command: SocketCommand,
    ): SocketManagerState {
        return when (command) {
            is SocketCommand.AttachHostConnection -> handleAttachHostConnection(state, command)
            is SocketCommand.HostHeartbeatReceived -> handleHostHeartbeatReceived(state, command)
            is SocketCommand.HostMessageReceived -> handleHostMessageReceived(state, command)
            is SocketCommand.HostReadFailed -> handleHostReadFailed(state, command)
            is SocketCommand.ReplaceHostConnection -> handleReplaceHostConnection(state, command)
            is SocketCommand.SendToParticipant -> handleSendToParticipant(state, command)
            is SocketCommand.Broadcast -> handleBroadcast(state, command)
            is SocketCommand.DisconnectParticipant -> handleDisconnectParticipant(state, command)
            else -> state
        }
    }

    private suspend fun dispatchClientCommand(
        state: SocketManagerState,
        command: SocketCommand,
    ): SocketManagerState {
        return when (command) {
            is SocketCommand.AttachClientConnection -> handleAttachClientConnection(state, command)
            SocketCommand.ClientHeartbeatReceived -> handleClientHeartbeatReceived(state)
            is SocketCommand.ClientMessageReceived -> handleClientMessageReceived(state, command)
            is SocketCommand.ClientReadFailed -> handleClientReadFailed(state, command)
            is SocketCommand.StartClientHeartbeatWatchdog -> handleStartClientHeartbeatWatchdog(state, command)
            is SocketCommand.ClientHeartbeatCheck -> handleClientHeartbeatCheck(state, command)
            is SocketCommand.SendToHost -> handleSendToHost(state, command)
            SocketCommand.CloseClientConnection -> handleCloseClientConnection(state)
            SocketCommand.Shutdown -> handleShutdown(state)
            else -> state
        }
    }

    @SuppressLint("MissingPermission")
    private fun handleStartServer(
        state: SocketManagerState,
        command: SocketCommand.StartServer,
    ): SocketManagerState {
        val adapter = bluetoothAdapter
        if (adapter == null) {
            command.result.complete(Result.failure(IllegalStateException("Bluetooth not supported")))
            return state
        }

        state.acceptJob?.cancel()
        state.heartbeatJob?.cancel()
        state.serverSocket?.closeSafely()

        return try {
            val serverSocket = adapter.listenUsingRfcommWithServiceRecord(command.serviceName, command.roomUuid)
            val acceptJob = scope.launch(Dispatchers.IO) {
                while (isActive) {
                    try {
                        val clientSocket = serverSocket.accept() ?: continue
                        commands.send(SocketCommand.ServerAccepted(clientSocket))
                    } catch (_: IOException) {
                        break
                    }
                }
            }
            command.result.complete(Result.success(Unit))
            state.copy(
                serverSocket = serverSocket,
                acceptJob = acceptJob,
                heartbeatJob = null,
            )
        } catch (error: IOException) {
            command.result.complete(Result.failure(error))
            state
        }
    }

    private suspend fun handleServerAccepted(
        state: SocketManagerState,
        command: SocketCommand.ServerAccepted,
    ): SocketManagerState {
        _events.emit(RoomSocketEvent.IncomingConnection(RoomSocketConnection(command.socket, frameCodec)))
        return state
    }

    private fun handleLaunchHeartbeatLoop(
        state: SocketManagerState,
        command: SocketCommand.LaunchHeartbeatLoop,
    ): SocketManagerState {
        state.heartbeatJob?.cancel()
        val heartbeatJob = scope.launch {
            while (isActive) {
                delay(command.heartbeatIntervalMs)
                commands.send(SocketCommand.HeartbeatTick(command.heartbeatTimeoutMs))
            }
        }
        command.result.complete(heartbeatJob)
        return state.copy(heartbeatJob = heartbeatJob)
    }

    private fun handleHeartbeatTick(
        state: SocketManagerState,
        command: SocketCommand.HeartbeatTick,
    ): SocketManagerState {
        val now = System.currentTimeMillis()
        val expiredIds = state.participantConnections.values
            .filter { now - it.lastHeartbeatAt > command.heartbeatTimeoutMs }
            .map { it.participantId }
        expiredIds.forEach { participantId ->
            state.participantConnections[participantId]?.readJob?.cancel()
            state.participantConnections[participantId]?.connection?.close()
            _events.tryEmit(RoomSocketEvent.ParticipantDisconnected(participantId))
        }
        val remainingConnections = state.participantConnections - expiredIds.toSet()
        remainingConnections.values.forEach { roomConnection ->
            roomConnection.connection.sendSafely(RoomWireMessage.HeartbeatPing)
        }
        return state.copy(participantConnections = remainingConnections)
    }

    private fun handleConnectToHostSucceeded(
        state: SocketManagerState,
        command: SocketCommand.ConnectToHostSucceeded,
    ): SocketManagerState {
        command.result.complete(Result.success(command.connection))
        return state.copy(
            clientConnection = command.connection,
            lastHostHeartbeatAt = System.currentTimeMillis(),
        )
    }

    private fun handleConnectToHostFailed(
        state: SocketManagerState,
        command: SocketCommand.ConnectToHostFailed,
    ): SocketManagerState {
        command.result.complete(Result.failure(command.error))
        return state
    }

    private fun handleAttachHostConnection(
        state: SocketManagerState,
        command: SocketCommand.AttachHostConnection,
    ): SocketManagerState {
        state.participantConnections[command.participantId]?.let { oldConnection ->
            oldConnection.readJob?.cancel()
            oldConnection.connection.close()
        }
        val readJob = scope.launch {
            try {
                while (isActive) {
                    when (val message = command.connection.read()) {
                        RoomWireMessage.HeartbeatPong -> {
                            commands.send(SocketCommand.HostHeartbeatReceived(command.participantId))
                        }

                        else -> {
                            commands.send(
                                SocketCommand.HostMessageReceived(
                                    participantId = command.participantId,
                                    message = message,
                                ),
                            )
                        }
                    }
                }
            } catch (_: IOException) {
                commands.send(SocketCommand.HostReadFailed(command.participantId))
            }
        }
        return state.copy(
            participantConnections = state.participantConnections + (
                command.participantId to RoomConnection(
                    participantId = command.participantId,
                    connection = command.connection,
                    readJob = readJob,
                )
            ),
        )
    }

    private fun handleHostHeartbeatReceived(
        state: SocketManagerState,
        command: SocketCommand.HostHeartbeatReceived,
    ): SocketManagerState {
        val roomConnection = state.participantConnections[command.participantId] ?: return state
        return state.copy(
            participantConnections = state.participantConnections + (
                command.participantId to roomConnection.copy(lastHeartbeatAt = System.currentTimeMillis())
            ),
        )
    }

    private suspend fun handleHostMessageReceived(
        state: SocketManagerState,
        command: SocketCommand.HostMessageReceived,
    ): SocketManagerState {
        _events.emit(
            RoomSocketEvent.HostMessage(
                participantId = command.participantId,
                message = command.message,
            ),
        )
        return state
    }

    private fun handleHostReadFailed(
        state: SocketManagerState,
        command: SocketCommand.HostReadFailed,
    ): SocketManagerState {
        val roomConnection = state.participantConnections[command.participantId] ?: return state
        roomConnection.readJob?.cancel()
        roomConnection.connection.close()
        _events.tryEmit(RoomSocketEvent.ParticipantDisconnected(command.participantId))
        return state.copy(
            participantConnections = state.participantConnections - command.participantId,
        )
    }

    private fun handleAttachClientConnection(
        state: SocketManagerState,
        command: SocketCommand.AttachClientConnection,
    ): SocketManagerState {
        state.clientReadJob?.cancel()
        if (state.clientConnection !== command.connection) {
            state.clientConnection?.close()
        }
        val readJob = scope.launch {
            try {
                while (isActive) {
                    when (val message = command.connection.read()) {
                        RoomWireMessage.HeartbeatPing -> {
                            command.connection.sendSafely(RoomWireMessage.HeartbeatPong)
                            commands.send(SocketCommand.ClientHeartbeatReceived)
                        }

                        else -> commands.send(SocketCommand.ClientMessageReceived(message))
                    }
                }
            } catch (_: IOException) {
                commands.send(SocketCommand.ClientReadFailed("Lost connection to host"))
            }
        }
        return state.copy(
            clientConnection = command.connection,
            clientReadJob = readJob,
            lastHostHeartbeatAt = System.currentTimeMillis(),
        )
    }

    private fun handleClientHeartbeatReceived(state: SocketManagerState): SocketManagerState {
        return state.copy(lastHostHeartbeatAt = System.currentTimeMillis())
    }

    private suspend fun handleClientMessageReceived(
        state: SocketManagerState,
        command: SocketCommand.ClientMessageReceived,
    ): SocketManagerState {
        _events.emit(RoomSocketEvent.ClientMessage(command.message))
        return state
    }

    private fun handleClientReadFailed(
        state: SocketManagerState,
        command: SocketCommand.ClientReadFailed,
    ): SocketManagerState {
        _events.tryEmit(RoomSocketEvent.HostConnectionLost(command.reason))
        state.clientReadJob?.cancel()
        state.clientConnection?.close()
        state.clientHeartbeatJob?.cancel()
        return state.copy(
            clientConnection = null,
            clientReadJob = null,
            clientHeartbeatJob = null,
            lastHostHeartbeatAt = 0L,
        )
    }

    private fun handleStartClientHeartbeatWatchdog(
        state: SocketManagerState,
        command: SocketCommand.StartClientHeartbeatWatchdog,
    ): SocketManagerState {
        state.clientHeartbeatJob?.cancel()
        val watchdogJob = scope.launch {
            while (isActive) {
                delay(command.checkIntervalMs)
                commands.send(SocketCommand.ClientHeartbeatCheck(command.heartbeatTimeoutMs))
            }
        }
        return state.copy(clientHeartbeatJob = watchdogJob)
    }

    private fun handleClientHeartbeatCheck(
        state: SocketManagerState,
        command: SocketCommand.ClientHeartbeatCheck,
    ): SocketManagerState {
        val shouldStopWatchdog = state.clientConnection == null
        val heartbeatExpired = !shouldStopWatchdog &&
            state.lastHostHeartbeatAt != 0L &&
            System.currentTimeMillis() - state.lastHostHeartbeatAt > command.heartbeatTimeoutMs

        return if (shouldStopWatchdog) {
            state.clientHeartbeatJob?.cancel()
            state.copy(clientHeartbeatJob = null)
        } else if (heartbeatExpired) {
            _events.tryEmit(RoomSocketEvent.HostConnectionLost("Host connection timed out"))
            state.clientReadJob?.cancel()
            state.clientConnection?.close()
            state.clientHeartbeatJob?.cancel()
            state.copy(
                clientConnection = null,
                clientReadJob = null,
                clientHeartbeatJob = null,
                lastHostHeartbeatAt = 0L,
            )
        } else {
            state
        }
    }

    private fun handleReplaceHostConnection(
        state: SocketManagerState,
        command: SocketCommand.ReplaceHostConnection,
    ): SocketManagerState {
        state.participantConnections[command.participantId]?.let { oldConnection ->
            oldConnection.readJob?.cancel()
            oldConnection.connection.close()
        }
        return handleAttachHostConnection(
            state = state,
            command = SocketCommand.AttachHostConnection(
                participantId = command.participantId,
                connection = command.connection,
            ),
        )
    }

    private fun handleSendToParticipant(
        state: SocketManagerState,
        command: SocketCommand.SendToParticipant,
    ): SocketManagerState {
        state.participantConnections[command.participantId]?.connection?.sendSafely(command.message)
        return state
    }

    private fun handleSendToHost(
        state: SocketManagerState,
        command: SocketCommand.SendToHost,
    ): SocketManagerState {
        state.clientConnection?.sendSafely(command.message)
        return state
    }

    private fun handleBroadcast(
        state: SocketManagerState,
        command: SocketCommand.Broadcast,
    ): SocketManagerState {
        state.participantConnections.values.forEach { roomConnection ->
            roomConnection.connection.sendSafely(command.message)
        }
        return state
    }

    private fun handleDisconnectParticipant(
        state: SocketManagerState,
        command: SocketCommand.DisconnectParticipant,
    ): SocketManagerState {
        val roomConnection = state.participantConnections[command.participantId] ?: return state
        roomConnection.readJob?.cancel()
        roomConnection.connection.close()
        return state.copy(
            participantConnections = state.participantConnections - command.participantId,
        )
    }

    private fun handleCloseClientConnection(state: SocketManagerState): SocketManagerState {
        state.clientReadJob?.cancel()
        state.clientConnection?.close()
        return state.copy(
            clientConnection = null,
            clientReadJob = null,
            lastHostHeartbeatAt = 0L,
        )
    }

    private fun handleShutdown(state: SocketManagerState): SocketManagerState {
        state.acceptJob?.cancel()
        state.heartbeatJob?.cancel()
        state.clientHeartbeatJob?.cancel()
        state.clientReadJob?.cancel()
        state.serverSocket?.closeSafely()
        state.participantConnections.values.forEach { roomConnection ->
            roomConnection.readJob?.cancel()
            roomConnection.connection.close()
        }
        state.clientConnection?.close()
        return SocketManagerState()
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
