package com.example.chudadi.network.room

import com.example.chudadi.controller.client.BluetoothRemoteMatchController
import com.example.chudadi.controller.game.LocalGameAction
import com.example.chudadi.controller.server.BluetoothAuthoritativeMatchController
import com.example.chudadi.model.game.entity.MatchPhase
import com.example.chudadi.model.game.entity.RoundScore
import com.example.chudadi.model.game.rule.GameRuleSet
import com.example.chudadi.network.game.GameWireMessage
import com.example.chudadi.network.game.toRemoteMatchSnapshot
import com.example.chudadi.ui.room.GameRuleDisplay
import com.example.chudadi.ui.room.MemberConnectionStatus
import com.example.chudadi.ui.room.SlotOccupantType

class NetworkMatchActionRouter(
    private val hostMatchController: BluetoothAuthoritativeMatchController,
    private val localMatchController: BluetoothRemoteMatchController,
) {
    data class HostGameEnvelopeContext(
        val authorityStore: RoomAuthorityStore,
        val sendToParticipant: (String, RoomWireMessage) -> Result<Unit>,
        val updateAllMatchSnapshots: (String?) -> Unit,
        val matchSeatIdOfParticipant: (String, RoomAuthorityStore) -> Int?,
    )

    data class MatchSnapshotDispatchContext(
        val authorityStore: RoomAuthorityStore,
        val localParticipantId: String,
        val activeMatchSeatAssignments: Map<String, Int>,
        val sendToParticipant: (String, RoomWireMessage) -> Result<Unit>,
        val onMatchFinished: (List<RoundScore>) -> Unit,
        val localSeatId: (String, RoomAuthorityStore) -> Int?,
        val onMatchEnded: () -> Unit,
    )

    @Suppress("LongParameterList")
    fun startNetworkMatch(
        authorityStore: RoomAuthorityStore,
        localParticipantId: String,
        activeMatchSeatAssignments: Map<String, Int>,
        sendToParticipant: (String, RoomWireMessage) -> Result<Unit>,
        onMatchStartedSendFailed: (String, Throwable) -> Unit,
        localSeatId: (String, RoomAuthorityStore) -> Int?,
        aiMoveDelayMillis: Long = 0L,
    ): Result<Unit> {
        val hostSeatId = localSeatId(localParticipantId, authorityStore)
            ?: return Result.failure(IllegalStateException("Host match seat is not available"))
        val seatConfigs = authorityStore.buildSeatConfigs()
        hostMatchController.startMatch(
            seatConfigs = seatConfigs,
            ruleSet = authorityStore.state.currentRule.toGameRuleSet(),
            aiMoveDelayMillis = aiMoveDelayMillis,
        )
        val hostSnapshot = hostMatchController.buildSnapshotForSeat(localSeatId = hostSeatId)
        localMatchController.onMatchStarted(
            GameWireMessage.MatchStarted(
                localSeatId = hostSeatId,
                snapshot = hostSnapshot.toRemoteMatchSnapshot(
                    hostMatchController.currentMatch()?.matchId.orEmpty(),
                ),
            ),
        )
        activeMatchSeatAssignments.forEach { (participantId, seatId) ->
            if (participantId == HOST_PARTICIPANT_ID) return@forEach
            val participant = authorityStore.state.participants[participantId] ?: return@forEach
            if (!participant.shouldReceiveMatchStarted()) return@forEach
            val sendResult = sendToParticipant(
                participantId,
                RoomWireMessage.GameEnvelope(
                    hostMatchController.buildMatchStartedMessage(localSeatId = seatId),
                ),
            )
            val failure = sendResult.exceptionOrNull()
            if (failure != null) {
                onMatchStartedSendFailed(participantId, failure)
            }
        }
        return Result.success(Unit)
    }

    fun onLocalGameActionAsHost(
        action: LocalGameAction,
        authorityStore: RoomAuthorityStore,
        localParticipantId: String,
        updateAllMatchSnapshots: (String?) -> Unit,
        localSeatId: (String, RoomAuthorityStore) -> Int?,
    ) {
        when (action) {
            is LocalGameAction.ToggleCardSelection -> localMatchController.onToggleCardSelection(action.cardId)
            LocalGameAction.ClearSelection -> localMatchController.onClearSelection()
            LocalGameAction.SubmitSelectedCards -> handleHostSubmitSelectedCards(
                authorityStore = authorityStore,
                localParticipantId = localParticipantId,
                updateAllMatchSnapshots = updateAllMatchSnapshots,
                localSeatId = localSeatId,
            )
            LocalGameAction.PassTurn -> handleHostPassTurn(
                authorityStore = authorityStore,
                localParticipantId = localParticipantId,
                updateAllMatchSnapshots = updateAllMatchSnapshots,
                localSeatId = localSeatId,
            )
            LocalGameAction.ExitToHome -> Unit
            LocalGameAction.RestartMatch -> Unit
            is LocalGameAction.StartLocalMatch -> Unit
        }
    }

    fun onLocalGameActionAsClient(
        action: LocalGameAction,
        sendToHost: (RoomWireMessage) -> Result<Unit>,
    ): Result<Unit> {
        when (action) {
            is LocalGameAction.ToggleCardSelection -> localMatchController.onToggleCardSelection(action.cardId)
            LocalGameAction.ClearSelection -> localMatchController.onClearSelection()
            LocalGameAction.SubmitSelectedCards -> {
                val sendResult = sendToHost(
                    RoomWireMessage.GameEnvelope(
                        GameWireMessage.PlayCardsRequest(localMatchController.selectedCardIds()),
                    ),
                )
                if (sendResult.isFailure) {
                    localMatchController.onActionRejected("游戏消息发送失败")
                    return sendResult
                }
            }

            LocalGameAction.PassTurn -> {
                val sendResult = sendToHost(RoomWireMessage.GameEnvelope(GameWireMessage.PassRequest))
                if (sendResult.isFailure) {
                    localMatchController.onActionRejected("游戏消息发送失败")
                    return sendResult
                }
            }

            LocalGameAction.ExitToHome -> Unit
            LocalGameAction.RestartMatch -> Unit
            is LocalGameAction.StartLocalMatch -> Unit
        }
        return Result.success(Unit)
    }

    fun handleClientGameEnvelope(
        message: GameWireMessage,
        handleHostConnectionLost: (String) -> Unit,
    ) {
        when (message) {
            is GameWireMessage.MatchStarted -> localMatchController.onMatchStarted(message)
            is GameWireMessage.MatchSnapshotMessage -> localMatchController.onSnapshot(message.snapshot)
            is GameWireMessage.ActionRejected -> localMatchController.onActionRejected(message.message)
            is GameWireMessage.MatchClosed -> {
                localMatchController.reset()
                handleHostConnectionLost(message.reason)
            }

            is GameWireMessage.PassRequest,
            is GameWireMessage.PlayCardsRequest,
            -> Unit
        }
    }

    fun handleHostGameEnvelope(
        participantId: String,
        message: GameWireMessage,
        context: HostGameEnvelopeContext,
    ) {
        val seatId = context.matchSeatIdOfParticipant(participantId, context.authorityStore)
        if (seatId != null) {
            when (message) {
                is GameWireMessage.PlayCardsRequest -> {
                    val result = hostMatchController.handlePlayRequest(seatId, message.selectedCardIds)
                    if (result.success) {
                        context.updateAllMatchSnapshots(result.message)
                    } else {
                        context.sendToParticipant(
                            participantId,
                            RoomWireMessage.GameEnvelope(
                                GameWireMessage.ActionRejected(result.message ?: "出牌失败"),
                            ),
                        )
                    }
                }

                GameWireMessage.PassRequest -> {
                    val result = hostMatchController.handlePassRequest(seatId)
                    if (result.success) {
                        context.updateAllMatchSnapshots(result.message)
                    } else {
                        context.sendToParticipant(
                            participantId,
                            RoomWireMessage.GameEnvelope(
                                GameWireMessage.ActionRejected(result.message ?: "过牌失败"),
                            ),
                        )
                    }
                }

                is GameWireMessage.MatchStarted,
                is GameWireMessage.MatchSnapshotMessage,
                is GameWireMessage.ActionRejected,
                is GameWireMessage.MatchClosed,
                -> Unit
            }
        }
    }

    fun updateAllMatchSnapshots(
        lastActionMessage: String?,
        context: MatchSnapshotDispatchContext,
    ) {
        val match = hostMatchController.currentMatch() ?: return
        val hostSeatId = context.localSeatId(context.localParticipantId, context.authorityStore) ?: return
        localMatchController.onSnapshot(
            hostMatchController.buildSnapshotMessage(
                localSeatId = hostSeatId,
                lastActionMessage = lastActionMessage,
            ).snapshot,
        )
        context.activeMatchSeatAssignments.forEach { (participantId, seatId) ->
            if (participantId == HOST_PARTICIPANT_ID) return@forEach
            context.sendToParticipant(
                participantId,
                RoomWireMessage.GameEnvelope(
                    hostMatchController.buildSnapshotMessage(seatId, lastActionMessage),
                ),
            ).onFailure { return@forEach }
        }
        if (match.phase == MatchPhase.FINISHED) {
            val roundScores = match.result?.scoreSummary?.roundScores.orEmpty()
            context.onMatchFinished(roundScores)
            context.onMatchEnded()
        }
    }

    fun sendMatchRecoveryMessage(
        participantId: String,
        authorityStore: RoomAuthorityStore,
        sendToParticipant: (String, RoomWireMessage) -> Result<Unit>,
        matchSeatIdOfParticipant: (String, RoomAuthorityStore) -> Int?,
    ) {
        val seatId = matchSeatIdOfParticipant(participantId, authorityStore)
        val match = hostMatchController.currentMatch()
        val isRecoverableMatch = match?.phase != MatchPhase.FINISHED
        if (seatId == null || match == null || !isRecoverableMatch) {
            return
        }
        sendToParticipant(
            participantId,
            RoomWireMessage.GameEnvelope(
                hostMatchController.buildMatchStartedMessage(localSeatId = seatId),
            ),
        ).onFailure { return }
    }

    private fun GameRuleDisplay.toGameRuleSet(): GameRuleSet {
        return when (this) {
            GameRuleDisplay.SOUTHERN -> GameRuleSet.SOUTHERN
            GameRuleDisplay.NORTHERN -> GameRuleSet.NORTHERN
        }
    }

    private fun handleHostSubmitSelectedCards(
        authorityStore: RoomAuthorityStore,
        localParticipantId: String,
        updateAllMatchSnapshots: (String?) -> Unit,
        localSeatId: (String, RoomAuthorityStore) -> Int?,
    ) {
        val seatId = localSeatId(localParticipantId, authorityStore) ?: return
        val selected = localMatchController.selectedCardIds()
        val result = hostMatchController.handlePlayRequest(
            seatId = seatId,
            selectedCardIds = selected,
        )
        if (result.success) {
            updateAllMatchSnapshots(result.message)
        } else {
            localMatchController.onActionRejected(result.message ?: "出牌失败")
        }
    }

    private fun handleHostPassTurn(
        authorityStore: RoomAuthorityStore,
        localParticipantId: String,
        updateAllMatchSnapshots: (String?) -> Unit,
        localSeatId: (String, RoomAuthorityStore) -> Int?,
    ) {
        val seatId = localSeatId(localParticipantId, authorityStore) ?: return
        val result = hostMatchController.handlePassRequest(seatId)
        if (result.success) {
            updateAllMatchSnapshots(result.message)
        } else {
            localMatchController.onActionRejected(result.message ?: "过牌失败")
        }
    }
}

private fun ParticipantRecord.shouldReceiveMatchStarted(): Boolean {
    return occupantType == SlotOccupantType.HUMAN_MEMBER &&
        connectionStatus != MemberConnectionStatus.DISCONNECTED
}
