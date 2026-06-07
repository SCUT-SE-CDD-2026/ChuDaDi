package com.example.chudadi.network.room

import com.example.chudadi.model.game.entity.SeatControllerType
import com.example.chudadi.ui.room.AIType
import com.example.chudadi.ui.room.MemberConnectionStatus
import com.example.chudadi.ui.room.RoomAiDifficulty
import com.example.chudadi.ui.room.SlotOccupantType
import org.junit.Assert.assertEquals
import org.junit.Test

class RoomAuthorityStoreAiMappingTest {

    @Test
    fun buildSeatConfigs_mapsV2AiToV2SeatControllerType() {
        val store = RoomAuthorityStore()
        store.setState(
            RoomAuthorityState(
                participants = mapOf(
                    "host" to ParticipantRecord(
                        participantId = "host",
                        occupantType = SlotOccupantType.HUMAN_HOST,
                        displayName = "Host",
                        avatarResId = null,
                        connectionStatus = MemberConnectionStatus.READY,
                    ),
                    "ai-v2" to ParticipantRecord(
                        participantId = "ai-v2",
                        occupantType = SlotOccupantType.AI,
                        displayName = "RL2-1",
                        avatarResId = null,
                        connectionStatus = MemberConnectionStatus.READY,
                        aiDifficulty = RoomAiDifficulty.ONNX_V2,
                        aiType = AIType.ONNX_RL_V2,
                    ),
                ),
                slotAssignments = mapOf(0 to "host", 1 to "ai-v2", 2 to null, 3 to null),
            ),
        )

        val configs = store.buildSeatConfigs()

        assertEquals(SeatControllerType.ONNX_RL_V2_AI, configs.first { it.first == 1 }.third)
    }

    @Test
    fun buildSeatConfigs_mapsV3AiToV3SeatControllerType() {
        val store = RoomAuthorityStore()
        store.setState(
            RoomAuthorityState(
                participants = mapOf(
                    "host" to ParticipantRecord(
                        participantId = "host",
                        occupantType = SlotOccupantType.HUMAN_HOST,
                        displayName = "Host",
                        avatarResId = null,
                        connectionStatus = MemberConnectionStatus.READY,
                    ),
                    "ai-v3" to ParticipantRecord(
                        participantId = "ai-v3",
                        occupantType = SlotOccupantType.AI,
                        displayName = "RL3-1",
                        avatarResId = null,
                        connectionStatus = MemberConnectionStatus.READY,
                        aiDifficulty = RoomAiDifficulty.ONNX_V3,
                        aiType = AIType.ONNX_RL_V3,
                    ),
                ),
                slotAssignments = mapOf(0 to "host", 1 to "ai-v3", 2 to null, 3 to null),
            ),
        )

        val configs = store.buildSeatConfigs()

        assertEquals(SeatControllerType.ONNX_RL_V3_AI, configs.first { it.first == 1 }.third)
    }
}
