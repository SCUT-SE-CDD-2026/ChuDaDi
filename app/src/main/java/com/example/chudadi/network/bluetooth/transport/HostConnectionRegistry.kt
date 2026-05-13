package com.example.chudadi.network.bluetooth.transport

/**
 * Keeps the host-side participant connection table without handling reads, protocol rules, or UI state.
 *
 * Keys are room participant ids assigned by room membership code. Values are the current bluetooth
 * connections used by ClassicBluetoothTransport for direct sends and multi-recipient broadcasts.
 */
class HostConnectionRegistry {
    private val connections = linkedMapOf<String, BluetoothConnection>()

    @Synchronized
    fun add(participantId: String, connection: BluetoothConnection): BluetoothConnection? {
        return connections.put(participantId, connection)
    }

    @Synchronized
    fun remove(participantId: String): BluetoothConnection? = connections.remove(participantId)

    @Synchronized
    fun find(participantId: String): BluetoothConnection? = connections[participantId]

    @Synchronized
    fun all(): List<BluetoothConnection> = connections.values.toList()

    @Synchronized
    fun broadcastTargets(): List<BluetoothConnection> = all()

    @Synchronized
    fun clear(): List<BluetoothConnection> {
        val removed = connections.values.toList()
        connections.clear()
        return removed
    }

    @Synchronized
    fun contains(participantId: String): Boolean = connections.containsKey(participantId)
}
