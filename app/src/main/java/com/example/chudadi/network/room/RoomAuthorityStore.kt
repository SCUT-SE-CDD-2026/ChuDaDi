@file:Suppress("TooManyFunctions")

package com.example.chudadi.network.room

import com.example.chudadi.R
import com.example.chudadi.model.game.entity.MatchPhase
import com.example.chudadi.model.game.entity.RoundScore
import com.example.chudadi.model.game.entity.SeatControllerType
import com.example.chudadi.ui.room.RoomAiDifficulty
import com.example.chudadi.ui.room.GameRuleDisplay
import com.example.chudadi.ui.room.MemberConnectionStatus
import com.example.chudadi.ui.room.RoomMatchRules
import com.example.chudadi.ui.room.SlotOccupantType
import com.example.chudadi.ui.room.SlotState

data class ParticipantRecord(
    val participantId: String,
    val occupantType: SlotOccupantType,
    val displayName: String,
    val avatarResId: Int?,
    val connectionStatus: MemberConnectionStatus?,
    val aiDifficulty: RoomAiDifficulty? = null,
    val cumulativeScore: Int = 0,
)

data class RoomAuthorityState(
    val roomName: String = "",
    val hostDeviceName: String = "",
    val currentRule: GameRuleDisplay = GameRuleDisplay.SOUTHERN,
    val bluetoothVisible: Boolean = false,
    val participants: Map<String, ParticipantRecord> = emptyMap(),
    val slotAssignments: Map<Int, String?> = emptySlotAssignments(),
)

internal const val DEFAULT_SLOT_COUNT = 4
internal const val HOST_PARTICIPANT_ID = "host"

internal fun emptySlotAssignments(): Map<Int, String?> {
    return (0 until DEFAULT_SLOT_COUNT).associate { it to null }
}

class RoomAuthorityStore {
    var state: RoomAuthorityState = RoomAuthorityState()
        private set

    fun reset() {
        state = RoomAuthorityState()
    }

    fun createHostRoom(
        playerName: String,
        avatarResId: Int?,
        hostDeviceName: String,
        bluetoothVisible: Boolean = true,
    ) {
        state = RoomAuthorityState(
            roomName = "$playerName 的房间",
            hostDeviceName = hostDeviceName,
            currentRule = GameRuleDisplay.SOUTHERN,
            bluetoothVisible = bluetoothVisible,
            participants = mapOf(
                HOST_PARTICIPANT_ID to ParticipantRecord(
                    participantId = HOST_PARTICIPANT_ID,
                    occupantType = SlotOccupantType.HUMAN_HOST,
                    displayName = playerName,
                    avatarResId = avatarResId ?: R.drawable.avatar,
                    connectionStatus = MemberConnectionStatus.READY,
                ),
            ),
            slotAssignments = (0 until SLOT_COUNT).associateWith { slotIndex ->
                if (slotIndex == 0) HOST_PARTICIPANT_ID else null
            },
        )
    }

    fun setState(newState: RoomAuthorityState) {
        state = newState
    }

    fun update(transform: (RoomAuthorityState) -> RoomAuthorityState) {
        state = transform(state)
    }

    fun updateParticipant(participantId: String, transform: (ParticipantRecord) -> ParticipantRecord) {
        val participant = state.participants[participantId] ?: return
        state = state.copy(
            participants = state.participants + (participantId to transform(participant)),
        )
    }

    fun occupantAt(slotIndex: Int): String? = state.slotAssignments[slotIndex]

    fun slotIndexOfParticipant(participantId: String): Int? {
        return state.slotAssignments.entries.firstOrNull { it.value == participantId }?.key
    }

    fun firstAvailableRemoteSlotIndex(): Int? {
        return (1 until SLOT_COUNT).firstOrNull { state.slotAssignments[it] == null }
    }

    fun normalizedSlotAssignments(participantId: String, keepSlotIndex: Int): Map<Int, String?> {
        return state.slotAssignments.toMutableMap().apply {
            entries.forEach { entry ->
                if (entry.key != keepSlotIndex && entry.value == participantId) {
                    this[entry.key] = null
                }
            }
            this[keepSlotIndex] = participantId
        }
    }

    fun nextAiParticipantId(): String {
        var index = 1
        while (state.participants.containsKey("ai-$index")) {
            index += 1
        }
        return "ai-$index"
    }

    fun nextAiDisplayName(difficulty: RoomAiDifficulty): String {
        val usedNumbers = state.participants.values
            .filter { it.occupantType == SlotOccupantType.AI }
            .mapNotNull { it.displayName.substringAfterLast(' ').toIntOrNull() }
            .toSet()
        val nextNumber = generateSequence(1) { it + 1 }.first { it !in usedNumbers }
        return "AI(${difficulty.label}) $nextNumber"
    }

    fun buildSlotStates(localParticipantId: String): List<SlotState> {
        return (0 until SLOT_COUNT).map { slotIndex ->
            val participantId = state.slotAssignments[slotIndex]
            val participant = participantId?.let(state.participants::get)
            if (participant == null) {
                SlotState(slotIndex = slotIndex, seatId = slotIndex)
            } else {
                SlotState(
                    slotIndex = slotIndex,
                    seatId = slotIndex,
                    participantId = participant.participantId,
                    occupantType = participant.occupantType,
                    displayName = participant.displayName,
                    avatarResId = participant.avatarResId,
                    connectionStatus = participant.connectionStatus,
                    aiDifficulty = participant.aiDifficulty,
                    cumulativeScore = participant.cumulativeScore,
                    isLocalPlayer = participant.participantId == localParticipantId,
                )
            }
        }
    }

    fun snapshotOfCurrentRoom(connectionHint: String, localParticipantId: String): RemoteRoomSnapshot {
        return RemoteRoomSnapshot(
            roomName = state.roomName,
            hostDeviceName = state.hostDeviceName,
            currentRule = state.currentRule.name,
            bluetoothVisible = state.bluetoothVisible,
            connectionHint = connectionHint,
            slots = buildSlotStates(localParticipantId = localParticipantId).map { slot ->
                RemoteSlotSnapshot(
                    slotIndex = slot.slotIndex,
                    seatId = slot.seatId,
                    participantId = slot.participantId,
                    occupantType = slot.occupantType?.name,
                    displayName = slot.displayName,
                    avatarResId = slot.avatarResId,
                    connectionStatus = slot.connectionStatus?.name,
                    aiDifficulty = slot.aiDifficulty?.name,
                    cumulativeScore = slot.cumulativeScore,
                )
            },
        )
    }

    fun applyRemoteSnapshot(snapshot: RemoteRoomSnapshot) {
        setState(snapshot.toAuthorityState())
    }

    fun buildSeatConfigs(): List<Triple<Int, String, SeatControllerType>> {
        return (0 until SLOT_COUNT).map { slotIndex ->
            val participantId = state.slotAssignments[slotIndex]
            val participant = participantId?.let(state.participants::get)
            val controllerType = if (participant?.occupantType == SlotOccupantType.AI) {
                SeatControllerType.RULE_BASED_AI
            } else {
                SeatControllerType.HUMAN
            }
            val displayName = participant?.displayName ?: "玩家${slotIndex + 1}"
            Triple(slotIndex, displayName, controllerType)
        }
    }

    fun applyRoundScores(roundScores: List<RoundScore>) {
        val scoreMap = roundScores.associateBy { it.seatId }
        state = state.copy(
            participants = state.participants.mapValues { (_, participant) ->
                val slotIndex = slotIndexOfParticipant(participant.participantId) ?: return@mapValues participant
                val roundScore = scoreMap[slotIndex]?.roundScore ?: 0
                participant.copy(cumulativeScore = participant.cumulativeScore + roundScore)
            },
        )
    }

    fun resetReadyStatesAfterMatchFinished() {
        state = state.copy(
            participants = state.participants.mapValues { (_, participant) ->
                participant.copy(connectionStatus = finishedMatchStatus(participant))
            },
        )
    }

    fun participantReconnectStatus(
        participantId: String,
        matchPhase: MatchPhase?,
        hasActiveMatchSeatAssignments: Boolean,
    ): MemberConnectionStatus {
        val participant = state.participants[participantId]
        return RoomMatchRules.participantReconnectStatus(
            occupantType = participant?.occupantType,
            isFinishedMatch = matchPhase == MatchPhase.FINISHED,
            hasActiveMatchSeatAssignments = hasActiveMatchSeatAssignments,
        )
    }

    fun canStart(localParticipantId: String): Boolean {
        return RoomMatchRules.canStart(buildSlotStates(localParticipantId = localParticipantId))
    }

    private fun finishedMatchStatus(participant: ParticipantRecord): MemberConnectionStatus? {
        return RoomMatchRules.finishedMatchStatus(
            occupantType = participant.occupantType,
            currentStatus = participant.connectionStatus,
        )
    }

    private fun RemoteRoomSnapshot.toAuthorityState(): RoomAuthorityState {
        val participants = mutableMapOf<String, ParticipantRecord>()
        val slotAssignments = emptySlotAssignments().toMutableMap()
        slots.forEach { slot ->
            val participantId = slot.participantId ?: return@forEach
            participants[participantId] = ParticipantRecord(
                participantId = participantId,
                occupantType = slot.occupantType?.let(SlotOccupantType::valueOf) ?: return@forEach,
                displayName = slot.displayName,
                avatarResId = slot.avatarResId,
                connectionStatus = slot.connectionStatus?.let(MemberConnectionStatus::valueOf),
                aiDifficulty = slot.aiDifficulty?.let(RoomAiDifficulty::valueOf),
                cumulativeScore = slot.cumulativeScore,
            )
            slotAssignments[slot.slotIndex] = participantId
        }
        return RoomAuthorityState(
            roomName = roomName,
            hostDeviceName = hostDeviceName,
            currentRule = GameRuleDisplay.valueOf(currentRule),
            bluetoothVisible = bluetoothVisible,
            participants = participants,
            slotAssignments = slotAssignments,
        )
    }

    companion object {
        const val SLOT_COUNT = 4
    }
}
