package com.example.chudadi.network.bluetooth.transport

import com.example.chudadi.network.room.RoomSocketConnection
import com.example.chudadi.network.room.RoomWireMessage
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive

/**
 * Wraps a single bluetooth socket connection and hides direct stream access from transport coordinators.
 *
 * Concurrency risk: ClassicBluetoothTransport and HeartbeatMonitor can currently write to the same
 * connection from different coroutines. The underlying socket write path is not explicitly serialized yet;
 * address this when the transport layer is slimmed down further.
 */
class BluetoothConnection(
    val rawConnection: RoomSocketConnection,
    val id: String = rawConnection.remoteAddress.ifBlank {
        System.identityHashCode(rawConnection).toString()
    },
    val deviceName: String? = null,
    val deviceAddress: String? = rawConnection.remoteAddress.ifBlank { null },
) {
    suspend fun send(message: RoomWireMessage) {
        rawConnection.send(message)
    }

    fun sendSafely(message: RoomWireMessage) {
        rawConnection.sendSafely(message)
    }

    suspend fun readLoop(onMessage: suspend (RoomWireMessage) -> Unit) {
        while (currentCoroutineContext().isActive) {
            onMessage(rawConnection.read())
        }
    }

    suspend fun close() {
        rawConnection.close()
    }

    fun closeNow() {
        rawConnection.close()
    }

    fun wraps(connection: RoomSocketConnection): Boolean = rawConnection === connection
}
