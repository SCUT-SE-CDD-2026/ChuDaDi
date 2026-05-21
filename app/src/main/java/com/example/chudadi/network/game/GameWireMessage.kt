package com.example.chudadi.network.game

import kotlinx.serialization.Serializable

@Serializable
sealed class GameWireMessage {
    @Serializable
    data class MatchStarted(
        val localSeatId: Int,
        val snapshot: RemoteMatchSnapshot,
    ) : GameWireMessage()

    @Serializable
    data class MatchSnapshotMessage(
        val snapshot: RemoteMatchSnapshot,
    ) : GameWireMessage()

    @Serializable
    data class PlayCardsRequest(
        val selectedCardIds: Set<String>,
    ) : GameWireMessage()

    @Serializable
    data object PassRequest : GameWireMessage()

    @Serializable
    data class ActionRejected(
        val message: String,
    ) : GameWireMessage()

    @Serializable
    data class MatchClosed(
        val reason: String,
    ) : GameWireMessage()
}
