package com.example.chudadi.network.bluetooth.transport

import java.util.UUID

/**
 * Holds the transport-only parameters required to start hosting a room.
 */
data class HostTransportConfig(
    val serviceName: String,
    val serviceUuid: UUID,
)
