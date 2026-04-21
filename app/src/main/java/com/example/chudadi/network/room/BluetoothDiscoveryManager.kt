@file:Suppress("TooManyFunctions")

package com.example.chudadi.network.room

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.example.chudadi.network.bluetooth.BluetoothPermissionUtils
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class BluetoothDiscoveredDevice(
    val name: String,
    val address: String,
    val isBonded: Boolean,
)

sealed interface BluetoothDiscoveryEvent {
    data object DiscoveryFinished : BluetoothDiscoveryEvent
}

class BluetoothDiscoveryManager(
    context: Context,
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter(),
) {
    private val appContext = context.applicationContext
    private val _devices = MutableStateFlow<List<BluetoothDiscoveredDevice>>(emptyList())
    private val _events = MutableSharedFlow<BluetoothDiscoveryEvent>(extraBufferCapacity = 4)
    private var discoveryReceiverRegistered = false

    val devices: StateFlow<List<BluetoothDiscoveredDevice>> = _devices.asStateFlow()
    val events: SharedFlow<BluetoothDiscoveryEvent> = _events.asSharedFlow()

    private val discoveryReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return
                        addDiscoveredDevice(device = device, isBonded = false)
                    }

                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                        _events.tryEmit(BluetoothDiscoveryEvent.DiscoveryFinished)
                    }
                }
            }
        }

    val isBluetoothSupported: Boolean
        get() = bluetoothAdapter != null

    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

    fun hasBluetoothConnectPermission(): Boolean = BluetoothPermissionUtils.hasConnectPermission(appContext)

    fun hasBluetoothScanPermission(): Boolean = BluetoothPermissionUtils.hasScanPermission(appContext)

    @SuppressLint("MissingPermission")
    fun loadBondedDevices(): List<BluetoothDiscoveredDevice> {
        val devices = getBondedDevices()
        _devices.value = devices
        return devices
    }

    @SuppressLint("MissingPermission")
    fun startDiscovery(): Boolean {
        val adapter = bluetoothAdapter ?: return false
        if (!hasBluetoothScanPermission()) return false
        registerDiscoveryReceiverIfNeeded()
        if (adapter.isDiscovering) {
            adapter.cancelDiscovery()
        }
        loadBondedDevices()
        return adapter.startDiscovery()
    }

    @SuppressLint("MissingPermission")
    fun getBondedDevices(): List<BluetoothDiscoveredDevice> {
        val adapter = bluetoothAdapter ?: return emptyList()
        if (!hasBluetoothConnectPermission()) {
            return emptyList()
        }
        return adapter.bondedDevices.orEmpty()
            .sortedBy { it.name.orEmpty() }
            .map { device ->
                BluetoothDiscoveredDevice(
                    name = device.name.orEmpty().ifBlank { "未知设备" },
                    address = device.address,
                    isBonded = true,
                )
            }
    }

    @SuppressLint("MissingPermission")
    fun stopDiscoveryIfNeeded() {
        val adapter = bluetoothAdapter ?: return
        if (!hasBluetoothScanPermission()) {
            return
        }
        if (adapter.isDiscovering) {
            adapter.cancelDiscovery()
        }
    }

    fun clear() {
        stopDiscoveryIfNeeded()
        unregisterDiscoveryReceiverIfNeeded()
        _devices.value = emptyList()
    }

    @SuppressLint("MissingPermission")
    private fun addDiscoveredDevice(device: BluetoothDevice, isBonded: Boolean) {
        if (!hasBluetoothConnectPermission()) {
            return
        }
        val item = BluetoothDiscoveredDevice(
            name = device.name.orEmpty().ifBlank { "未知设备" },
            address = device.address,
            isBonded = isBonded,
        )
        _devices.update { state ->
            (state + item)
                .distinctBy { it.address }
                .sortedWith(compareByDescending<BluetoothDiscoveredDevice> { it.isBonded }.thenBy { it.name })
        }
    }

    private fun registerDiscoveryReceiverIfNeeded() {
        if (discoveryReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        appContext.registerReceiver(discoveryReceiver, filter)
        discoveryReceiverRegistered = true
    }

    private fun unregisterDiscoveryReceiverIfNeeded() {
        if (!discoveryReceiverRegistered) return
        appContext.unregisterReceiver(discoveryReceiver)
        discoveryReceiverRegistered = false
    }
}
