package com.example.chudadi.network.room

import kotlinx.serialization.Serializable

@Serializable
data class RemoteRoomSnapshot(
    val roomName: String,
    val hostDeviceName: String,
    val currentRule: String,
    val slots: List<RemoteSlotSnapshot>,
    val connectionHint: String = "",
)

@Serializable
data class RemoteSlotSnapshot(
    val slotIndex: Int,
    val seatId: Int,
    val participantId: String? = null,
    val occupantType: String? = null,
    val displayName: String = "",
    val avatarResId: Int? = null,
    val connectionStatus: String? = null,
    val aiDifficulty: String? = null,
    val cumulativeScore: Int = 0,
)

@Serializable
data class RemoteSwapRequest(
    val requesterSlotIndex: Int,
    val targetSlotIndex: Int,
    val requesterName: String,
)

@Serializable
sealed class RoomWireMessage {
    @Serializable
    data class JoinRoomRequest(
        val playerName: String,
        val avatarResId: Int?,
    ) : RoomWireMessage()

    @Serializable
    data class JoinRoomAccepted(
        val localParticipantId: String,
        val snapshot: RemoteRoomSnapshot,
    ) : RoomWireMessage()

    @Serializable
    data class JoinRoomRejected(
        val reason: String,
    ) : RoomWireMessage()

    @Serializable
    data class RemovedFromRoom(
        val reason: String,
    ) : RoomWireMessage()

    @Serializable
    data class RoomClosedByHost(
        val reason: String,
    ) : RoomWireMessage()

    @Serializable
    data class RoomSnapshotMessage(
        val snapshot: RemoteRoomSnapshot,
    ) : RoomWireMessage()

    @Serializable
    data class SwapSeatRequestMessage(
        val targetSlotIndex: Int,
    ) : RoomWireMessage()

    @Serializable
    data class SwapSeatPromptMessage(
        val request: RemoteSwapRequest,
    ) : RoomWireMessage()

    @Serializable
    data class SwapSeatDecisionMessage(
        val requesterSlotIndex: Int,
        val targetSlotIndex: Int,
        val accepted: Boolean,
    ) : RoomWireMessage()

    @Serializable
    data class ReadyStateChangeMessage(
        val ready: Boolean,
    ) : RoomWireMessage()

    @Serializable
    data object LeaveRoomMessage : RoomWireMessage()

    @Serializable
    data object HeartbeatPing : RoomWireMessage()

    @Serializable
    data object HeartbeatPong : RoomWireMessage()
}
