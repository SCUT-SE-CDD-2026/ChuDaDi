package com.example.chudadi.network.bluetooth.platform

import android.annotation.SuppressLint
import com.example.chudadi.network.room.BluetoothDiscoveredDevice
import com.example.chudadi.network.room.BluetoothDiscoveryEvent
import com.example.chudadi.network.room.BluetoothDiscoveryManager
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Owns bluetooth discovery, paired device loading, and discovered device state.
 *
 * Discovery means scanning nearby devices that may host rooms. It is intentionally separate from
 * BluetoothDiscoverabilityController, which makes the local device visible to other scanners.
 */
class BluetoothDiscoveryService(
    private val discoveryManager: BluetoothDiscoveryManager,
) {
    val devices: StateFlow<List<BluetoothDiscoveredDevice>> = discoveryManager.devices
    val events: SharedFlow<BluetoothDiscoveryEvent> = discoveryManager.events

    @SuppressLint("MissingPermission")
    fun loadBondedDevices(): List<BluetoothDiscoveredDevice> = discoveryManager.loadBondedDevices()

    @SuppressLint("MissingPermission")
    fun getBondedDevices(): List<BluetoothDiscoveredDevice> = discoveryManager.getBondedDevices()

    @SuppressLint("MissingPermission")
    fun startDiscovery(): Boolean = discoveryManager.startDiscovery()

    fun stopDiscovery() {
        discoveryManager.stopDiscoveryIfNeeded()
    }

    fun clearDiscoveredDevices() {
        discoveryManager.clear()
    }
}
