package com.example.chudadi.controller.client

import com.example.chudadi.model.game.snapshot.MatchUiState
import com.example.chudadi.network.game.GameWireMessage
import com.example.chudadi.network.game.RemoteMatchSnapshot
import com.example.chudadi.network.game.toMatchUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class BluetoothRemoteMatchController {
    private val _uiState = MutableStateFlow(MatchUiState())
    val uiState: StateFlow<MatchUiState> = _uiState.asStateFlow()

    private var selectedCardIds: Set<String> = emptySet()
    private var localSeatId: Int = 0
    private var lastSnapshot: RemoteMatchSnapshot? = null
    private var lastTablePlaySignature: String? = null

    fun onMatchStarted(message: GameWireMessage.MatchStarted) {
        localSeatId = message.localSeatId
        selectedCardIds = emptySet()
        lastSnapshot = message.snapshot
        lastTablePlaySignature = tablePlaySignature(message.snapshot)
        _uiState.value = message.snapshot.toMatchUiState(selectedCardIds)
    }

    fun onSnapshot(snapshot: RemoteMatchSnapshot) {
        val nextTablePlaySignature = tablePlaySignature(snapshot)
        val shouldClearSelection = !snapshot.isHumanTurn || lastTablePlaySignature != nextTablePlaySignature
        lastSnapshot = snapshot
        val baseSelection = if (shouldClearSelection) emptySet() else selectedCardIds
        val validSelection = snapshot.playerHand
            .map { "${it.rank}_${it.suit}" }
            .filter { it in baseSelection }
            .toSet()
        selectedCardIds = validSelection
        lastTablePlaySignature = nextTablePlaySignature
        _uiState.value = snapshot.toMatchUiState(selectedCardIds)
    }

    fun onToggleCardSelection(cardId: String) {
        val snapshot = lastSnapshot ?: return
        if (!_uiState.value.isHumanTurn) {
            return
        }
        selectedCardIds = if (cardId in selectedCardIds) selectedCardIds - cardId else selectedCardIds + cardId
        _uiState.value = snapshot.toMatchUiState(selectedCardIds)
    }

    fun onClearSelection() {
        val snapshot = lastSnapshot ?: return
        selectedCardIds = emptySet()
        _uiState.value = snapshot.toMatchUiState(selectedCardIds)
    }

    fun onActionRejected(message: String) {
        val snapshot = lastSnapshot ?: return
        _uiState.value = snapshot.toMatchUiState(selectedCardIds).copy(lastActionMessage = message)
    }

    fun selectedCardIds(): Set<String> = selectedCardIds
    fun localSeatId(): Int = localSeatId

    fun reset() {
        selectedCardIds = emptySet()
        lastSnapshot = null
        lastTablePlaySignature = null
        _uiState.value = MatchUiState()
    }

    private fun tablePlaySignature(snapshot: RemoteMatchSnapshot): String {
        val tablePlays = if (snapshot.tablePlays.isNotEmpty()) {
            snapshot.tablePlays
        } else {
            listOfNotNull(snapshot.currentTablePlay)
        }
        return tablePlays.joinToString(separator = "#") { tablePlay ->
            listOf(
                tablePlay.ownerViewSeat,
                tablePlay.ownerName,
                tablePlay.combinationLabel,
                tablePlay.stackOrder.toString(),
                tablePlay.cardLabels.joinToString(separator = "|"),
            ).joinToString(separator = ":")
        }
    }
}
