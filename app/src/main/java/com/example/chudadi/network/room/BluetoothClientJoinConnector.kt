package com.example.chudadi.network.room

import com.example.chudadi.network.bluetooth.transport.RoomTransport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.util.UUID

internal class BluetoothClientJoinConnector(
    private val roomTransport: RoomTransport,
    private val roomUuid: UUID,
    private val connectTimeoutMs: Long,
    private val connectTimeoutMessage: String,
    private val joinTimeoutMs: Long,
    private val joinTimeoutMessage: String,
) {
    suspend fun connectAndAwaitAccepted(
        device: BluetoothDiscoveredDevice,
        playerName: String,
        avatarResId: Int?,
        resumeParticipantId: String?,
    ): Result<ClientJoinSession> {
        val connectResult = connectToHost(device)
        val connectError = connectResult.exceptionOrNull()
        if (connectError != null) {
            if (connectError is CancellationException) throw connectError
            return Result.failure(connectError)
        }
        val connection = requireNotNull(connectResult.getOrNull())
        return awaitJoinAccepted(
            connection = connection,
            playerName = playerName,
            avatarResId = avatarResId,
            resumeParticipantId = resumeParticipantId,
        )
    }

    private suspend fun connectToHost(device: BluetoothDiscoveredDevice): Result<RoomClientConnection> {
        return try {
            withTimeout(connectTimeoutMs) {
                roomTransport.connectToHost(device, roomUuid)
            }
        } catch (error: TimeoutCancellationException) {
            roomTransport.clearClientConnection()
            Result.failure(IOException(connectTimeoutMessage, error))
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun awaitJoinAccepted(
        connection: RoomClientConnection,
        playerName: String,
        avatarResId: Int?,
        resumeParticipantId: String?,
    ): Result<ClientJoinSession> {
        return try {
            val accepted = withTimeout(joinTimeoutMs) {
                connection.awaitJoinAccepted(
                    playerName = playerName,
                    avatarResId = avatarResId,
                    resumeParticipantId = resumeParticipantId,
                )
            }
            Result.success(ClientJoinSession(connection = connection, accepted = accepted))
        } catch (error: TimeoutCancellationException) {
            roomTransport.cleanupFailedClientJoin(connection)
            Result.failure(IOException(joinTimeoutMessage, error))
        } catch (error: CancellationException) {
            roomTransport.cleanupFailedClientJoin(connection)
            throw error
        } catch (error: Exception) {
            roomTransport.cleanupFailedClientJoin(connection)
            Result.failure(error)
        }
    }
}

internal fun RoomTransport.cleanupFailedClientJoin(connection: RoomClientConnection) {
    connection.closeNow()
    clearClientConnection()
}

internal data class ClientJoinSession(
    val connection: RoomClientConnection,
    val accepted: RoomWireMessage.JoinRoomAccepted,
)
