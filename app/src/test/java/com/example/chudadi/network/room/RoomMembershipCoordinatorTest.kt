package com.example.chudadi.network.room

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import com.example.chudadi.data.repository.ReconnectSessionRepository
import com.example.chudadi.network.bluetooth.transport.BroadcastResult
import com.example.chudadi.network.bluetooth.transport.HostTransportConfig
import com.example.chudadi.network.bluetooth.transport.RoomTransport
import com.example.chudadi.network.bluetooth.transport.RoomTransportEvent
import com.example.chudadi.ui.room.MemberConnectionStatus
import com.example.chudadi.ui.room.SlotOccupantType
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.IOException
import java.io.OutputStream
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.atLeastOnce
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class RoomMembershipCoordinatorTest {
    @Test
    fun joinAcceptedSendFailureDoesNotCommitParticipant() = runTest {
        val store = hostAuthorityStore()
        val port = FakeMembershipPort(store)
        val transport = FakeRoomTransport()
        val coordinator = coordinator(store, transport, port)
        val output = ThrowingOutputStream(IOException("accepted write failed"))
        val connection = roomConnection(address = "00:11:22:33:44:01", output = output)

        coordinator.handleJoinRequest(connection, joinRequest("Player One"))

        assertNoParticipant(store, "Player One")
        assertTrue(store.state.slotAssignments.values.none { it == "00:11:22:33:44:01" })
        assertEquals(0, port.broadcastCount)
        assertTrue(port.hints.none { it.contains("Player One 已加入房间") })
        assertTrue(output.closeCalled)
        assertTrue(transport.attachedParticipants.isEmpty())
    }

    @Test
    fun joinAcceptedSendFailureReleasesSlot() = runTest {
        val store = hostAuthorityStore()
        val port = FakeMembershipPort(store)
        val transport = FakeRoomTransport()
        val coordinator = coordinator(store, transport, port)

        coordinator.handleJoinRequest(
            roomConnection(
                address = "00:11:22:33:44:01",
                output = ThrowingOutputStream(IOException("accepted write failed")),
            ),
            joinRequest("Player One"),
        )
        coordinator.handleJoinRequest(
            roomConnection(address = "00:11:22:33:44:02"),
            joinRequest("Player Two"),
        )

        assertNoParticipant(store, "Player One")
        val playerTwo = store.state.participants.values.single { it.displayName == "Player Two" }
        assertEquals(1, store.slotIndexOfParticipant(playerTwo.participantId))
    }

    @Test
    fun joinAcceptedSendFailureClearsConnection() = runTest {
        val store = hostAuthorityStore()
        val port = FakeMembershipPort(store)
        val transport = FakeRoomTransport()
        val coordinator = coordinator(store, transport, port)
        val socket = mock(BluetoothSocket::class.java)
        val device = mock(BluetoothDevice::class.java)
        val output = ThrowingOutputStream(IOException("accepted write failed"))
        `when`(device.address).thenReturn("00:11:22:33:44:01")
        `when`(socket.remoteDevice).thenReturn(device)
        `when`(socket.inputStream).thenReturn(ByteArrayInputStream(ByteArray(0)))
        `when`(socket.outputStream).thenReturn(output)
        val connection = RoomSocketConnection(socket = socket, frameCodec = RoomFrameCodec())

        coordinator.handleJoinRequest(connection, joinRequest("Player One"))

        verify(socket, atLeastOnce()).close()
        assertTrue(output.closeCalled)
        assertTrue(transport.disconnectedParticipants.isEmpty())
        assertNoParticipant(store, "Player One")
    }

    @Test
    fun successfulJoinCommitsAfterAcceptedSent() = runTest {
        val store = hostAuthorityStore()
        val port = FakeMembershipPort(store)
        val transport = FakeRoomTransport(
            onAttach = {
                assertNoParticipant(store, "Player One")
                assertTrue(port.hints.none { hint -> hint.contains("Player One 已加入房间") })
            },
        )
        val output = CloseAwareOutputStream(
            onFirstWrite = {
                assertNoParticipant(store, "Player One")
                assertTrue(port.hints.none { hint -> hint.contains("Player One 已加入房间") })
            },
        )
        val coordinator = coordinator(store, transport, port)

        coordinator.handleJoinRequest(
            roomConnection(address = "00:11:22:33:44:01", output = output),
            joinRequest("Player One"),
        )

        val participant = store.state.participants.values.single { it.displayName == "Player One" }
        assertEquals(SlotOccupantType.HUMAN_MEMBER, participant.occupantType)
        assertEquals(MemberConnectionStatus.CONNECTED, participant.connectionStatus)
        assertEquals(1, store.slotIndexOfParticipant(participant.participantId))
        assertEquals(listOf(participant.participantId), transport.attachedParticipants)
        assertEquals(1, port.broadcastCount)
        assertTrue(port.hints.last().contains("Player One 已加入房间"))

        val accepted = decodeSingleMessage(output) as RoomWireMessage.JoinRoomAccepted
        assertEquals(participant.participantId, accepted.localParticipantId)
        assertTrue(accepted.snapshot.slots.any { it.participantId == participant.participantId })
    }

    @Test
    fun roomClosedDuringJoinDoesNotCommitParticipant() = runTest {
        val store = hostAuthorityStore()
        val port = FakeMembershipPort(store)
        val transport = FakeRoomTransport()
        val output = CloseAwareOutputStream(onFirstWrite = { store.reset() })
        val coordinator = coordinator(store, transport, port)

        coordinator.handleJoinRequest(
            roomConnection(address = "00:11:22:33:44:01", output = output),
            joinRequest("Player One"),
        )

        assertNoParticipant(store, "Player One")
        assertEquals(0, port.broadcastCount)
        assertEquals(listOf("00:11:22:33:44:01"), transport.disconnectedParticipants)
        assertTrue(output.closeCalled)
        assertTrue(port.hints.last().contains("入房提交失败"))
    }

    @Test
    fun concurrentJoinDoesNotDuplicateSlotOrExceedCapacity() = runTest {
        val store = hostAuthorityStore()
        val port = FakeMembershipPort(store)
        val transport = FakeRoomTransport()
        val coordinator = coordinator(store, transport, port)
        val joins = (1..4).map { index ->
            async {
                coordinator.handleJoinRequest(
                    roomConnection(address = "00:11:22:33:44:0$index"),
                    joinRequest("Player $index"),
                )
            }
        }

        joins.forEach { it.await() }

        val remoteSlots = store.state.slotAssignments.filterKeys { it != 0 }.values.filterNotNull()
        assertEquals(remoteSlots.toSet().size, remoteSlots.size)
        assertEquals(3, remoteSlots.size)
        assertEquals(3, store.state.participants.values.count { it.occupantType == SlotOccupantType.HUMAN_MEMBER })
    }

    @Test
    fun joinRejectedSendFailureDoesNotCreateParticipant() = runTest {
        val store = hostAuthorityStore()
        fillRemoteSlots(store)
        val port = FakeMembershipPort(store)
        val transport = FakeRoomTransport()
        val coordinator = coordinator(store, transport, port)
        val output = ThrowingOutputStream(IOException("reject write failed"))

        coordinator.handleJoinRequest(
            roomConnection(address = "00:11:22:33:44:09", output = output),
            joinRequest("Late Player"),
        )

        assertNoParticipant(store, "Late Player")
        assertNull(store.slotIndexOfParticipant("00:11:22:33:44:09"))
        assertEquals(0, port.broadcastCount)
        assertTrue(port.hints.last().contains("入房拒绝发送失败"))
        assertTrue(output.closeCalled)
    }

    @Test
    fun expiredResumeParticipantIdIsRejectedWithoutCreatingNewParticipant() = runTest {
        val store = hostAuthorityStore()
        val port = FakeMembershipPort(store)
        val transport = FakeRoomTransport()
        val output = CloseAwareOutputStream()
        val coordinator = coordinator(store, transport, port)

        coordinator.handleJoinRequest(
            roomConnection(address = "00:11:22:33:44:99", output = output),
            joinRequest("Removed Member", resumeParticipantId = "removed-member"),
        )

        val rejected = decodeSingleMessage(output) as RoomWireMessage.JoinRoomRejected
        assertEquals("原房间成员已失效，请重新搜索房间", rejected.reason)
        assertNoParticipant(store, "Removed Member")
        assertFalse(store.state.participants.containsKey("removed-member"))
        assertFalse(store.state.participants.containsKey("00:11:22:33:44:99"))
        assertTrue(store.state.slotAssignments.values.none { it == "00:11:22:33:44:99" })
        assertEquals(0, port.broadcastCount)
        assertTrue(transport.attachedParticipants.isEmpty())
        assertTrue(output.closeCalled)
    }

    @Test
    fun attachReadLoopFailureDoesNotCommitParticipant() = runTest {
        val store = hostAuthorityStore()
        val port = FakeMembershipPort(store)
        val transport = FakeRoomTransport(
            attachAction = { throw IOException("attach failed") },
        )
        val output = CloseAwareOutputStream()
        val coordinator = coordinator(store, transport, port)

        coordinator.handleJoinRequest(
            roomConnection(address = "00:11:22:33:44:01", output = output),
            joinRequest("Player One"),
        )

        assertNoParticipant(store, "Player One")
        assertEquals(0, port.broadcastCount)
        assertEquals(listOf("00:11:22:33:44:01"), transport.disconnectedParticipants)
        assertTrue(output.closeCalled)
        assertTrue(port.hints.last().contains("入房读循环启动失败"))
    }

    @Test
    fun reconnectAcceptedSendFailureDoesNotCommitReconnectStateOrReplaceConnection() = runTest {
        val store = hostAuthorityStore()
        addDisconnectedMember(store)
        val port = FakeMembershipPort(store)
        val transport = FakeRoomTransport()
        val output = ThrowingOutputStream(IOException("accepted write failed"))
        val coordinator = coordinator(store, transport, port)

        coordinator.handleJoinRequest(
            roomConnection(address = "00:11:22:33:44:99", output = output),
            joinRequest("Member Reconnected", resumeParticipantId = "member-1"),
        )

        val participant = store.state.participants.getValue("member-1")
        assertEquals("Member", participant.displayName)
        assertEquals(MemberConnectionStatus.DISCONNECTED, participant.connectionStatus)
        assertEquals(1, store.slotIndexOfParticipant("member-1"))
        assertTrue(transport.replacedParticipants.isEmpty())
        assertTrue(transport.disconnectedParticipants.isEmpty())
        assertEquals(0, port.broadcastCount)
        assertTrue(port.hints.none { it.contains("已重新连接") })
        assertTrue(output.closeCalled)
    }

    @Test
    fun successfulReconnectCommitsAfterAcceptedSent() = runTest {
        val store = hostAuthorityStore()
        addDisconnectedMember(store)
        val port = FakeMembershipPort(store)
        val transport = FakeRoomTransport(
            onReplace = {
                val participant = store.state.participants.getValue("member-1")
                assertEquals("Member", participant.displayName)
                assertEquals(MemberConnectionStatus.DISCONNECTED, participant.connectionStatus)
                assertTrue(port.hints.none { hint -> hint.contains("已重新连接") })
            },
        )
        val output = CloseAwareOutputStream(
            onFirstWrite = {
                val participant = store.state.participants.getValue("member-1")
                assertEquals("Member", participant.displayName)
                assertEquals(MemberConnectionStatus.DISCONNECTED, participant.connectionStatus)
            },
        )
        val coordinator = coordinator(store, transport, port)

        coordinator.handleJoinRequest(
            roomConnection(address = "00:11:22:33:44:99", output = output),
            joinRequest("Member Reconnected", resumeParticipantId = "member-1"),
        )

        val participant = store.state.participants.getValue("member-1")
        assertEquals("Member Reconnected", participant.displayName)
        assertEquals(MemberConnectionStatus.NOT_READY, participant.connectionStatus)
        assertEquals(1, store.slotIndexOfParticipant("member-1"))
        assertEquals(listOf("member-1"), transport.replacedParticipants)
        assertEquals(1, port.broadcastCount)
        assertTrue(port.hints.last().contains("Member Reconnected 已重新连接"))

        val accepted = decodeSingleMessage(output) as RoomWireMessage.JoinRoomAccepted
        val reconnectedSlot = accepted.snapshot.slots.single { it.participantId == "member-1" }
        assertEquals("Member Reconnected", reconnectedSlot.displayName)
        assertEquals(MemberConnectionStatus.NOT_READY.name, reconnectedSlot.connectionStatus)
    }

    private fun coordinator(
        authorityStore: RoomAuthorityStore,
        roomTransport: RoomTransport,
        port: RoomMembershipPort,
    ): RoomMembershipCoordinator {
        return RoomMembershipCoordinator(
            scope = CoroutineScope(SupervisorJob()),
            authorityStore = authorityStore,
            roomTransport = roomTransport,
            matchCoordinator = NetworkMatchCoordinator(CoroutineScope(SupervisorJob())),
            reconnectSessionRepository = mock(ReconnectSessionRepository::class.java),
            port = port,
        )
    }

    private fun hostAuthorityStore(): RoomAuthorityStore {
        return RoomAuthorityStore().apply {
            createHostRoom(
                playerName = "Host",
                avatarResId = null,
                hostDeviceName = "HostDevice",
                bluetoothVisible = true,
            )
        }
    }

    private fun joinRequest(
        playerName: String,
        resumeParticipantId: String? = null,
    ): RoomWireMessage.JoinRoomRequest {
        return RoomWireMessage.JoinRoomRequest(
            playerName = playerName,
            avatarResId = null,
            resumeParticipantId = resumeParticipantId,
        )
    }

    private fun roomConnection(
        address: String,
        output: OutputStream = CloseAwareOutputStream(),
    ): RoomSocketConnection {
        val socket = mock(BluetoothSocket::class.java)
        val device = mock(BluetoothDevice::class.java)
        `when`(device.address).thenReturn(address)
        `when`(socket.remoteDevice).thenReturn(device)
        `when`(socket.inputStream).thenReturn(ByteArrayInputStream(ByteArray(0)))
        `when`(socket.outputStream).thenReturn(output)
        return RoomSocketConnection(socket = socket, frameCodec = RoomFrameCodec())
    }

    private fun decodeSingleMessage(output: CloseAwareOutputStream): RoomWireMessage {
        return RoomFrameCodec().readMessage(DataInputStream(ByteArrayInputStream(output.toByteArray())))
    }

    private fun assertNoParticipant(store: RoomAuthorityStore, displayName: String) {
        assertTrue(store.state.participants.values.none { it.displayName == displayName })
        assertTrue(store.state.slotAssignments.values.none { participantId ->
            store.state.participants[participantId]?.displayName == displayName
        })
    }

    private fun fillRemoteSlots(store: RoomAuthorityStore) {
        store.update { state ->
            state.copy(
                participants = state.participants + (1..3).associate { index ->
                    "member-$index" to ParticipantRecord(
                        participantId = "member-$index",
                        occupantType = SlotOccupantType.HUMAN_MEMBER,
                        displayName = "Member $index",
                        avatarResId = null,
                        connectionStatus = MemberConnectionStatus.CONNECTED,
                    )
                },
                slotAssignments = state.slotAssignments + mapOf(
                    1 to "member-1",
                    2 to "member-2",
                    3 to "member-3",
                ),
            )
        }
    }

    private fun addDisconnectedMember(store: RoomAuthorityStore) {
        store.update { state ->
            state.copy(
                participants = state.participants + (
                    "member-1" to ParticipantRecord(
                        participantId = "member-1",
                        occupantType = SlotOccupantType.HUMAN_MEMBER,
                        displayName = "Member",
                        avatarResId = null,
                        connectionStatus = MemberConnectionStatus.DISCONNECTED,
                    )
                ),
                slotAssignments = state.slotAssignments + (1 to "member-1"),
            )
        }
    }

    private class FakeMembershipPort(
        private val authorityStore: RoomAuthorityStore,
    ) : RoomMembershipPort {
        val hints = mutableListOf<String>()
        var broadcastCount = 0

        override fun snapshotOfCurrentRoom(): RemoteRoomSnapshot {
            return authorityStore.snapshotOfCurrentRoom(
                connectionHint = hints.lastOrNull().orEmpty(),
                localParticipantId = HOST_PARTICIPANT_ID,
            )
        }

        override fun publishConnectionHint(message: String) {
            hints += message
        }

        override fun publishRoomClosed(message: String) = Unit

        override fun resetRoomUiState() = Unit

        override fun broadcastSnapshot() {
            broadcastCount++
        }

        override fun updateAllMatchSnapshots(lastActionMessage: String?) = Unit
    }

    private class FakeRoomTransport(
        private val replaceAction: () -> Unit = {},
        private val onReplace: () -> Unit = {},
    ) : RoomTransport {
        val attachedParticipants = mutableListOf<String>()
        val replacedParticipants = mutableListOf<String>()
        val disconnectedParticipants = mutableListOf<String>()
        override val events: Flow<RoomTransportEvent> = MutableSharedFlow()

        override suspend fun startHost(config: HostTransportConfig): Result<Unit> = Result.success(Unit)

        override fun launchHostHeartbeat(
            heartbeatIntervalMs: Long,
            heartbeatTimeoutMs: Long,
        ) = Unit

        override suspend fun connectToHost(
            device: BluetoothDiscoveredDevice,
            roomUuid: UUID,
        ): Result<RoomClientConnection> = Result.failure(IOException("not used"))

        override fun attachHostReadLoop(
            participantId: String,
            connection: RoomSocketConnection,
        ) {
            attachAction()
            onAttach()
            attachedParticipants += participantId
        }

        override fun attachClientReadLoop(connection: RoomClientConnection) = Unit

        override fun startClientHeartbeatWatchdog(
            heartbeatTimeoutMs: Long,
            clientHeartbeatCheckIntervalMs: Long,
        ) = Unit

        override fun replaceHostConnection(
            participantId: String,
            connection: RoomSocketConnection,
        ) {
            replaceAction()
            onReplace()
            replacedParticipants += participantId
        }

        override fun sendToHost(message: RoomWireMessage): Result<Unit> = Result.success(Unit)

        override fun sendToParticipant(
            participantId: String,
            message: RoomWireMessage,
        ): Result<Unit> = Result.success(Unit)

        override fun broadcast(message: RoomWireMessage): Result<BroadcastResult> = Result.success(BroadcastResult())

        override fun disconnectParticipant(participantId: String) {
            disconnectedParticipants += participantId
        }

        override fun shutdown() = Unit

        override fun closeNow() = Unit

        override fun clearClientConnection() = Unit
    }

    private open class CloseAwareOutputStream(
        private val onFirstWrite: () -> Unit = {},
    ) : OutputStream() {
        private val bytes = ByteArrayOutputStream()
        var closeCalled = false
        private var writeObserved = false

        override fun write(b: Int) {
            notifyFirstWrite()
            bytes.write(b)
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            notifyFirstWrite()
            bytes.write(b, off, len)
        }

        override fun flush() {
            bytes.flush()
        }

        override fun close() {
            closeCalled = true
            bytes.close()
        }

        fun toByteArray(): ByteArray = bytes.toByteArray()

        private fun notifyFirstWrite() {
            if (!writeObserved) {
                writeObserved = true
                onFirstWrite()
            }
        }
    }

    private class ThrowingOutputStream(
        private val error: IOException,
    ) : CloseAwareOutputStream() {
        override fun write(b: Int) {
            throw error
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            throw error
        }
    }
}
