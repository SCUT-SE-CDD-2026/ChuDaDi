@file:Suppress(
    "TooManyFunctions",
    "LongParameterList",
)

package com.example.chudadi.network.room

import com.example.chudadi.R
import com.example.chudadi.data.repository.ReconnectSessionRepository
import com.example.chudadi.model.game.entity.MatchPhase
import com.example.chudadi.ui.room.MemberConnectionStatus
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class RoomMembershipCoordinator(
    private val scope: CoroutineScope,
    private val authorityStore: RoomAuthorityStore,
    private val socketManager: RoomSocketManager,
    private val matchCoordinator: NetworkMatchCoordinator,
    private val reconnectSessionRepository: ReconnectSessionRepository,
    private val snapshotProvider: () -> RemoteRoomSnapshot,
    private val publishUiState: (String) -> Unit,
    private val publishRoomClosed: (String) -> Unit,
    private val resetRoomUi: () -> Unit,
    private val broadcastSnapshot: () -> Unit,
    private val updateAllMatchSnapshots: (String?) -> Unit,
) {
    suspend fun handleIncomingConnection(connection: RoomSocketConnection) {
        try {
            when (val firstMessage = connection.read()) {
                is RoomWireMessage.JoinRoomRequest -> handleJoinRequest(connection, firstMessage)
                else -> connection.close()
            }
        } catch (_: IOException) {
            connection.close()
        }
    }

    suspend fun handleJoinRequest(
        connection: RoomSocketConnection,
        request: RoomWireMessage.JoinRoomRequest,
    ) {
        val participantId = request.resumeParticipantId
            ?.takeIf { authorityStore.state.participants.containsKey(it) }
            ?: participantIdForConnection(connection)
        val existingParticipant = authorityStore.state.participants[participantId]
        val existingSlotIndex = authorityStore.slotIndexOfParticipant(participantId)

        if (existingParticipant != null && existingSlotIndex != null) {
            handleReconnect(
                connection = connection,
                request = request,
                participantId = participantId,
                existingSlotIndex = existingSlotIndex,
            )
            return
        }

        handleNewJoin(
            connection = connection,
            request = request,
            participantId = participantId,
        )
    }

    fun handleRemoteLeave(participantId: String) {
        socketManager.disconnectParticipant(participantId)
        removeParticipantFromRoom(participantId, clearSlot = true, reason = "已离开房间")
    }

    fun markParticipantDisconnected(participantId: String) {
        authorityStore.updateParticipant(participantId) {
            it.copy(connectionStatus = MemberConnectionStatus.DISCONNECTED)
        }
        matchCoordinator.onParticipantDisconnected(participantId, authorityStore)

        val displayName = authorityStore.state.participants[participantId]?.displayName.orEmpty()
        publishUiState("$displayName 连接中断")
        broadcastSnapshot()

        if (matchCoordinator.currentMatchPhase() != MatchPhase.FINISHED &&
            matchCoordinator.hasActiveMatchSeatAssignments()
        ) {
            updateAllMatchSnapshots("$displayName 已掉线")
        }
    }

    fun handleRemovedFromRoom(reason: String) {
        scope.launch { reconnectSessionRepository.clearSession() }
        publishRoomClosed(reason)
        matchCoordinator.reset()
        authorityStore.reset()
        resetRoomUi()
    }

    fun handleRoomClosedByHost(reason: String) {
        scope.launch { reconnectSessionRepository.clearSession() }
        publishRoomClosed(reason)
        matchCoordinator.reset()
        authorityStore.reset()
        resetRoomUi()
    }

    fun handleHostConnectionLost(message: String) {
        publishRoomClosed(message)
        matchCoordinator.reset()
        authorityStore.reset()
        resetRoomUi()
    }

    private suspend fun handleReconnect(
        connection: RoomSocketConnection,
        request: RoomWireMessage.JoinRoomRequest,
        participantId: String,
        existingSlotIndex: Int,
    ) {
        socketManager.replaceHostConnection(participantId = participantId, connection = connection)
        authorityStore.update {
            it.copy(
                slotAssignments = authorityStore.normalizedSlotAssignments(
                    participantId = participantId,
                    keepSlotIndex = existingSlotIndex,
                ),
            )
        }
        authorityStore.updateParticipant(participantId) {
            it.copy(
                displayName = request.playerName,
                avatarResId = request.avatarResId ?: R.drawable.avatar,
                connectionStatus = authorityStore.participantReconnectStatus(
                    participantId = participantId,
                    matchPhase = matchCoordinator.currentMatchPhase(),
                    hasActiveMatchSeatAssignments = matchCoordinator.hasActiveMatchSeatAssignments(),
                ),
            )
        }
        matchCoordinator.onParticipantReconnected(participantId, authorityStore)
        publishUiState("${request.playerName} 已重新连接")

        connection.send(
            RoomWireMessage.JoinRoomAccepted(
                localParticipantId = participantId,
                snapshot = snapshotProvider(),
            ),
        )

        matchCoordinator.sendMatchRecoveryMessage(
            participantId = participantId,
            authorityStore = authorityStore,
            sendToParticipant = socketManager::sendToParticipant,
        )
        broadcastSnapshot()
    }

    private suspend fun handleNewJoin(
        connection: RoomSocketConnection,
        request: RoomWireMessage.JoinRoomRequest,
        participantId: String,
    ) {
        val targetSlotIndex = authorityStore.firstAvailableRemoteSlotIndex()
        if (targetSlotIndex == null) {
            connection.send(RoomWireMessage.JoinRoomRejected("房间已满，请等待房主调整座位"))
            connection.close()
            return
        }

        authorityStore.update { state ->
            state.copy(
                participants = state.participants + (
                    participantId to ParticipantRecord(
                        participantId = participantId,
                        occupantType = com.example.chudadi.ui.room.SlotOccupantType.HUMAN_MEMBER,
                        displayName = request.playerName,
                        avatarResId = request.avatarResId ?: R.drawable.avatar,
                        connectionStatus = MemberConnectionStatus.CONNECTED,
                    )
                ),
                slotAssignments = state.slotAssignments + (targetSlotIndex to participantId),
            )
        }

        socketManager.attachHostReadLoop(participantId = participantId, connection = connection)
        publishUiState("${request.playerName} 已加入房间")

        connection.send(
            RoomWireMessage.JoinRoomAccepted(
                localParticipantId = participantId,
                snapshot = snapshotProvider(),
            ),
        )
        broadcastSnapshot()
    }

    private fun removeParticipantFromRoom(participantId: String, clearSlot: Boolean, reason: String) {
        val slotIndex = authorityStore.slotIndexOfParticipant(participantId)
        val displayName = authorityStore.state.participants[participantId]?.displayName.orEmpty()
        authorityStore.update { state ->
            state.copy(
                participants = state.participants - participantId,
                slotAssignments = state.slotAssignments.toMutableMap().apply {
                    if (clearSlot && slotIndex != null) {
                        this[slotIndex] = null
                    }
                },
            )
        }
        publishUiState("$displayName $reason")
        broadcastSnapshot()
    }

    private fun participantIdForConnection(connection: RoomSocketConnection): String {
        return connection.remoteAddress.ifBlank { "member-${System.currentTimeMillis()}" }
    }
}
