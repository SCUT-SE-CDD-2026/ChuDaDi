@file:Suppress(
    "TooManyFunctions",
    "LongParameterList",
)

package com.example.chudadi.network.room

import com.example.chudadi.R
import com.example.chudadi.data.repository.ReconnectSessionRepository
import com.example.chudadi.model.game.entity.MatchPhase
import com.example.chudadi.network.bluetooth.transport.RoomTransport
import com.example.chudadi.ui.room.MemberConnectionStatus
import com.example.chudadi.ui.room.SlotOccupantType
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val EXPIRED_RECONNECT_SESSION_REASON = "原房间成员已失效，请重新搜索房间"

interface RoomMembershipPort {
    fun snapshotOfCurrentRoom(): RemoteRoomSnapshot

    fun publishConnectionHint(message: String)

    fun publishRoomClosed(message: String)

    fun resetRoomUiState()

    fun broadcastSnapshot()

    fun updateAllMatchSnapshots(lastActionMessage: String?)
}

class RoomMembershipCoordinator(
    private val scope: CoroutineScope,
    private val authorityStore: RoomAuthorityStore,
    private val roomTransport: RoomTransport,
    private val matchCoordinator: NetworkMatchCoordinator,
    private val reconnectSessionRepository: ReconnectSessionRepository,
    private val port: RoomMembershipPort,
) {
    private val membershipMutex = Mutex()
    private val pendingParticipants = mutableMapOf<String, PendingParticipant>()
    private val pendingSlotReservations = mutableMapOf<Int, String>()
    private val pendingConnectionParticipants = mutableMapOf<String, String>()
    private val pendingReconnectParticipants = mutableSetOf<String>()

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
        val participantId = resolveJoinParticipantId(connection, request) ?: return
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

    private suspend fun resolveJoinParticipantId(
        connection: RoomSocketConnection,
        request: RoomWireMessage.JoinRoomRequest,
    ): String? {
        val resumeParticipantId = request.resumeParticipantId
        if (resumeParticipantId == null) {
            return participantIdForConnection(connection)
        }
        if (authorityStore.state.participants.containsKey(resumeParticipantId)) {
            return resumeParticipantId
        }
        rejectJoin(
            connection = connection,
            reason = EXPIRED_RECONNECT_SESSION_REASON,
            targetId = "expired reconnect participantId=$resumeParticipantId",
        )
        return null
    }

    private suspend fun rejectJoin(
        connection: RoomSocketConnection,
        reason: String,
        targetId: String,
    ) {
        val rejectSendResult = connection.sendSafely(
            RoomWireMessage.JoinRoomRejected(reason),
            targetId = targetId,
        )
        if (rejectSendResult.isFailure) {
            port.publishConnectionHint(
                "入房拒绝发送失败：${rejectSendResult.exceptionOrNull()?.message ?: "蓝牙写入失败"}",
            )
        }
        connection.close()
    }

    fun handleRemoteLeave(participantId: String) {
        roomTransport.disconnectParticipant(participantId)
        removeParticipantFromRoom(participantId, clearSlot = true, reason = "已离开房间")
    }

    fun markParticipantDisconnected(participantId: String) {
        authorityStore.updateParticipant(participantId) {
            it.copy(connectionStatus = MemberConnectionStatus.DISCONNECTED)
        }
        matchCoordinator.onParticipantDisconnected(participantId, authorityStore)

        val displayName = authorityStore.state.participants[participantId]?.displayName.orEmpty()
        port.publishConnectionHint("$displayName 连接中断")
        port.broadcastSnapshot()

        if (matchCoordinator.currentMatchPhase() != MatchPhase.FINISHED &&
            matchCoordinator.hasActiveMatchSeatAssignments()
        ) {
            port.updateAllMatchSnapshots("$displayName 已掉线")
        }
    }

    fun handleRemovedFromRoom(reason: String) {
        closeRoomSession(reason = reason, clearReconnectSession = true)
    }

    fun handleRoomClosedByHost(reason: String) {
        closeRoomSession(reason = reason, clearReconnectSession = true)
    }

    fun handleHostConnectionLost(message: String) {
        closeRoomSession(reason = message, clearReconnectSession = false)
    }

    private suspend fun handleReconnect(
        connection: RoomSocketConnection,
        request: RoomWireMessage.JoinRoomRequest,
        participantId: String,
        existingSlotIndex: Int,
    ) {
        when (val preparation = preparePendingReconnect(connection, request, participantId, existingSlotIndex)) {
            is ReconnectPreparation.Accept -> completePendingReconnect(preparation.pending)
            is ReconnectPreparation.Reject -> {
                rejectJoin(
                    connection = connection,
                    reason = preparation.reason,
                    targetId = "reconnect rejected participantId=$participantId",
                )
            }
        }
    }

    private suspend fun preparePendingReconnect(
        connection: RoomSocketConnection,
        request: RoomWireMessage.JoinRoomRequest,
        participantId: String,
        existingSlotIndex: Int,
    ): ReconnectPreparation {
        return membershipMutex.withLock {
            if (!isHostRoomOpen()) {
                return@withLock ReconnectPreparation.Reject("房间已关闭，无法重新连接")
            }
            if (!authorityStore.state.participants.containsKey(participantId) ||
                authorityStore.slotIndexOfParticipant(participantId) != existingSlotIndex
            ) {
                return@withLock ReconnectPreparation.Reject("原房间成员已失效")
            }
            val connectionKey = connectionKey(connection)
            if (pendingReconnectParticipants.contains(participantId) ||
                pendingConnectionParticipants.containsKey(connectionKey)
            ) {
                return@withLock ReconnectPreparation.Reject("重连请求正在处理中，请稍候")
            }
            val pending = PendingReconnect(
                participantId = participantId,
                slotIndex = existingSlotIndex,
                connection = connection,
                connectionKey = connectionKey,
                playerName = request.playerName,
                avatarResId = request.avatarResId ?: R.drawable.avatar,
                connectionStatus = authorityStore.participantReconnectStatus(
                    participantId = participantId,
                    matchPhase = matchCoordinator.currentMatchPhase(),
                    hasActiveMatchSeatAssignments = matchCoordinator.hasActiveMatchSeatAssignments(),
                ),
            )
            pendingReconnectParticipants += participantId
            pendingConnectionParticipants[connectionKey] = participantId
            ReconnectPreparation.Accept(pending)
        }
    }

    private suspend fun completePendingReconnect(pending: PendingReconnect) {
        val acceptedSendResult = pending.connection.sendSafely(
            RoomWireMessage.JoinRoomAccepted(
                localParticipantId = pending.participantId,
                snapshot = snapshotWithReconnectedParticipant(pending),
            ),
            targetId = "join accepted participantId=${pending.participantId}",
        )
        if (acceptedSendResult.isFailure) {
            rollbackPendingReconnect(pending)
            port.publishConnectionHint("入房接受发送失败：${acceptedSendResult.exceptionOrNull()?.message ?: "蓝牙写入失败"}")
            return
        }

        val replaceResult = runCatching {
            roomTransport.replaceHostConnection(participantId = pending.participantId, connection = pending.connection)
        }
        replaceResult.exceptionOrNull()?.let { error ->
            if (error is CancellationException) throw error
            rollbackPendingReconnect(pending)
            port.publishConnectionHint("重连读循环启动失败：${error.message ?: "蓝牙连接失败"}")
            return
        }

        val commitResult = commitPendingReconnect(pending)
        if (commitResult.isFailure) {
            rollbackPendingReconnect(pending, disconnectAttachedConnection = true)
            port.publishConnectionHint(
                "重连提交失败：${commitResult.exceptionOrNull()?.message ?: "房间状态已变化"}",
            )
            return
        }

        matchCoordinator.onParticipantReconnected(pending.participantId, authorityStore)
        port.publishConnectionHint("${pending.playerName} 已重新连接")
        matchCoordinator.sendMatchRecoveryMessage(
            participantId = pending.participantId,
            authorityStore = authorityStore,
            sendToParticipant = roomTransport::sendToParticipant,
        )
        port.broadcastSnapshot()
    }

    private suspend fun commitPendingReconnect(pending: PendingReconnect): Result<Unit> {
        return membershipMutex.withLock {
            if (!pendingReconnectParticipants.contains(pending.participantId)) {
                return@withLock Result.failure(IOException("重连请求已失效"))
            }
            if (!isHostRoomOpen()) {
                return@withLock Result.failure(IOException("房间已关闭"))
            }
            if (authorityStore.slotIndexOfParticipant(pending.participantId) != pending.slotIndex) {
                return@withLock Result.failure(IOException("原座位已不可用"))
            }
            authorityStore.update {
                it.copy(
                    slotAssignments = authorityStore.normalizedSlotAssignments(
                        participantId = pending.participantId,
                        keepSlotIndex = pending.slotIndex,
                    ),
                )
            }
            authorityStore.updateParticipant(pending.participantId) {
                it.copy(
                    displayName = pending.playerName,
                    avatarResId = pending.avatarResId,
                    connectionStatus = pending.connectionStatus,
                )
            }
            clearPendingReconnect(pending)
            Result.success(Unit)
        }
    }

    private suspend fun handleNewJoin(
        connection: RoomSocketConnection,
        request: RoomWireMessage.JoinRoomRequest,
        participantId: String,
    ) {
        when (val preparation = preparePendingParticipant(connection, request, participantId)) {
            is JoinPreparation.Accept -> completePendingJoin(preparation.pending)
            is JoinPreparation.Reject -> {
                rejectJoin(
                    connection = connection,
                    reason = preparation.reason,
                    targetId = "join rejected participantId=$participantId",
                )
            }
        }
    }

    private suspend fun preparePendingParticipant(
        connection: RoomSocketConnection,
        request: RoomWireMessage.JoinRoomRequest,
        participantId: String,
    ): JoinPreparation {
        return membershipMutex.withLock {
            if (!isHostRoomOpen()) {
                return@withLock JoinPreparation.Reject("房间已关闭，无法加入")
            }
            if (authorityStore.state.participants.containsKey(participantId)) {
                return@withLock JoinPreparation.Reject("该连接已在房间中")
            }
            val connectionKey = connectionKey(connection)
            if (pendingParticipants.containsKey(participantId) ||
                pendingConnectionParticipants.containsKey(connectionKey)
            ) {
                return@withLock JoinPreparation.Reject("入房请求正在处理中，请稍候")
            }
            val targetSlotIndex = firstAvailableRemoteSlotIndexExcludingPending()
                ?: return@withLock JoinPreparation.Reject("房间已满，请等待房主调整座位")
            val pending = PendingParticipant(
                participantId = participantId,
                slotIndex = targetSlotIndex,
                connection = connection,
                connectionKey = connectionKey,
                playerName = request.playerName,
                avatarResId = request.avatarResId ?: R.drawable.avatar,
            )
            pendingParticipants[participantId] = pending
            pendingSlotReservations[targetSlotIndex] = participantId
            pendingConnectionParticipants[connectionKey] = participantId
            JoinPreparation.Accept(pending)
        }
    }

    private suspend fun completePendingJoin(pending: PendingParticipant) {
        val acceptedSendResult = pending.connection.sendSafely(
            RoomWireMessage.JoinRoomAccepted(
                localParticipantId = pending.participantId,
                snapshot = snapshotWithPendingParticipant(pending),
            ),
            targetId = "join accepted participantId=${pending.participantId}",
        )
        if (acceptedSendResult.isFailure) {
            rollbackPendingParticipant(pending, disconnectAttachedConnection = false)
            port.publishConnectionHint(
                "入房接受发送失败：${acceptedSendResult.exceptionOrNull()?.message ?: "蓝牙写入失败"}",
            )
            return
        }

        val attachResult = runCatching {
            roomTransport.attachHostReadLoop(participantId = pending.participantId, connection = pending.connection)
        }
        attachResult.exceptionOrNull()?.let { error ->
            if (error is CancellationException) throw error
            rollbackPendingParticipant(pending, disconnectAttachedConnection = true)
            port.publishConnectionHint("入房读循环启动失败：${error.message ?: "蓝牙连接失败"}")
            return
        }

        val commitResult = commitPendingParticipant(pending)
        if (commitResult.isFailure) {
            rollbackPendingParticipant(pending, disconnectAttachedConnection = true)
            port.publishConnectionHint(
                "入房提交失败：${commitResult.exceptionOrNull()?.message ?: "房间状态已变化"}",
            )
            return
        }

        port.publishConnectionHint("${pending.playerName} 已加入房间")
        port.broadcastSnapshot()
    }

    private suspend fun commitPendingParticipant(pending: PendingParticipant): Result<Unit> {
        return membershipMutex.withLock {
            if (pendingParticipants[pending.participantId] != pending) {
                return@withLock Result.failure(IOException("入房请求已失效"))
            }
            if (!isHostRoomOpen()) {
                return@withLock Result.failure(IOException("房间已关闭"))
            }
            if (pendingSlotReservations[pending.slotIndex] != pending.participantId ||
                authorityStore.state.slotAssignments[pending.slotIndex] != null
            ) {
                return@withLock Result.failure(IOException("座位已不可用"))
            }

            authorityStore.update { state ->
                state.copy(
                    participants = state.participants + (
                        pending.participantId to ParticipantRecord(
                            participantId = pending.participantId,
                            occupantType = SlotOccupantType.HUMAN_MEMBER,
                            displayName = pending.playerName,
                            avatarResId = pending.avatarResId,
                            connectionStatus = MemberConnectionStatus.CONNECTED,
                        )
                    ),
                    slotAssignments = state.slotAssignments + (pending.slotIndex to pending.participantId),
                )
            }
            clearPendingParticipant(pending)
            Result.success(Unit)
        }
    }

    private suspend fun rollbackPendingParticipant(
        pending: PendingParticipant,
        disconnectAttachedConnection: Boolean,
    ) {
        val removed = membershipMutex.withLock {
            if (pendingParticipants[pending.participantId] != pending) {
                return@withLock false
            }
            clearPendingParticipant(pending)
            true
        }
        if (!removed) return
        if (disconnectAttachedConnection) {
            roomTransport.disconnectParticipant(pending.participantId)
        }
        pending.connection.close()
    }

    private suspend fun rollbackPendingReconnect(
        pending: PendingReconnect,
        disconnectAttachedConnection: Boolean = false,
    ) {
        val removed = membershipMutex.withLock {
            if (!pendingReconnectParticipants.contains(pending.participantId)) {
                return@withLock false
            }
            clearPendingReconnect(pending)
            true
        }
        if (!removed) return
        if (disconnectAttachedConnection) {
            roomTransport.disconnectParticipant(pending.participantId)
        }
        pending.connection.close()
    }

    private fun clearPendingParticipant(pending: PendingParticipant) {
        pendingParticipants.remove(pending.participantId)
        if (pendingSlotReservations[pending.slotIndex] == pending.participantId) {
            pendingSlotReservations.remove(pending.slotIndex)
        }
        if (pendingConnectionParticipants[pending.connectionKey] == pending.participantId) {
            pendingConnectionParticipants.remove(pending.connectionKey)
        }
    }

    private fun clearPendingReconnect(pending: PendingReconnect) {
        pendingReconnectParticipants.remove(pending.participantId)
        if (pendingConnectionParticipants[pending.connectionKey] == pending.participantId) {
            pendingConnectionParticipants.remove(pending.connectionKey)
        }
    }

    private fun firstAvailableRemoteSlotIndexExcludingPending(): Int? {
        return (1 until RoomAuthorityStore.SLOT_COUNT).firstOrNull { slotIndex ->
            authorityStore.state.slotAssignments[slotIndex] == null &&
                !pendingSlotReservations.containsKey(slotIndex)
        }
    }

    private fun isHostRoomOpen(): Boolean {
        return authorityStore.state.bluetoothVisible &&
            authorityStore.state.participants.containsKey(HOST_PARTICIPANT_ID)
    }

    private fun snapshotWithPendingParticipant(pending: PendingParticipant): RemoteRoomSnapshot {
        val pendingSlot = RemoteSlotSnapshot(
            slotIndex = pending.slotIndex,
            seatId = pending.slotIndex,
            participantId = pending.participantId,
            occupantType = SlotOccupantType.HUMAN_MEMBER.name,
            displayName = pending.playerName,
            avatarResId = pending.avatarResId,
            connectionStatus = MemberConnectionStatus.CONNECTED.name,
        )
        val snapshot = port.snapshotOfCurrentRoom()
        val slots = if (snapshot.slots.any { it.slotIndex == pending.slotIndex }) {
            snapshot.slots.map { slot ->
                if (slot.slotIndex == pending.slotIndex) pendingSlot else slot
            }
        } else {
            snapshot.slots + pendingSlot
        }
        return snapshot.copy(slots = slots.sortedBy { it.slotIndex })
    }

    private fun snapshotWithReconnectedParticipant(pending: PendingReconnect): RemoteRoomSnapshot {
        val currentParticipant = authorityStore.state.participants[pending.participantId]
        val reconnectedSlot = RemoteSlotSnapshot(
            slotIndex = pending.slotIndex,
            seatId = pending.slotIndex,
            participantId = pending.participantId,
            occupantType = currentParticipant?.occupantType?.name,
            displayName = pending.playerName,
            avatarResId = pending.avatarResId,
            connectionStatus = pending.connectionStatus.name,
            aiDifficulty = currentParticipant?.aiDifficulty?.name,
            cumulativeScore = currentParticipant?.cumulativeScore ?: 0,
        )
        val snapshot = port.snapshotOfCurrentRoom()
        val slots = snapshot.slots.map { slot ->
            if (slot.slotIndex == pending.slotIndex) reconnectedSlot else slot
        }
        return snapshot.copy(slots = slots.sortedBy { it.slotIndex })
    }

    private fun closeRoomSession(
        reason: String,
        clearReconnectSession: Boolean,
    ) {
        if (clearReconnectSession) {
            scope.launch { reconnectSessionRepository.clearSession() }
        }
        port.publishRoomClosed(reason)
        matchCoordinator.reset()
        authorityStore.reset()
        port.resetRoomUiState()
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
        port.publishConnectionHint("$displayName $reason")
        port.broadcastSnapshot()
    }

    private fun participantIdForConnection(connection: RoomSocketConnection): String {
        return connection.remoteAddress.ifBlank { "member-${System.identityHashCode(connection)}" }
    }

    private fun connectionKey(connection: RoomSocketConnection): String {
        return connection.remoteAddress.ifBlank { "connection-${System.identityHashCode(connection)}" }
    }

    private data class PendingParticipant(
        val participantId: String,
        val slotIndex: Int,
        val connection: RoomSocketConnection,
        val connectionKey: String,
        val playerName: String,
        val avatarResId: Int,
    )

    private data class PendingReconnect(
        val participantId: String,
        val slotIndex: Int,
        val connection: RoomSocketConnection,
        val connectionKey: String,
        val playerName: String,
        val avatarResId: Int,
        val connectionStatus: MemberConnectionStatus,
    )

    private sealed interface JoinPreparation {
        data class Accept(val pending: PendingParticipant) : JoinPreparation
        data class Reject(val reason: String) : JoinPreparation
    }

    private sealed interface ReconnectPreparation {
        data class Accept(val pending: PendingReconnect) : ReconnectPreparation
        data class Reject(val reason: String) : ReconnectPreparation
    }
}
