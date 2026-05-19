package com.example.chudadi.network.bluetooth.transport

import com.example.chudadi.network.room.RoomSocketConnection
import com.example.chudadi.network.room.RoomWireMessage

/**
 * Describes transport-level room events without applying room rules or UI wording.
 *
 * A null participant id means the client-side connection to the host. A non-null participant id
 * means a host-side connection to that participant.
 */
sealed interface RoomTransportEvent {
    data object HostStarted : RoomTransportEvent

    data class HostStartFailed(
        val cause: Throwable,
    ) : RoomTransportEvent

    data class ClientConnected(
        val deviceName: String?,
        val deviceAddress: String?,
    ) : RoomTransportEvent

    data class IncomingConnection(
        val connection: RoomSocketConnection,
    ) : RoomTransportEvent

    data class MessageReceived(
        val fromParticipantId: String?,
        val payload: RoomWireMessage,
    ) : RoomTransportEvent

    data class ConnectionLost(
        val participantId: String?,
        val cause: Throwable?,
    ) : RoomTransportEvent

    data class SendFailed(
        val participantId: String?,
        val messageType: String,
        val cause: Throwable,
    ) : RoomTransportEvent

    data class TransportError(
        val cause: Throwable,
    ) : RoomTransportEvent

    data object Closed : RoomTransportEvent
}
