package com.example.chudadi.network.room

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

@OptIn(ExperimentalCoroutinesApi::class)
class BluetoothDiscoveryManagerTest {
    @Test
    fun startDiscoveryFalseUnregistersReceiver() {
        val context = context()
        val adapter = adapter(startResult = false)
        val registrar = FakeReceiverRegistrar()
        val manager = manager(context = context, adapter = adapter, registrar = registrar)

        val result = manager.startDiscovery()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is BluetoothDiscoveryStartException)
        assertFalse(manager.isDiscoveryReceiverRegisteredForTest())
        assertFalse(manager.isDiscoveryInProgressForTest())
        assertTrue(registrar.registerCallCount == 1)
        assertTrue(registrar.unregisterCallCount == 1)
    }

    @Test
    fun startDiscoveryUsesBluetoothManagerAdapterWhenAdapterIsNotInjected() {
        val context = context()
        val bluetoothManager = mock(BluetoothManager::class.java)
        val adapter = adapter(startResult = true)
        val registrar = FakeReceiverRegistrar()
        `when`(context.getSystemService(Context.BLUETOOTH_SERVICE)).thenReturn(bluetoothManager)
        `when`(bluetoothManager.adapter).thenReturn(adapter)
        val manager = BluetoothDiscoveryManager(
            context = context,
            scanPermissionChecker = { true },
            connectPermissionChecker = { true },
            receiverRegistrar = registrar,
        )

        val result = manager.startDiscovery()

        assertTrue(result.isSuccess)
        assertTrue(registrar.registerCallCount == 1)
        assertTrue(manager.isDiscoveryReceiverRegisteredForTest())
    }

    @Test
    fun startDiscoverySecurityExceptionUnregistersReceiver() {
        val error = SecurityException("scan denied")
        val context = context()
        val adapter = adapter(startError = error)
        val registrar = FakeReceiverRegistrar()
        val manager = manager(context = context, adapter = adapter, registrar = registrar)

        val result = manager.startDiscovery()

        assertTrue(result.isFailure)
        assertSame(error, result.exceptionOrNull())
        assertFalse(manager.isDiscoveryReceiverRegisteredForTest())
        assertFalse(manager.isDiscoveryInProgressForTest())
        assertTrue(registrar.registerCallCount == 1)
        assertTrue(registrar.unregisterCallCount == 1)
    }

    @Test
    fun stopDiscoveryIsIdempotent() {
        val context = context()
        val adapter = adapter(startResult = true)
        val registrar = FakeReceiverRegistrar()
        val manager = manager(context = context, adapter = adapter, registrar = registrar)

        val result = manager.startDiscovery()
        manager.stopDiscoveryIfNeeded()
        manager.stopDiscoveryIfNeeded()

        assertTrue(result.isSuccess)
        assertFalse(manager.isDiscoveryReceiverRegisteredForTest())
        assertFalse(manager.isDiscoveryInProgressForTest())
        assertTrue(registrar.unregisterCallCount == 1)
    }

    @Test
    fun refreshDiscoveryDoesNotRegisterReceiverTwiceWithoutUnregisteringOldReceiver() {
        val context = context()
        val adapter = adapter(startResult = true)
        val registrar = FakeReceiverRegistrar()
        val manager = manager(context = context, adapter = adapter, registrar = registrar)

        val first = manager.startDiscovery()
        val second = manager.startDiscovery()

        assertTrue(first.isSuccess)
        assertTrue(second.isSuccess)
        assertTrue(manager.isDiscoveryReceiverRegisteredForTest())
        assertTrue(manager.isDiscoveryInProgressForTest())
        assertTrue(registrar.registerCallCount == 2)
        assertTrue(registrar.unregisterCallCount == 1)
    }

    @Test
    fun startDiscoveryWithoutPermissionClearsExistingReceiver() {
        var scanPermission = true
        val context = context()
        val adapter = adapter(startResult = true)
        val registrar = FakeReceiverRegistrar()
        val manager = manager(
            context = context,
            adapter = adapter,
            registrar = registrar,
            scanPermissionChecker = { scanPermission },
        )

        val first = manager.startDiscovery()
        scanPermission = false
        val second = manager.startDiscovery()

        assertTrue(first.isSuccess)
        assertTrue(second.isFailure)
        assertTrue(second.exceptionOrNull() is SecurityException)
        assertFalse(manager.isDiscoveryReceiverRegisteredForTest())
        assertFalse(manager.isDiscoveryInProgressForTest())
        assertTrue(registrar.registerCallCount == 1)
        assertTrue(registrar.unregisterCallCount == 1)
    }

    @Test
    fun discoveryFinishedUnregistersReceiverAndClearsState() = runTest {
        val context = context()
        val adapter = adapter(startResult = true)
        val registrar = FakeReceiverRegistrar()
        val manager = manager(context = context, adapter = adapter, registrar = registrar)
        val event = async { manager.events.first() }
        runCurrent()

        val result = manager.startDiscovery()
        requireNotNull(registrar.registeredReceiver)
            .onReceive(context, discoveryFinishedIntent())

        assertTrue(result.isSuccess)
        assertFalse(manager.isDiscoveryReceiverRegisteredForTest())
        assertFalse(manager.isDiscoveryInProgressForTest())
        assertEquals(1, registrar.unregisterCallCount)
        assertEquals(BluetoothDiscoveryEvent.DiscoveryFinished, event.await())
    }

    @Test
    fun discoveryFinishedCleanupIsIdempotent() {
        val context = context()
        val adapter = adapter(startResult = true)
        val registrar = FakeReceiverRegistrar()
        val manager = manager(context = context, adapter = adapter, registrar = registrar)

        val result = manager.startDiscovery()
        requireNotNull(registrar.registeredReceiver)
            .onReceive(context, discoveryFinishedIntent())
        manager.stopDiscoveryIfNeeded()

        assertTrue(result.isSuccess)
        assertFalse(manager.isDiscoveryReceiverRegisteredForTest())
        assertFalse(manager.isDiscoveryInProgressForTest())
        assertEquals(1, registrar.unregisterCallCount)
    }

    private fun manager(
        context: Context,
        adapter: BluetoothAdapter,
        registrar: BluetoothDiscoveryReceiverRegistrar,
        scanPermissionChecker: (Context) -> Boolean = { true },
    ): BluetoothDiscoveryManager {
        return BluetoothDiscoveryManager(
            context = context,
            bluetoothAdapter = adapter,
            scanPermissionChecker = scanPermissionChecker,
            connectPermissionChecker = { true },
            receiverRegistrar = registrar,
        )
    }

    private fun context(): Context {
        val context = mock(Context::class.java)
        `when`(context.applicationContext).thenReturn(context)
        return context
    }

    private fun discoveryFinishedIntent(): Intent {
        val intent = mock(Intent::class.java)
        `when`(intent.action).thenReturn(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        return intent
    }

    private class FakeReceiverRegistrar : BluetoothDiscoveryReceiverRegistrar {
        var registerCallCount = 0
        var unregisterCallCount = 0
        var registeredReceiver: BroadcastReceiver? = null

        override fun register(receiver: BroadcastReceiver) {
            registerCallCount++
            registeredReceiver = receiver
        }

        override fun unregister(receiver: BroadcastReceiver) {
            unregisterCallCount++
            registeredReceiver = null
        }
    }

    private fun adapter(
        startResult: Boolean = true,
        startError: RuntimeException? = null,
    ): BluetoothAdapter {
        val adapter = mock(BluetoothAdapter::class.java)
        `when`(adapter.isDiscovering).thenReturn(false)
        `when`(adapter.bondedDevices).thenReturn(emptySet<BluetoothDevice>())
        if (startError == null) {
            `when`(adapter.startDiscovery()).thenReturn(startResult)
        } else {
            `when`(adapter.startDiscovery()).thenThrow(startError)
        }
        return adapter
    }
}
