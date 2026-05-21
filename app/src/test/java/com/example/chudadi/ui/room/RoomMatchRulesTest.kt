package com.example.chudadi.ui.room

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomMatchRulesTest {

    @Test
    fun finishedMatchStatus_keepsHostReadyAndDisconnectedMemberDisconnected() {
        assertEquals(
            MemberConnectionStatus.READY,
            RoomMatchRules.finishedMatchStatus(
                occupantType = SlotOccupantType.HUMAN_HOST,
                currentStatus = MemberConnectionStatus.READY,
            ),
        )
        assertEquals(
            MemberConnectionStatus.DISCONNECTED,
            RoomMatchRules.finishedMatchStatus(
                occupantType = SlotOccupantType.HUMAN_MEMBER,
                currentStatus = MemberConnectionStatus.DISCONNECTED,
            ),
        )
        assertEquals(
            MemberConnectionStatus.NOT_READY,
            RoomMatchRules.finishedMatchStatus(
                occupantType = SlotOccupantType.HUMAN_MEMBER,
                currentStatus = MemberConnectionStatus.READY,
            ),
        )
    }

    @Test
    fun participantReconnectStatus_returnsNotReadyAfterFinishedMatch() {
        assertEquals(
            MemberConnectionStatus.NOT_READY,
            RoomMatchRules.participantReconnectStatus(
                occupantType = SlotOccupantType.HUMAN_MEMBER,
                isFinishedMatch = true,
                hasActiveMatchSeatAssignments = false,
            ),
        )
        assertEquals(
            MemberConnectionStatus.CONNECTED,
            RoomMatchRules.participantReconnectStatus(
                occupantType = SlotOccupantType.HUMAN_MEMBER,
                isFinishedMatch = false,
                hasActiveMatchSeatAssignments = true,
            ),
        )
    }

    @Test
    fun canStart_requiresAllSeatsFilledAndReady() {
        val readySlots = listOf(
            SlotState(
                slotIndex = 0,
                occupantType = SlotOccupantType.HUMAN_HOST,
                connectionStatus = MemberConnectionStatus.READY,
            ),
            SlotState(
                slotIndex = 1,
                occupantType = SlotOccupantType.HUMAN_MEMBER,
                connectionStatus = MemberConnectionStatus.READY,
            ),
            SlotState(
                slotIndex = 2,
                occupantType = SlotOccupantType.AI,
                connectionStatus = MemberConnectionStatus.READY,
            ),
            SlotState(
                slotIndex = 3,
                occupantType = SlotOccupantType.AI,
                connectionStatus = MemberConnectionStatus.READY,
            ),
        )
        val disconnectedSlots = readySlots.mapIndexed { index, slot ->
            if (index == 1) slot.copy(connectionStatus = MemberConnectionStatus.DISCONNECTED) else slot
        }

        assertTrue(RoomMatchRules.canStart(readySlots))
        assertFalse(RoomMatchRules.canStart(disconnectedSlots))
    }
}
