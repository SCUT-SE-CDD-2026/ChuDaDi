package com.example.chudadi.network.room.presentation

import com.example.chudadi.network.room.BluetoothDiscoveredDevice
import com.example.chudadi.network.room.RoomAuthorityStore
import com.example.chudadi.ui.room.BluetoothSearchState
import com.example.chudadi.ui.room.DiscoveredDeviceUiState
import com.example.chudadi.ui.room.RoomMode
import com.example.chudadi.ui.room.RoomUiState
import com.example.chudadi.ui.room.SlotState
import com.example.chudadi.ui.room.SwapRequest

/**
 * Maps bluetooth room authority data and lightweight presentation inputs into room UI state.
 */
class RoomUiStateMapper {
    @Suppress("LongParameterList")
    fun toUiState(
        authorityStore: RoomAuthorityStore,
        previousState: RoomUiState,
        discoveredDevices: List<BluetoothDiscoveredDevice>,
        connectionHint: String? = null,
        pendingSwapRequest: SwapRequest? = previousState.pendingSwapRequest,
        showAiDifficultyDialog: Boolean = previousState.showAiDifficultyDialog,
        aiDialogTargetSlot: Int = previousState.aiDialogTargetSlot,
        showSlotActionMenu: Boolean = previousState.showSlotActionMenu,
        slotActionMenuTarget: Int = previousState.slotActionMenuTarget,
        localParticipantId: String,
        isHost: Boolean,
        roomMode: RoomMode = previousState.roomMode,
        searchState: BluetoothSearchState = previousState.searchState,
        selectedDeviceAddress: String? = previousState.selectedDeviceAddress,
    ): RoomUiState {
        val slots = authorityStore.buildSlotStates(localParticipantId = localParticipantId)
        val canStart = authorityStore.canStart(localParticipantId)
        return RoomUiState(
            isHost = isHost,
            roomMode = roomMode,
            roomName = authorityStore.state.roomName,
            hostDeviceName = authorityStore.state.hostDeviceName,
            currentRule = authorityStore.state.currentRule,
            slots = slots,
            bluetoothVisible = authorityStore.state.bluetoothVisible && roomMode != RoomMode.LOCAL,
            connectionHint = connectionHint ?: previousState.connectionHint,
            homeNoticeMessage = previousState.homeNoticeMessage,
            discoveredDevices = discoveredDevices.map(::toDiscoveredDeviceUiState),
            searchState = searchState,
            selectedDeviceAddress = selectedDeviceAddress,
            canStartGame = canStart,
            canStartLocalGame = canStart && roomMode == RoomMode.LOCAL && isHost,
            canStartNetworkGame = canStart && roomMode == RoomMode.BLUETOOTH_HOST && isHost,
            canEnableBroadcast = roomMode == RoomMode.LOCAL && isHost,
            canManageAiSeats = isHost && roomMode != RoomMode.BLUETOOTH_CLIENT,
            pendingSwapRequest = pendingSwapRequest,
            removedFromRoom = previousState.removedFromRoom,
            roomClosedByHost = previousState.roomClosedByHost,
            joinErrorMessage = previousState.joinErrorMessage,
            joinErrorTitle = previousState.joinErrorTitle,
            showAiDifficultyDialog = showAiDifficultyDialog,
            aiDialogTargetSlot = aiDialogTargetSlot,
            showSlotActionMenu = showSlotActionMenu,
            slotActionMenuTarget = slotActionMenuTarget,
        )
    }

    fun resetRoomUiState(previousState: RoomUiState): RoomUiState {
        return previousState.copy(
            slots = List(RoomAuthorityStore.SLOT_COUNT) { index -> SlotState(slotIndex = index) },
            bluetoothVisible = false,
            roomMode = RoomMode.LOCAL,
            canStartLocalGame = false,
            canStartNetworkGame = false,
            canEnableBroadcast = false,
            canManageAiSeats = false,
        )
    }

    fun withBondedDevices(
        previousState: RoomUiState,
        devices: List<BluetoothDiscoveredDevice>,
    ): RoomUiState {
        return previousState.copy(
            discoveredDevices = devices.map(::toDiscoveredDeviceUiState),
            connectionHint = if (devices.isEmpty()) {
                "暂无已配对设备，请先让房主创建房间"
            } else {
                "已加载已配对设备，请选择要加入的房间"
            },
        )
    }

    fun withDiscoveryFinished(previousState: RoomUiState): RoomUiState {
        return previousState.copy(
            searchState = BluetoothSearchState.IDLE,
            connectionHint = if (previousState.discoveredDevices.isEmpty()) {
                "未发现可连接房间，请确认房主已创建房间"
            } else {
                "扫描完成，请选择要加入的房间"
            },
        )
    }

    fun toDiscoveredDeviceUiState(device: BluetoothDiscoveredDevice): DiscoveredDeviceUiState {
        return DiscoveredDeviceUiState(
            name = device.name,
            address = device.address,
            isBonded = device.isBonded,
        )
    }
}
