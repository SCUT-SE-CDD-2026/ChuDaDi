package com.example.chudadi.network.bluetooth.platform

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import com.example.chudadi.network.bluetooth.BluetoothPermissionUtils

/**
 * Checks bluetooth adapter availability and runtime bluetooth permissions.
 *
 * Keep permission policy changes here so repository and transport code do not need to know Android
 * version-specific bluetooth permission details.
 */
class BluetoothPermissionChecker(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val bluetoothAdapter: BluetoothAdapter? =
        appContext.getSystemService(BluetoothManager::class.java)?.adapter

    fun isBluetoothSupported(): Boolean = bluetoothAdapter != null

    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

    fun hasScanPermission(): Boolean = BluetoothPermissionUtils.hasScanPermission(appContext)

    fun hasConnectPermission(): Boolean = BluetoothPermissionUtils.hasConnectPermission(appContext)

    fun hasRequiredBluetoothPermissions(): Boolean = hasScanPermission() && hasConnectPermission()

    fun requireBluetoothAdapter(): BluetoothAdapter? = bluetoothAdapter
}
