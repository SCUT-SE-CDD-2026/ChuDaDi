@file:Suppress(
    "TooManyFunctions",
    "LoopWithTooManyJumpStatements",
    "LongParameterList",
)

package com.example.chudadi.network.room

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.util.Log
import com.example.chudadi.network.bluetooth.transport.BluetoothConnection
import com.example.chudadi.network.bluetooth.transport.ClientConnectionHolder
import com.example.chudadi.network.bluetooth.transport.HeartbeatEvent
import com.example.chudadi.network.bluetooth.transport.HeartbeatMonitor
import com.example.chudadi.network.bluetooth.transport.HostConnectionRegistry
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

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
    val hostReadJobs: Map<String, ReadLoopHandle> = emptyMap(),
    val serverSocket: BluetoothServerSocket? = null,
    val acceptJob: Job? = null,
    val clientReadJob: ReadLoopHandle? = null,
)

private data class ReadLoopHandle(
    val connection: BluetoothConnection,
    val job: Job,
    val locallyClosing: AtomicBoolean,
) {
    fun closeAndCancel() {
        locallyClosing.set(true)
        job.cancel()
        connection.closeNow()
    }
}

private sealed interface SocketCommand {
    data class StartServer(
        val serviceName: String,
        val roomUuid: UUID,
        val result: CompletableDeferred<Result<Unit>>,
    ) : SocketCommand

    data class ServerAccepted(
        val socket: BluetoothSocket,
    ) : SocketCommand

    data class ParticipantHeartbeatTimedOut(
        val participantId: String,
    ) : SocketCommand

    data class ConnectToHostSucceeded(
        val connection: BluetoothConnection,
        val result: CompletableDeferred<Result<RoomSocketConnection>>,
    ) : SocketCommand

    data class ConnectToHostFailed(
        val error: Throwable,
        val result: CompletableDeferred<Result<RoomSocketConnection>>,
    ) : SocketCommand

    data class AttachHostConnection(
        val participantId: String,
        val connection: BluetoothConnection,
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
        val connection: BluetoothConnection,
    ) : SocketCommand

    data object ClientHeartbeatReceived : SocketCommand

    data class ClientMessageReceived(
        val message: RoomWireMessage,
    ) : SocketCommand

    data class ClientReadFailed(
        val reason: String,
    ) : SocketCommand

    data object HostHeartbeatTimedOut : SocketCommand

    data class ReplaceHostConnection(
        val participantId: String,
        val connection: BluetoothConnection,
    ) : SocketCommand

    data class DisconnectParticipant(
        val participantId: String,
    ) : SocketCommand

    data object Shutdown : SocketCommand
}

/**
 * Keeps low-level RFCOMM socket creation, accept loops, and read-loop lifecycle wiring.
 *
 * Connection lookup, client connection ownership, message sending, and heartbeat timing live in
 * transport components shared with ClassicBluetoothTransport.
 *
 * Compatibility debt: this manager still coordinates the actor loop that attaches read jobs to
 * HostConnectionRegistry, ClientConnectionHolder, and HeartbeatMonitor. Avoid adding membership,
 * seating, UI wording, or game-rule decisions here; those belong above the transport boundary.
 */
class RoomSocketManager(
    private val bluetoothAdapter: BluetoothAdapter?,
    private val frameCodec: RoomFrameCodec,
    private val scope: CoroutineScope,
    private val hostConnectionRegistry: HostConnectionRegistry = HostConnectionRegistry(),
    private val clientConnectionHolder: ClientConnectionHolder = ClientConnectionHolder(),
    private val heartbeatMonitor: HeartbeatMonitor = HeartbeatMonitor(scope),
    private val connectionFactory: RoomSocketConnectionFactory = DefaultRoomSocketConnectionFactory,
) {
    private val _events = MutableSharedFlow<RoomSocketEvent>(extraBufferCapacity = 32)
    private val commands = Channel<SocketCommand>(capacity = Channel.UNLIMITED)
    @Volatile
    private var currentState = SocketManagerState()
    @Volatile
    private var pendingClientSocket: BluetoothSocket? = null

    init {
        scope.launch { runActorLoop() }
        scope.launch {
            heartbeatMonitor.events.collect { event ->
                when (event) {
                    is HeartbeatEvent.ParticipantTimedOut -> {
                        commands.send(SocketCommand.ParticipantHeartbeatTimedOut(event.participantId))
                    }

                    HeartbeatEvent.HostTimedOut -> {
                        commands.send(SocketCommand.HostHeartbeatTimedOut)
                    }
                }
            }
        }
    }

    val events: SharedFlow<RoomSocketEvent> = _events.asSharedFlow()

    @SuppressLint("MissingPermission")
    @Suppress("TooGenericExceptionCaught")
    suspend fun startServer(
        serviceName: String,
        roomUuid: UUID,
    ): Result<Unit> {
        val result = CompletableDeferred<Result<Unit>>()
        return try {
            commands.send(
                SocketCommand.StartServer(
                    serviceName = serviceName,
                    roomUuid = roomUuid,
                    result = result,
                ),
            )
            withTimeout(HOST_START_TIMEOUT_MS) {
                result.await()
            }
        } catch (e: TimeoutCancellationException) {
            Result.failure(IOException(HOST_START_TIMEOUT_MESSAGE, e))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun connectToHost(
        device: BluetoothDiscoveredDevice,
        roomUuid: UUID,
    ): Result<RoomSocketConnection> {
        val adapter = bluetoothAdapter ?: return Result.failure(
            IllegalStateException("当前设备不支持蓝牙"),
        )
        val result = CompletableDeferred<Result<RoomSocketConnection>>()
        scope.launch(Dispatchers.IO) {
            var socket: BluetoothSocket? = null
            runCatching {
                val remoteDevice = adapter.getRemoteDevice(device.address)
                val createdSocket = remoteDevice.createRfcommSocketToServiceRecord(roomUuid)
                socket = createdSocket
                pendingClientSocket = createdSocket
                createdSocket.connect()
                BluetoothConnection(
                    rawConnection = connectionFactory.create(socket = createdSocket, frameCodec = frameCodec),
                    deviceName = device.name,
                    deviceAddress = device.address,
                )
            }.onSuccess { connection ->
                pendingClientSocket = null
                if (result.isActive) {
                    commands.send(SocketCommand.ConnectToHostSucceeded(connection = connection, result = result))
                } else {
                    connection.closeNow()
                }
            }.onFailure { error ->
                socket?.closeSafely()
                if (pendingClientSocket === socket) {
                    pendingClientSocket = null
                }
                if (result.isActive) {
                    commands.send(SocketCommand.ConnectToHostFailed(error = error, result = result))
                }
            }
        }
        return try {
            result.await()
        } catch (e: CancellationException) {
            result.cancel(e)
            val socket = pendingClientSocket
            socket?.closeSafely()
            if (pendingClientSocket === socket) {
                pendingClientSocket = null
            }
            throw e
        }
    }

    fun attachHostReadLoop(participantId: String, connection: RoomSocketConnection) {
        commands.trySend(
            SocketCommand.AttachHostConnection(
                participantId = participantId,
                connection = BluetoothConnection(connection),
            ),
        )
    }

    fun attachClientReadLoop(connection: RoomSocketConnection) {
        commands.trySend(SocketCommand.AttachClientConnection(BluetoothConnection(connection)))
    }

    fun replaceHostConnection(participantId: String, connection: RoomSocketConnection) {
        commands.trySend(
            SocketCommand.ReplaceHostConnection(
                participantId = participantId,
                connection = BluetoothConnection(connection),
            ),
        )
    }

    fun disconnectParticipant(participantId: String) {
        commands.trySend(SocketCommand.DisconnectParticipant(participantId))
    }

    fun shutdown() {
        commands.trySend(SocketCommand.Shutdown)
    }

    fun closeNow() {
        val state = currentState
        pendingClientSocket?.closeSafely()
        pendingClientSocket = null
        state.serverSocket?.closeSafely()
        state.acceptJob?.cancel()
        state.clientReadJob?.closeAndCancel()
        state.hostReadJobs.values.forEach { readJob -> readJob.closeAndCancel() }
        heartbeatMonitor.stop()
        hostConnectionRegistry.clear().forEach { connection ->
            connection.closeNow()
        }
        clientConnectionHolder.clear()?.closeNow()
        currentState = SocketManagerState()
    }

    internal suspend fun acceptSocketForTest(socket: BluetoothSocket) {
        commands.send(SocketCommand.ServerAccepted(socket))
    }

    fun clearClientConnection() {
        val state = currentState
        pendingClientSocket?.closeSafely()
        pendingClientSocket = null
        state.clientReadJob?.closeAndCancel()
        clientConnectionHolder.clear()?.closeNow()
        heartbeatMonitor.stopClientWatchdog()
        heartbeatMonitor.clearHostHeartbeat()
        currentState = state.copy(clientReadJob = null)
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun runActorLoop() {
        var state = SocketManagerState()
        currentState = state
        for (command in commands) {
            state = try {
                dispatchCommand(state, command)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                handleActorCommandFailure(command, e)
                state
            }
            currentState = state
        }
    }

    private fun handleActorCommandFailure(command: SocketCommand, error: Throwable) {
        Log.w("RoomSocketManager", "Socket command failed", error)
        when (command) {
            is SocketCommand.ServerAccepted -> command.socket.closeSafely()
            is SocketCommand.ConnectToHostSucceeded -> command.connection.closeNow()
            is SocketCommand.AttachHostConnection -> command.connection.closeNow()
            is SocketCommand.AttachClientConnection -> command.connection.closeNow()
            is SocketCommand.ReplaceHostConnection -> command.connection.closeNow()
            else -> Unit
        }
    }

    private suspend fun dispatchCommand(
        state: SocketManagerState,
        command: SocketCommand,
    ): SocketManagerState {
        return when (command) {
            is SocketCommand.StartServer,
            is SocketCommand.ServerAccepted,
            is SocketCommand.ParticipantHeartbeatTimedOut,
            is SocketCommand.ConnectToHostSucceeded,
            is SocketCommand.ConnectToHostFailed,
            -> dispatchServerCommand(state, command)

            is SocketCommand.AttachHostConnection,
            is SocketCommand.HostHeartbeatReceived,
            is SocketCommand.HostMessageReceived,
            is SocketCommand.HostReadFailed,
            is SocketCommand.ReplaceHostConnection,
            is SocketCommand.DisconnectParticipant,
            -> dispatchParticipantCommand(state, command)

            is SocketCommand.AttachClientConnection,
            SocketCommand.ClientHeartbeatReceived,
            is SocketCommand.ClientMessageReceived,
            is SocketCommand.ClientReadFailed,
            SocketCommand.HostHeartbeatTimedOut,
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
            is SocketCommand.ParticipantHeartbeatTimedOut -> handleParticipantHeartbeatTimedOut(state, command)
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
            SocketCommand.HostHeartbeatTimedOut -> handleHostHeartbeatTimedOut(state)
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
            command.result.complete(Result.failure(IllegalStateException("当前设备不支持蓝牙")))
            return state
        }

        state.acceptJob?.cancel()
        heartbeatMonitor.stopHostHeartbeat()
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
        val connection = try {
            connectionFactory.create(command.socket, frameCodec)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            command.socket.closeSafely()
            return state
        }
        _events.emit(RoomSocketEvent.IncomingConnection(connection))
        return state
    }

    private fun handleParticipantHeartbeatTimedOut(
        state: SocketManagerState,
        command: SocketCommand.ParticipantHeartbeatTimedOut,
    ): SocketManagerState {
        state.hostReadJobs[command.participantId]?.closeAndCancel()
        hostConnectionRegistry.remove(command.participantId)?.closeNow()
        _events.tryEmit(RoomSocketEvent.ParticipantDisconnected(command.participantId))
        return state.copy(hostReadJobs = state.hostReadJobs - command.participantId)
    }

    private fun handleConnectToHostSucceeded(
        state: SocketManagerState,
        command: SocketCommand.ConnectToHostSucceeded,
    ): SocketManagerState {
        command.result.complete(Result.success(command.connection.rawConnection))
        return state
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
        state.hostReadJobs[command.participantId]?.closeAndCancel()
        hostConnectionRegistry.remove(command.participantId)?.closeNow()
        heartbeatMonitor.removeParticipant(command.participantId)
        val locallyClosing = AtomicBoolean(false)
        val readJob = scope.launch {
            try {
                command.connection.readLoop { message ->
                    when (message) {
                        RoomWireMessage.HeartbeatPong -> commands.send(
                            SocketCommand.HostHeartbeatReceived(command.participantId),
                        )

                        else -> commands.send(
                            SocketCommand.HostMessageReceived(
                                participantId = command.participantId,
                                message = message,
                            ),
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: IOException) {
                if (!locallyClosing.get() && isActive) {
                    commands.send(SocketCommand.HostReadFailed(command.participantId))
                }
            } finally {
                command.connection.closeNow()
            }
        }
        hostConnectionRegistry.add(command.participantId, command.connection)
        heartbeatMonitor.trackParticipant(command.participantId)
        return state.copy(
            hostReadJobs = state.hostReadJobs + (
                command.participantId to ReadLoopHandle(command.connection, readJob, locallyClosing)
            ),
        )
    }

    private fun handleHostHeartbeatReceived(
        state: SocketManagerState,
        command: SocketCommand.HostHeartbeatReceived,
    ): SocketManagerState {
        if (!hostConnectionRegistry.contains(command.participantId)) return state
        heartbeatMonitor.markHeartbeatReceived(command.participantId)
        return state
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
        if (!hostConnectionRegistry.contains(command.participantId)) return state
        state.hostReadJobs[command.participantId]?.closeAndCancel()
        hostConnectionRegistry.remove(command.participantId)?.closeNow()
        heartbeatMonitor.removeParticipant(command.participantId)
        _events.tryEmit(RoomSocketEvent.ParticipantDisconnected(command.participantId))
        return state.copy(hostReadJobs = state.hostReadJobs - command.participantId)
    }

    private fun handleAttachClientConnection(
        state: SocketManagerState,
        command: SocketCommand.AttachClientConnection,
    ): SocketManagerState {
        state.clientReadJob?.closeAndCancel()
        val previousConnection = clientConnectionHolder.set(command.connection)
        if (previousConnection != null && !previousConnection.wraps(command.connection.rawConnection)) {
            previousConnection.closeNow()
        }
        heartbeatMonitor.markHostConnected()
        val locallyClosing = AtomicBoolean(false)
        val readJob = scope.launch {
            try {
                command.connection.readLoop { message ->
                    when (message) {
                        RoomWireMessage.HeartbeatPing -> {
                            command.connection.sendSafely(RoomWireMessage.HeartbeatPong)
                            commands.send(SocketCommand.ClientHeartbeatReceived)
                        }

                        else -> commands.send(SocketCommand.ClientMessageReceived(message))
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                if (!locallyClosing.get() && isActive) {
                    commands.send(SocketCommand.ClientReadFailed(e.toClientReadFailedReason()))
                }
            } finally {
                command.connection.closeNow()
            }
        }
        return state.copy(clientReadJob = ReadLoopHandle(command.connection, readJob, locallyClosing))
    }

    private fun handleClientHeartbeatReceived(state: SocketManagerState): SocketManagerState {
        heartbeatMonitor.markHeartbeatReceived(participantId = null)
        return state
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
        state.clientReadJob?.closeAndCancel()
        clientConnectionHolder.clear()?.closeNow()
        heartbeatMonitor.stopClientWatchdog()
        heartbeatMonitor.clearHostHeartbeat()
        return state.copy(
            clientReadJob = null,
        )
    }

    private fun handleHostHeartbeatTimedOut(state: SocketManagerState): SocketManagerState {
        if (clientConnectionHolder.current() == null) return state
        _events.tryEmit(RoomSocketEvent.HostConnectionLost("Host connection heartbeat timed out"))
        state.clientReadJob?.closeAndCancel()
        clientConnectionHolder.clear()?.closeNow()
        heartbeatMonitor.clearHostHeartbeat()
        return state.copy(clientReadJob = null)
    }

    private fun handleReplaceHostConnection(
        state: SocketManagerState,
        command: SocketCommand.ReplaceHostConnection,
    ): SocketManagerState {
        return handleAttachHostConnection(
            state = state,
            command = SocketCommand.AttachHostConnection(
                participantId = command.participantId,
                connection = command.connection,
            ),
        )
    }

    private fun handleDisconnectParticipant(
        state: SocketManagerState,
        command: SocketCommand.DisconnectParticipant,
    ): SocketManagerState {
        state.hostReadJobs[command.participantId]?.closeAndCancel()
        hostConnectionRegistry.remove(command.participantId)?.closeNow() ?: return state
        heartbeatMonitor.removeParticipant(command.participantId)
        return state.copy(hostReadJobs = state.hostReadJobs - command.participantId)
    }

    private fun handleShutdown(state: SocketManagerState): SocketManagerState {
        state.acceptJob?.cancel()
        state.clientReadJob?.closeAndCancel()
        state.serverSocket?.closeSafely()
        heartbeatMonitor.stop()
        state.hostReadJobs.values.forEach { readJob -> readJob.closeAndCancel() }
        hostConnectionRegistry.clear().forEach { connection ->
            connection.closeNow()
        }
        clientConnectionHolder.clear()?.closeNow()
        return SocketManagerState()
    }
}

fun interface RoomSocketConnectionFactory {
    fun create(socket: BluetoothSocket, frameCodec: RoomFrameCodec): RoomSocketConnection
}

private object DefaultRoomSocketConnectionFactory : RoomSocketConnectionFactory {
    override fun create(socket: BluetoothSocket, frameCodec: RoomFrameCodec): RoomSocketConnection {
        return RoomSocketConnection(socket = socket, frameCodec = frameCodec)
    }
}

class RoomSocketConnection(
    socket: BluetoothSocket,
    private val frameCodec: RoomFrameCodec,
) : RoomClientConnection {
    private val input = DataInputStream(socket.inputStream)
    private val output = DataOutputStream(socket.outputStream)
    private val bluetoothSocket = socket
    val remoteAddress: String = socket.remoteDevice?.address.orEmpty()

    override suspend fun awaitJoinAccepted(
        playerName: String,
        avatarResId: Int?,
        resumeParticipantId: String?,
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
            is RoomWireMessage.JoinRoomRejected -> throw UserVisibleRoomException(
                response.reason.ifBlank { JOIN_REJECTED_DEFAULT_MESSAGE },
            )
            else -> throw IOException("房间响应异常，请重试")
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
        closeNow()
    }

    override fun closeNow() {
        closeIgnoringFailure { input.close() }
        closeIgnoringFailure { output.close() }
        closeIgnoringFailure { bluetoothSocket.close() }
    }

    private fun closeIgnoringFailure(closeBlock: () -> Unit) {
        try {
            closeBlock()
        } catch (_: IOException) {
            // ignore close failure
        } catch (_: RuntimeException) {
            // ignore close failure
        }
    }
}

private fun BluetoothServerSocket.closeSafely() {
    try {
        close()
    } catch (_: IOException) {
        // ignore close failure
    } catch (_: RuntimeException) {
        // ignore close failure
    }
}

private fun BluetoothSocket.closeSafely() {
    try {
        close()
    } catch (_: IOException) {
        // ignore close failure
    } catch (_: RuntimeException) {
        // ignore close failure
    }
}

private fun IOException.toClientReadFailedReason(): String {
    return if (isRoomProtocolFailure()) {
        ROOM_CONNECTION_ABNORMAL_MESSAGE
    } else {
        "与房主的连接已断开"
    }
}

private fun IOException.isRoomProtocolFailure(): Boolean {
    val rawMessage = message.orEmpty()
    return rawMessage.contains("Failed to decode room protocol message", ignoreCase = true) ||
        rawMessage.contains("Invalid room protocol message", ignoreCase = true)
}

private const val HOST_START_TIMEOUT_MS = 8_000L
private const val HOST_START_TIMEOUT_MESSAGE = "开启蓝牙房间超时"
private const val ROOM_CONNECTION_ABNORMAL_MESSAGE = "房间连接异常，请重新加入房间"
private const val JOIN_REJECTED_DEFAULT_MESSAGE = "房主拒绝了入房请求"
