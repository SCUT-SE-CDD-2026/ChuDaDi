package com.example.chudadi.ui.room

object RoomMatchRules {
    fun finishedMatchStatus(
        occupantType: SlotOccupantType,
        currentStatus: MemberConnectionStatus?,
    ): MemberConnectionStatus {
        return when (occupantType) {
            SlotOccupantType.HUMAN_HOST -> MemberConnectionStatus.READY
            SlotOccupantType.AI -> MemberConnectionStatus.READY
            SlotOccupantType.HUMAN_MEMBER -> {
                if (currentStatus == MemberConnectionStatus.DISCONNECTED) {
                    MemberConnectionStatus.DISCONNECTED
                } else {
                    MemberConnectionStatus.NOT_READY
                }
            }
        }
    }

    fun participantReconnectStatus(
        occupantType: SlotOccupantType?,
        isFinishedMatch: Boolean,
        hasActiveMatchSeatAssignments: Boolean,
    ): MemberConnectionStatus {
        return when (occupantType) {
            SlotOccupantType.HUMAN_HOST -> MemberConnectionStatus.READY
            SlotOccupantType.AI -> MemberConnectionStatus.READY
            SlotOccupantType.HUMAN_MEMBER, null -> {
                if (isFinishedMatch || !hasActiveMatchSeatAssignments) {
                    MemberConnectionStatus.NOT_READY
                } else {
                    MemberConnectionStatus.CONNECTED
                }
            }
        }
    }

    fun canStart(slots: List<SlotState>): Boolean {
        val allFilled = slots.all { it.occupantType != null }
        val allReady = slots.all { it.connectionStatus == MemberConnectionStatus.READY }
        return allFilled && allReady
    }
}
