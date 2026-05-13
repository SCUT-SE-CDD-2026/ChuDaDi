package com.example.chudadi.network.bluetooth.platform

import android.bluetooth.BluetoothAdapter
import android.content.Intent

/**
 * Creates classic bluetooth discoverability requests; this is separate from starting room listening.
 */
class BluetoothDiscoverabilityController(
    private val permissionChecker: BluetoothPermissionChecker,
) {
    fun isDiscoverabilitySupported(): Boolean {
        return permissionChecker.isBluetoothSupported() && permissionChecker.hasConnectPermission()
    }

    fun createDiscoverableIntent(durationSeconds: Int): Intent {
        return Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
            putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, durationSeconds)
        }
    }
}
