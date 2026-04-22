@file:Suppress(
    "TooManyFunctions",
    "LongParameterList",
)

package com.example.chudadi.network.room

import com.example.chudadi.R
import com.example.chudadi.ui.room.AiDifficulty
import com.example.chudadi.ui.room.MemberConnectionStatus
import com.example.chudadi.ui.room.SlotOccupantType
import com.example.chudadi.ui.room.SwapRequest

interface RoomSeatPort {
    fun snapshotOfCurrentRoom(): RemoteRoomSnapshot

    fun publishConnectionHint(message: String)

    fun showHostSwapPrompt(request: SwapRequest)

    fun clearPendingSwapRequest()

    fun broadcastSnapshot()
}

class RoomSeatCoordinator(
    private val authorityStore: RoomAuthorityStore,
    private val socketManager: RoomSocketManager,
    private val localParticipantIdProvider: () -> String,
    private val port: RoomSeatPort,
) {
    fun handleAddAiToSlot(slotIndex: Int, difficulty: AiDifficulty) {
        if (authorityStore.occupantAt(slotIndex) != null) return
        val aiId = authorityStore.nextAiParticipantId()
        authorityStore.update {
            it.copy(
                participants = it.participants + (
                    aiId to ParticipantRecord(
                        participantId = aiId,
                        occupantType = SlotOccupantType.AI,
                        displayName = authorityStore.nextAiDisplayName(difficulty),
                        avatarResId = R.drawable.avatar,
                        connectionStatus = MemberConnectionStatus.READY,
                        aiDifficulty = difficulty,
                    )
                ),
                slotAssignments = it.slotAssignments + (slotIndex to aiId),
            )
        }
        port.publishConnectionHint("已更新房间成员")
        port.broadcastSnapshot()
    }

    fun setRemoteReady(participantId: String, ready: Boolean) {
        authorityStore.updateParticipant(participantId) {
            it.copy(
                connectionStatus = if (ready) MemberConnectionStatus.READY else MemberConnectionStatus.NOT_READY,
            )
        }
        val displayName = authorityStore.state.participants[participantId]?.displayName.orEmpty()
        port.publishConnectionHint("$displayName ${if (ready) "已准备" else "取消准备"}")
        port.broadcastSnapshot()
    }

    fun toggleReadyForParticipant(participantId: String) {
        val participant = authorityStore.state.participants[participantId] ?: return
        val nextStatus = if (participant.connectionStatus == MemberConnectionStatus.READY) {
            MemberConnectionStatus.NOT_READY
        } else {
            MemberConnectionStatus.READY
        }
        authorityStore.updateParticipant(participantId) {
            it.copy(connectionStatus = nextStatus)
        }
        port.publishConnectionHint("准备状态已更新")
        port.broadcastSnapshot()
    }

    fun handleHostSwapRequest(requesterParticipantId: String, targetSlotIndex: Int) {
        val requesterSlotIndex = authorityStore.slotIndexOfParticipant(requesterParticipantId)
        val requester = authorityStore.state.participants[requesterParticipantId]
        if (requesterSlotIndex == null || requester == null || requesterSlotIndex == targetSlotIndex) {
            return
        }

        val targetParticipantId = authorityStore.occupantAt(targetSlotIndex)
        if (targetParticipantId == null) {
            applySeatSwap(requesterSlotIndex, targetSlotIndex)
            return
        }

        val targetParticipant = authorityStore.state.participants[targetParticipantId] ?: return
        when (targetParticipant.occupantType) {
            SlotOccupantType.AI -> applySeatSwap(requesterSlotIndex, targetSlotIndex)
            SlotOccupantType.HUMAN_HOST -> {
                port.showHostSwapPrompt(
                    SwapRequest(
                        requesterSlotIndex = requesterSlotIndex,
                        targetSlotIndex = targetSlotIndex,
                        requesterName = requester.displayName,
                    ),
                )
                port.publishConnectionHint("${requester.displayName} 请求与房主换位")
            }

            SlotOccupantType.HUMAN_MEMBER -> {
                socketManager.sendToParticipant(
                    targetParticipantId,
                    RoomWireMessage.SwapSeatPromptMessage(
                        RemoteSwapRequest(
                            requesterSlotIndex = requesterSlotIndex,
                            targetSlotIndex = targetSlotIndex,
                            requesterName = requester.displayName,
                        ),
                    ),
                )
                port.publishConnectionHint("已向 ${targetParticipant.displayName} 发送换位请求")
            }
        }
    }

    fun handleRemoteSwapDecision(message: RoomWireMessage.SwapSeatDecisionMessage) {
        if (message.accepted) {
            applySeatSwap(message.requesterSlotIndex, message.targetSlotIndex)
            return
        }
        notifyRequesterSwapRejected(message.requesterSlotIndex)
        port.clearPendingSwapRequest()
        port.publishConnectionHint("换位请求被拒绝")
    }

    fun applySeatSwap(slotIndexA: Int, slotIndexB: Int) {
        val participantA = authorityStore.occupantAt(slotIndexA)
        val participantB = authorityStore.occupantAt(slotIndexB)
        authorityStore.update { state ->
            state.copy(
                slotAssignments = state.slotAssignments.toMutableMap().apply {
                    this[slotIndexA] = participantB
                    this[slotIndexB] = participantA
                },
            )
        }
        port.clearPendingSwapRequest()
        port.publishConnectionHint("已完成换位")
        port.broadcastSnapshot()
    }

    private fun notifyRequesterSwapRejected(requesterSlotIndex: Int) {
        val requesterParticipantId = authorityStore.occupantAt(requesterSlotIndex) ?: return
        if (requesterParticipantId == localParticipantIdProvider()) {
            port.publishConnectionHint("换位请求已被拒绝")
            return
        }
        socketManager.sendToParticipant(
            requesterParticipantId,
            RoomWireMessage.RoomSnapshotMessage(port.snapshotOfCurrentRoom()),
        )
    }
}
