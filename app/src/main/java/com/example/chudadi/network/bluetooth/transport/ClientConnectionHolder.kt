package com.example.chudadi.network.bluetooth.transport

/**
 * Holds the client's single active connection to the host without applying room or reconnect rules.
 *
 * This class models the client side of a room as exactly one host connection. Host-side participant
 * connections are tracked separately by HostConnectionRegistry.
 */
class ClientConnectionHolder {
    private var hostConnection: BluetoothConnection? = null

    @Synchronized
    fun set(connection: BluetoothConnection): BluetoothConnection? {
        val previous = hostConnection
        hostConnection = connection
        return previous
    }

    @Synchronized
    fun current(): BluetoothConnection? = hostConnection

    @Synchronized
    fun clear(): BluetoothConnection? {
        val previous = hostConnection
        hostConnection = null
        return previous
    }

    suspend fun close() {
        clear()?.close()
    }
}
