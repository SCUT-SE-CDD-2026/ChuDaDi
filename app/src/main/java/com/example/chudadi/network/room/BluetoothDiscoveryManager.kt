@file:Suppress("TooManyFunctions")

package com.example.chudadi.network.room

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Parcelable
import android.util.Log
import com.example.chudadi.network.bluetooth.BluetoothPermissionUtils
import java.io.IOException
import kotlinx.coroutines.CancellationException
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

class BluetoothDiscoveryStartException(
    message: String = "Bluetooth discovery failed to start",
    cause: Throwable? = null,
) : IOException(message, cause)

class BluetoothDiscoveryManager(
    context: Context,
    private val bluetoothAdapter: BluetoothAdapter? = defaultBluetoothAdapter(context.applicationContext),
    private val scanPermissionChecker: (Context) -> Boolean = BluetoothPermissionUtils::hasScanPermission,
    private val connectPermissionChecker: (Context) -> Boolean = BluetoothPermissionUtils::hasConnectPermission,
    private val receiverRegistrar: BluetoothDiscoveryReceiverRegistrar =
        AndroidBluetoothDiscoveryReceiverRegistrar(context.applicationContext),
) {
    private val appContext = context.applicationContext
    private val _devices = MutableStateFlow<List<BluetoothDiscoveredDevice>>(emptyList())
    private val _events = MutableSharedFlow<BluetoothDiscoveryEvent>(extraBufferCapacity = 4)
    private var discoveryReceiverRegistered = false
    private var discoveryInProgress = false

    val devices: StateFlow<List<BluetoothDiscoveredDevice>> = _devices.asStateFlow()
    val events: SharedFlow<BluetoothDiscoveryEvent> = _events.asSharedFlow()

    private val discoveryReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val device = intent.parcelableExtraCompat<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                            ?: return
                        addDiscoveredDevice(device = device, isBonded = false)
                    }

                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                        clearDiscoveryLifecycle()
                        _events.tryEmit(BluetoothDiscoveryEvent.DiscoveryFinished)
                    }
                }
            }
        }

    val isBluetoothSupported: Boolean
        get() = bluetoothAdapter != null

    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

    fun hasBluetoothConnectPermission(): Boolean = connectPermissionChecker(appContext)

    fun hasBluetoothScanPermission(): Boolean = scanPermissionChecker(appContext)

    fun loadBondedDevices(): List<BluetoothDiscoveredDevice> {
        val devices = getBondedDevices()
        _devices.value = devices
        return devices
    }

    @SuppressLint("MissingPermission")
    @Suppress("TooGenericExceptionCaught")
    fun startDiscovery(): Result<Unit> {
        stopDiscoveryIfNeeded()
        val adapter = bluetoothAdapter
            ?: return Result.failure(BluetoothDiscoveryStartException("Bluetooth adapter is not available"))
        if (!hasBluetoothScanPermission()) {
            clearDiscoveryLifecycle()
            return Result.failure(SecurityException("Missing bluetooth scan permission"))
        }
        return try {
            registerDiscoveryReceiverIfNeeded()
            loadBondedDevices()
            val started = adapter.startDiscovery()
            if (started) {
                discoveryInProgress = true
                Result.success(Unit)
            } else {
                clearDiscoveryLifecycle()
                Result.failure(BluetoothDiscoveryStartException())
            }
        } catch (error: CancellationException) {
            clearDiscoveryLifecycle()
            throw error
        } catch (error: SecurityException) {
            clearDiscoveryLifecycle()
            Result.failure(error)
        } catch (error: IllegalStateException) {
            clearDiscoveryLifecycle()
            Result.failure(error)
        } catch (error: Throwable) {
            clearDiscoveryLifecycle()
            Result.failure(error)
        }
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
    @Suppress("TooGenericExceptionCaught")
    fun stopDiscoveryIfNeeded() {
        try {
            val adapter = bluetoothAdapter
            if (adapter != null && hasBluetoothScanPermission() && adapter.isDiscovering) {
                adapter.cancelDiscovery()
            }
        } catch (error: RuntimeException) {
            logDiscoveryCleanupFailure(error)
        } finally {
            clearDiscoveryLifecycle()
        }
    }

    fun clear() {
        stopDiscoveryIfNeeded()
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
        receiverRegistrar.register(discoveryReceiver)
        discoveryReceiverRegistered = true
    }

    @Suppress("TooGenericExceptionCaught")
    private fun unregisterDiscoveryReceiverIfNeeded() {
        if (!discoveryReceiverRegistered) return
        try {
            receiverRegistrar.unregister(discoveryReceiver)
        } catch (error: RuntimeException) {
            logDiscoveryCleanupFailure(error)
        }
        discoveryReceiverRegistered = false
    }

    private fun clearDiscoveryLifecycle() {
        unregisterDiscoveryReceiverIfNeeded()
        discoveryInProgress = false
    }

    private fun logDiscoveryCleanupFailure(error: RuntimeException) {
        try {
            Log.w("BluetoothDiscoveryManager", "Bluetooth discovery cleanup failed", error)
        } catch (_: RuntimeException) {
            // Android Log is not available in local JVM unit tests.
        }
    }

    internal fun isDiscoveryReceiverRegisteredForTest(): Boolean = discoveryReceiverRegistered

    internal fun isDiscoveryInProgressForTest(): Boolean = discoveryInProgress
}

interface BluetoothDiscoveryReceiverRegistrar {
    fun register(receiver: BroadcastReceiver)

    fun unregister(receiver: BroadcastReceiver)
}

private class AndroidBluetoothDiscoveryReceiverRegistrar(
    private val appContext: Context,
) : BluetoothDiscoveryReceiverRegistrar {
    override fun register(receiver: BroadcastReceiver) {
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        appContext.registerReceiver(receiver, filter)
    }

    override fun unregister(receiver: BroadcastReceiver) {
        appContext.unregisterReceiver(receiver)
    }
}

private fun defaultBluetoothAdapter(context: Context): BluetoothAdapter? {
    val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    return bluetoothManager?.adapter
}

private inline fun <reified T : Parcelable> Intent.parcelableExtraCompat(key: String): T? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(key, T::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(key)
    }
}
