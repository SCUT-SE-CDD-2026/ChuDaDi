package com.example.chudadi.network.room

interface RoomClientConnection {
    suspend fun awaitJoinAccepted(
        playerName: String,
        avatarResId: Int?,
        resumeParticipantId: String? = null,
    ): RoomWireMessage.JoinRoomAccepted

    fun closeNow()
}
