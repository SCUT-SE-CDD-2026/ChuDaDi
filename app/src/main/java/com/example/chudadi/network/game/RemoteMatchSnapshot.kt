package com.example.chudadi.network.game

import com.example.chudadi.model.game.entity.Card
import com.example.chudadi.model.game.entity.CardRank
import com.example.chudadi.model.game.entity.CardSuit
import com.example.chudadi.model.game.entity.MatchPhase
import com.example.chudadi.model.game.entity.RoundScore
import com.example.chudadi.model.game.snapshot.MatchUiState
import com.example.chudadi.model.game.snapshot.OpponentSummary
import com.example.chudadi.model.game.snapshot.ResultSummary
import com.example.chudadi.model.game.snapshot.TablePlaySummary
import com.example.chudadi.model.game.snapshot.ViewSeat
import kotlinx.serialization.Serializable

@Serializable
data class RemoteMatchCard(
    val suit: String,
    val rank: String,
)

@Serializable
data class RemoteOpponentSummary(
    val viewSeat: String,
    val authoritySeatId: Int,
    val displayName: String,
    val avatarResId: Int,
    val remainingCards: Int,
    val isCurrentActor: Boolean,
    val hasPassed: Boolean,
    val isDisconnected: Boolean,
)

@Serializable
data class RemoteTablePlaySummary(
    val playId: String,
    val ownerViewSeat: String,
    val ownerName: String,
    val combinationLabel: String,
    val cardLabels: List<String>,
    val stackOrder: Int,
)

@Serializable
data class RemoteRoundScore(
    val seatId: Int,
    val playerName: String,
    val remainingCards: Int,
    val roundScore: Int,
)

@Serializable
data class RemoteResultSummary(
    val winnerName: String,
    val rankingLines: List<String>,
    val roundScores: List<RemoteRoundScore>,
)

@Serializable
data class RemoteMatchSnapshot(
    val matchId: String,
    val phase: String,
    val playerHand: List<RemoteMatchCard>,
    val opponentSummaries: List<RemoteOpponentSummary>,
    val currentActorName: String,
    val tablePlays: List<RemoteTablePlaySummary>,
    val currentTablePlay: RemoteTablePlaySummary?,
    val lastActionMessage: String?,
    val canPass: Boolean,
    val remainingTurnSeconds: Int?,
    val resultSummary: RemoteResultSummary?,
    val isHumanTurn: Boolean,
    val isLocalDisconnected: Boolean,
)

fun MatchUiState.toRemoteMatchSnapshot(matchId: String): RemoteMatchSnapshot {
    return RemoteMatchSnapshot(
        matchId = matchId,
        phase = phase.name,
        playerHand = playerHand.map { card ->
            RemoteMatchCard(suit = card.suit.name, rank = card.rank.name)
        },
        opponentSummaries = opponentSummaries.map { opponent ->
            RemoteOpponentSummary(
                viewSeat = opponent.viewSeat.name,
                authoritySeatId = opponent.authoritySeatId,
                displayName = opponent.displayName,
                avatarResId = opponent.avatarResId,
                remainingCards = opponent.remainingCards,
                isCurrentActor = opponent.isCurrentActor,
                hasPassed = opponent.hasPassed,
                isDisconnected = opponent.isDisconnected,
            )
        },
        currentActorName = currentActorName,
        tablePlays = tablePlays.map { tablePlay ->
            RemoteTablePlaySummary(
                playId = tablePlay.playId,
                ownerViewSeat = tablePlay.ownerViewSeat.name,
                ownerName = tablePlay.ownerName,
                combinationLabel = tablePlay.combinationLabel,
                cardLabels = tablePlay.cardLabels,
                stackOrder = tablePlay.stackOrder,
            )
        },
        currentTablePlay = currentTablePlay?.let { tablePlay ->
            RemoteTablePlaySummary(
                playId = tablePlay.playId,
                ownerViewSeat = tablePlay.ownerViewSeat.name,
                ownerName = tablePlay.ownerName,
                combinationLabel = tablePlay.combinationLabel,
                cardLabels = tablePlay.cardLabels,
                stackOrder = tablePlay.stackOrder,
            )
        },
        lastActionMessage = lastActionMessage,
        canPass = canPass,
        remainingTurnSeconds = remainingTurnSeconds,
        resultSummary = resultSummary?.let { result ->
            RemoteResultSummary(
                winnerName = result.winnerName,
                rankingLines = result.rankingLines,
                roundScores = result.roundScores.map { score ->
                    RemoteRoundScore(
                        seatId = score.seatId,
                        playerName = score.playerName,
                        remainingCards = score.remainingCards,
                        roundScore = score.roundScore,
                    )
                },
            )
        },
        isHumanTurn = isHumanTurn,
        isLocalDisconnected = isLocalDisconnected,
    )
}

fun RemoteMatchSnapshot.toMatchUiState(selectedCardIds: Set<String>): MatchUiState {
    val hand = playerHand.map { remoteCard ->
        Card(
            suit = CardSuit.valueOf(remoteCard.suit),
            rank = CardRank.valueOf(remoteCard.rank),
        )
    }
    val actualSelected = hand.filter { it.id in selectedCardIds }.map(Card::id).toSet()
    return MatchUiState(
        phase = MatchPhase.valueOf(phase),
        playerHand = hand,
        selectedCards = actualSelected,
        opponentSummaries = opponentSummaries.map { opponent ->
            OpponentSummary(
                viewSeat = ViewSeat.valueOf(opponent.viewSeat),
                authoritySeatId = opponent.authoritySeatId,
                displayName = opponent.displayName,
                avatarResId = opponent.avatarResId,
                remainingCards = opponent.remainingCards,
                isCurrentActor = opponent.isCurrentActor,
                hasPassed = opponent.hasPassed,
                isDisconnected = opponent.isDisconnected,
            )
        },
        currentActorName = currentActorName,
        tablePlays = tablePlays.map { tablePlay ->
            TablePlaySummary(
                playId = tablePlay.playId,
                ownerViewSeat = ViewSeat.valueOf(tablePlay.ownerViewSeat),
                ownerName = tablePlay.ownerName,
                combinationLabel = tablePlay.combinationLabel,
                cardLabels = tablePlay.cardLabels,
                stackOrder = tablePlay.stackOrder,
            )
        },
        currentTablePlay = currentTablePlay?.let { tablePlay ->
            TablePlaySummary(
                playId = tablePlay.playId,
                ownerViewSeat = ViewSeat.valueOf(tablePlay.ownerViewSeat),
                ownerName = tablePlay.ownerName,
                combinationLabel = tablePlay.combinationLabel,
                cardLabels = tablePlay.cardLabels,
                stackOrder = tablePlay.stackOrder,
            )
        },
        lastActionMessage = lastActionMessage,
        canSubmitPlay = isHumanTurn && actualSelected.isNotEmpty(),
        canPass = canPass,
        remainingTurnSeconds = remainingTurnSeconds,
        resultSummary = resultSummary?.let { result ->
            ResultSummary(
                winnerName = result.winnerName,
                rankingLines = result.rankingLines,
                roundScores = result.roundScores.map { score ->
                    RoundScore(
                        seatId = score.seatId,
                        playerName = score.playerName,
                        remainingCards = score.remainingCards,
                        roundScore = score.roundScore,
                    )
                },
            )
        },
        isHumanTurn = isHumanTurn,
        isLocalDisconnected = isLocalDisconnected,
    )
}
