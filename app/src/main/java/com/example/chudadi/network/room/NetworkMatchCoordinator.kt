@file:Suppress("TooManyFunctions")

package com.example.chudadi.network.room

import com.example.chudadi.ai.rulebased.RuleBasedAiPlayer
import com.example.chudadi.controller.client.BluetoothRemoteMatchController
import com.example.chudadi.controller.game.LocalGameAction
import com.example.chudadi.controller.game.MatchUiStateMapper
import com.example.chudadi.controller.server.BluetoothAuthoritativeMatchController
import com.example.chudadi.model.game.engine.GameEngine
import com.example.chudadi.model.game.entity.MatchPhase
import com.example.chudadi.model.game.entity.RoundScore
import com.example.chudadi.model.game.snapshot.MatchUiState
import com.example.chudadi.network.game.GameWireMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

class NetworkMatchCoordinator(
    scope: CoroutineScope,
) {
    private val engine = GameEngine()
    private val matchMapper = MatchUiStateMapper(engine)
    private val aiPlayer = RuleBasedAiPlayer()
    private val hostMatchController = BluetoothAuthoritativeMatchController(engine, matchMapper, aiPlayer)
    private val localMatchController = BluetoothRemoteMatchController()
    private val actionRouter = NetworkMatchActionRouter(hostMatchController, localMatchController)
    private val loopDriver = NetworkMatchLoopDriver(scope, hostMatchController)
    private var activeMatchSeatAssignments: Map<String, Int> = emptyMap()

    val matchUiState: StateFlow<MatchUiState> = localMatchController.uiState

    fun currentMatchPhase(): MatchPhase? = hostMatchController.currentMatch()?.phase

    fun hasActiveMatchSeatAssignments(): Boolean = activeMatchSeatAssignments.isNotEmpty()

    fun clearCurrentMatch() {
        hostMatchController.clearCurrentMatch()
        clearActiveMatchState()
        loopDriver.cancel()
    }

    fun reset() {
        localMatchController.reset()
        clearCurrentMatch()
    }

    fun exitMatchToRoom() {
        localMatchController.reset()
        clearCurrentMatch()
    }

    fun localSeatId(participantId: String, authorityStore: RoomAuthorityStore): Int? {
        return matchSeatIdOfParticipant(participantId, authorityStore)
    }

    fun matchSeatIdOfParticipant(participantId: String, authorityStore: RoomAuthorityStore): Int? {
        return activeMatchSeatAssignments[participantId] ?: authorityStore.slotIndexOfParticipant(participantId)
    }

    fun startNetworkMatch(
        authorityStore: RoomAuthorityStore,
        localParticipantId: String,
        sendToParticipant: (String, RoomWireMessage) -> Result<Unit>,
        onMatchStartedSendFailed: (String, Throwable) -> Unit,
    ): Result<Unit> {
        activeMatchSeatAssignments = buildActiveMatchSeatAssignments(authorityStore)
        return actionRouter.startNetworkMatch(
            authorityStore = authorityStore,
            localParticipantId = localParticipantId,
            activeMatchSeatAssignments = activeMatchSeatAssignments,
            sendToParticipant = sendToParticipant,
            onMatchStartedSendFailed = onMatchStartedSendFailed,
            localSeatId = ::localSeatId,
        )
    }

    fun onLocalGameActionAsHost(
        action: LocalGameAction,
        authorityStore: RoomAuthorityStore,
        localParticipantId: String,
        updateAllMatchSnapshots: (String?) -> Unit,
    ) {
        actionRouter.onLocalGameActionAsHost(
            action = action,
            authorityStore = authorityStore,
            localParticipantId = localParticipantId,
            updateAllMatchSnapshots = updateAllMatchSnapshots,
            localSeatId = ::localSeatId,
        )
    }

    fun onLocalGameActionAsClient(
        action: LocalGameAction,
        sendToHost: (RoomWireMessage) -> Result<Unit>,
    ): Result<Unit> {
        return actionRouter.onLocalGameActionAsClient(action, sendToHost)
    }

    fun handleClientGameEnvelope(
        message: GameWireMessage,
        handleHostConnectionLost: (String) -> Unit,
    ) {
        actionRouter.handleClientGameEnvelope(message, handleHostConnectionLost)
    }

    fun handleHostGameEnvelope(
        participantId: String,
        message: GameWireMessage,
        authorityStore: RoomAuthorityStore,
        sendToParticipant: (String, RoomWireMessage) -> Result<Unit>,
        updateAllMatchSnapshots: (String?) -> Unit,
    ) {
        actionRouter.handleHostGameEnvelope(
            participantId = participantId,
            message = message,
            context = NetworkMatchActionRouter.HostGameEnvelopeContext(
                authorityStore = authorityStore,
                sendToParticipant = sendToParticipant,
                updateAllMatchSnapshots = updateAllMatchSnapshots,
                matchSeatIdOfParticipant = ::matchSeatIdOfParticipant,
            ),
        )
    }

    fun startMatchLoop(
        updateAllMatchSnapshots: (String?) -> Unit,
        seatDisplayName: (Int) -> String,
        isSeatDisconnected: (Int) -> Boolean,
    ) {
        loopDriver.startMatchLoop(updateAllMatchSnapshots, seatDisplayName, isSeatDisconnected)
    }

    fun updateAllMatchSnapshots(
        authorityStore: RoomAuthorityStore,
        localParticipantId: String,
        sendToParticipant: (String, RoomWireMessage) -> Result<Unit>,
        onMatchFinished: (List<RoundScore>) -> Unit,
        lastActionMessage: String?,
    ) {
        actionRouter.updateAllMatchSnapshots(
            lastActionMessage = lastActionMessage,
            context = NetworkMatchActionRouter.MatchSnapshotDispatchContext(
                authorityStore = authorityStore,
                localParticipantId = localParticipantId,
                activeMatchSeatAssignments = activeMatchSeatAssignments,
                sendToParticipant = sendToParticipant,
                onMatchFinished = onMatchFinished,
                localSeatId = ::localSeatId,
                onMatchEnded = {
                    clearActiveMatchState()
                    loopDriver.cancel()
                },
            ),
        )
    }

    fun onParticipantDisconnected(participantId: String, authorityStore: RoomAuthorityStore) {
        matchSeatIdOfParticipant(participantId, authorityStore)?.let { seatId ->
            hostMatchController.markDisconnected(seatId, disconnected = true)
        }
    }

    fun onParticipantReconnected(participantId: String, authorityStore: RoomAuthorityStore) {
        val seatId = matchSeatIdOfParticipant(participantId, authorityStore) ?: return
        hostMatchController.onSeatReconnected(seatId)
    }

    fun sendMatchRecoveryMessage(
        participantId: String,
        authorityStore: RoomAuthorityStore,
        sendToParticipant: (String, RoomWireMessage) -> Result<Unit>,
    ) {
        actionRouter.sendMatchRecoveryMessage(
            participantId = participantId,
            authorityStore = authorityStore,
            sendToParticipant = sendToParticipant,
            matchSeatIdOfParticipant = ::matchSeatIdOfParticipant,
        )
    }

    private fun buildActiveMatchSeatAssignments(authorityStore: RoomAuthorityStore): Map<String, Int> {
        return authorityStore.state.slotAssignments.entries
            .mapNotNull { (slotIndex, participantId) -> participantId?.let { it to slotIndex } }
            .toMap()
    }

    private fun clearActiveMatchState() {
        activeMatchSeatAssignments = emptyMap()
    }
}
