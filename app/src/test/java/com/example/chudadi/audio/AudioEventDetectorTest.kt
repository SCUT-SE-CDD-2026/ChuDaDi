package com.example.chudadi.audio

import com.example.chudadi.model.game.entity.MatchPhase
import com.example.chudadi.model.game.snapshot.MatchUiState
import com.example.chudadi.model.game.snapshot.OpponentSummary
import com.example.chudadi.model.game.snapshot.ResultSummary
import com.example.chudadi.model.game.snapshot.TablePlaySummary
import com.example.chudadi.model.game.snapshot.ViewSeat
import org.junit.Assert.assertEquals
import org.junit.Test

class AudioEventDetectorTest {

    private val detector = AudioEventDetector()

    // -- Main path tests --

    @Test
    fun detect_tablePlaysIncrease_emitsCardPlay() {
        val old = defaultState(StateParams(tablePlays = listOf(dummyTablePlay(0))))
        val new = defaultState(StateParams(tablePlays = listOf(dummyTablePlay(0), dummyTablePlay(1))))

        val events = detector.detect(old, new)

        assertEquals(listOf(AudioEvent.Sfx.CardPlay), events)
    }

    @Test
    fun detect_humanTurnBecomesTrue_emitsYourTurn() {
        val old = defaultState(StateParams(isHumanTurn = false,
        phase = MatchPhase.PLAYER_TURN,))
        val new = defaultState(StateParams(isHumanTurn = true,
        phase = MatchPhase.PLAYER_TURN,))

        val events = detector.detect(old, new)

        assertEquals(listOf(AudioEvent.Sfx.YourTurn), events)
    }

    @Test
    fun detect_playerTurnToRoundReset_emitsTrickWon() {
        val old = defaultState(StateParams(phase = MatchPhase.PLAYER_TURN))
        val new = defaultState(StateParams(phase = MatchPhase.ROUND_RESET))

        val events = detector.detect(old, new)

        assertEquals(listOf(AudioEvent.Sfx.TrickWon), events)
    }

    @Test
    fun detect_finishedWithEmptyHand_emitsGameWin() {
        val old = defaultState(StateParams(phase = MatchPhase.PLAYER_TURN))
        val new = defaultState(StateParams(phase = MatchPhase.FINISHED,
        playerHand = emptyList(),
        resultSummary = ResultSummary(
            winnerName = "You",
            rankingLines = emptyList(),
        ),))

        val events = detector.detect(old, new)

        assertEquals(listOf(AudioEvent.Sfx.GameWin), events)
    }

    @Test
    fun detect_finishedWithCardsLeft_emitsGameLose() {
        val old = defaultState(StateParams(phase = MatchPhase.PLAYER_TURN))
        val new = defaultState(StateParams(phase = MatchPhase.FINISHED,
        playerHand = listOf(
            com.example.chudadi.model.game.entity.Card(
                rank = com.example.chudadi.model.game.entity.CardRank.THREE,
                suit = com.example.chudadi.model.game.entity.CardSuit.SPADES,
            ),
        ),
        resultSummary = ResultSummary(
            winnerName = "AI 1",
            rankingLines = emptyList(),
        ),))

        val events = detector.detect(old, new)

        assertEquals(listOf(AudioEvent.Sfx.GameLose), events)
    }

    @Test
    fun detect_noChange_emitsNothing() {
        val state = defaultState(StateParams())

        val events = detector.detect(state, state)

        assertEquals(emptyList<AudioEvent>(), events)
    }

    @Test
    fun detect_combinedEvents_emitsMultiple() {
        val old = defaultState(StateParams(phase = MatchPhase.PLAYER_TURN,
        isHumanTurn = false,
        tablePlays = listOf(dummyTablePlay(0)),))
        val new = defaultState(StateParams(phase = MatchPhase.PLAYER_TURN,
        isHumanTurn = true,
        tablePlays = listOf(dummyTablePlay(0), dummyTablePlay(1)),))

        val events = detector.detect(old, new)

        assertEquals(2, events.size)
        assertEquals(AudioEvent.Sfx.CardPlay, events[0])
        assertEquals(AudioEvent.Sfx.YourTurn, events[1])
    }

    // -- Edge case tests --

    @Test
    fun detect_tablePlaysIncreaseBy2_emitsSingleCardPlay() {
        val old = defaultState(StateParams(tablePlays = emptyList()))
        val new = defaultState(StateParams(tablePlays = listOf(dummyTablePlay(0), dummyTablePlay(1))))

        val events = detector.detect(old, new)

        assertEquals(listOf(AudioEvent.Sfx.CardPlay), events)
    }

    @Test
    fun detect_tablePlaysDecrease_emitsNothing() {
        val old = defaultState(StateParams(tablePlays = listOf(dummyTablePlay(0), dummyTablePlay(1))))
        val new = defaultState(StateParams(tablePlays = emptyList()))

        val events = detector.detect(old, new)

        assertEquals(emptyList<AudioEvent>(), events)
    }

    @Test
    fun detect_finishedToNotStarted_emitsNothing() {
        val old = defaultState(StateParams(phase = MatchPhase.FINISHED))
        val new = defaultState(StateParams(phase = MatchPhase.NOT_STARTED))

        val events = detector.detect(old, new)

        assertEquals(emptyList<AudioEvent>(), events)
    }

    @Test
    fun detect_notStartedToDealing_emitsNothing() {
        val old = defaultState(StateParams(phase = MatchPhase.NOT_STARTED))
        val new = defaultState(StateParams(phase = MatchPhase.DEALING))

        val events = detector.detect(old, new)

        assertEquals(emptyList<AudioEvent>(), events)
    }

    @Test
    fun detect_dealingToPlayerTurn_emitsNothing() {
        val old = defaultState(StateParams(phase = MatchPhase.DEALING))
        val new = defaultState(StateParams(phase = MatchPhase.PLAYER_TURN))

        val events = detector.detect(old, new)

        assertEquals(emptyList<AudioEvent>(), events)
    }

    @Test
    fun detect_roundResetToPlayerTurn_emitsNothing() {
        val old = defaultState(StateParams(phase = MatchPhase.ROUND_RESET))
        val new = defaultState(StateParams(phase = MatchPhase.PLAYER_TURN))

        val events = detector.detect(old, new)

        assertEquals(emptyList<AudioEvent>(), events)
    }

    @Test
    fun detect_humanTurnButNotPlayerTurnPhase_emitsNothing() {
        val old = defaultState(StateParams(isHumanTurn = false,
        phase = MatchPhase.FINISHED,))
        val new = defaultState(StateParams(isHumanTurn = true,
        phase = MatchPhase.FINISHED,))

        val events = detector.detect(old, new)

        assertEquals(emptyList<AudioEvent>(), events)
    }

    @Test
    fun detect_consecutiveHumanTurnToggle_emitsTwoYourTurn() {
        // false -> true (emit), then true -> false (no emit), then false -> true (emit)
        val state0 = defaultState(StateParams(isHumanTurn = false, phase = MatchPhase.PLAYER_TURN))
        val state1 = defaultState(StateParams(isHumanTurn = true, phase = MatchPhase.PLAYER_TURN))
        val state2 = defaultState(StateParams(isHumanTurn = false, phase = MatchPhase.PLAYER_TURN))
        val state3 = defaultState(StateParams(isHumanTurn = true, phase = MatchPhase.PLAYER_TURN))

        val events1 = detector.detect(state0, state1)
        val events2 = detector.detect(state1, state2)
        val events3 = detector.detect(state2, state3)

        assertEquals(listOf(AudioEvent.Sfx.YourTurn), events1)
        assertEquals(emptyList<AudioEvent>(), events2)
        assertEquals(listOf(AudioEvent.Sfx.YourTurn), events3)
    }

    @Test
    fun detect_finishedToPlayerTurnWithHumanTurn_emitsYourTurn() {
        val old = defaultState(StateParams(phase = MatchPhase.FINISHED,
        isHumanTurn = false,))
        val new = defaultState(StateParams(phase = MatchPhase.PLAYER_TURN,
        isHumanTurn = true,))

        val events = detector.detect(old, new)

        assertEquals(listOf(AudioEvent.Sfx.YourTurn), events)
    }

    // -- Helpers --

    private data class StateParams(
        val phase: MatchPhase = MatchPhase.NOT_STARTED,
        val playerHand: List<com.example.chudadi.model.game.entity.Card> = emptyList(),
        val tablePlays: List<TablePlaySummary> = emptyList(),
        val isHumanTurn: Boolean = false,
        val resultSummary: ResultSummary? = null,
        val localSeatId: Int = 0,
        val opponentSummaries: List<OpponentSummary> = emptyList(),
    )

    private fun defaultState(params: StateParams = StateParams()): MatchUiState {
        return MatchUiState(
            phase = params.phase,
            playerHand = params.playerHand,
            tablePlays = params.tablePlays,
            isHumanTurn = params.isHumanTurn,
            resultSummary = params.resultSummary,
            localSeatId = params.localSeatId,
            opponentSummaries = params.opponentSummaries,
        )
    }

    private fun dummyTablePlay(stackOrder: Int): TablePlaySummary {
        return TablePlaySummary(
            playId = "play-$stackOrder",
            ownerViewSeat = ViewSeat.SELF,
            ownerName = "Player",
            combinationLabel = "单张",
            cardLabels = listOf("A♠"),
            stackOrder = stackOrder,
        )
    }
}
