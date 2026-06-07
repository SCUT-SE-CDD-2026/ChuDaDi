package com.example.chudadi.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.example.chudadi.controller.game.LocalMatchViewModel
import com.example.chudadi.data.repository.PlayerPreferencesRepository
import com.example.chudadi.data.repository.ReconnectSessionRepository
import com.example.chudadi.model.game.engine.GameEngine
import com.example.chudadi.model.game.entity.Card
import com.example.chudadi.model.game.entity.CardRank
import com.example.chudadi.model.game.entity.CardSuit
import com.example.chudadi.model.game.entity.Match
import com.example.chudadi.model.game.entity.MatchPhase
import com.example.chudadi.model.game.entity.RoundResult
import com.example.chudadi.model.game.entity.RoundScore
import com.example.chudadi.model.game.entity.ScoreSummary
import com.example.chudadi.model.game.entity.Seat
import com.example.chudadi.model.game.entity.SeatControllerType
import com.example.chudadi.model.game.entity.SeatStatus
import com.example.chudadi.model.game.entity.TrickState
import com.example.chudadi.model.game.rule.GameRuleSet
import com.example.chudadi.navigation.ChuDaDiNavGraph
import com.example.chudadi.network.room.BluetoothRoomRepository
import com.example.chudadi.ui.room.RoomAction
import com.example.chudadi.ui.room.RoomAiDifficulty
import com.example.chudadi.ui.room.RoomViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test

class ResultFlowTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun returnToRoomFromResult_navigatesBackToRoom() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val playerPreferencesRepository = PlayerPreferencesRepository(context)
        val reconnectSessionRepository = ReconnectSessionRepository(context)
        val bluetoothRoomRepository = BluetoothRoomRepository(context, reconnectSessionRepository)
        val roomViewModel = RoomViewModel(
            playerPrefsRepository = playerPreferencesRepository,
            bluetoothRoomRepository = bluetoothRoomRepository,
            reconnectSessionRepository = reconnectSessionRepository,
        ).apply {
            runBlocking { createHostRoom(hostDeviceName = "Test Device") }
dispatch(RoomAction.AddAiToSlot(1, RoomAiDifficulty.RULE_NORMAL))
dispatch(RoomAction.AddAiToSlot(2, RoomAiDifficulty.RULE_NORMAL))
dispatch(RoomAction.AddAiToSlot(3, RoomAiDifficulty.RULE_NORMAL))
        }

        composeRule.setContent {
            ChuDaDiNavGraph(
                viewModel = LocalMatchViewModel(engine = ScriptedGameEngine(finishedMatch())),
                roomViewModel = roomViewModel,
                playerPreferencesRepository = playerPreferencesRepository,
                localDeviceName = "Test Device",
                onRequestBluetoothEnable = { onComplete -> onComplete() },
                onRequestBluetoothPermissions = { onComplete -> onComplete() },
            )
        }

        composeRule.onNodeWithText("创建房间").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(ComposeTestTags.START_GAME_BUTTON).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(ComposeTestTags.RESULT_SCREEN).assertIsDisplayed()

        composeRule.onNodeWithTag(ComposeTestTags.RETURN_TO_ROOM_BUTTON).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(ComposeTestTags.ROOM_SCREEN).assertIsDisplayed()
    }

    private class ScriptedGameEngine(
        private vararg val scriptedMatches: Match,
    ) : GameEngine() {
        private var cursor = 0

        override fun startLocalMatch(ruleSet: GameRuleSet): Match {
            val match = scriptedMatches[cursor.coerceAtMost(scriptedMatches.lastIndex)]
            if (cursor < scriptedMatches.lastIndex) {
                cursor++
            }
            return match
        }
    }

    companion object {
        private fun finishedMatch(): Match {
            return Match(
                matchId = "finished-match",
                ruleSet = GameRuleSet.SOUTHERN,
                phase = MatchPhase.FINISHED,
                seats = baseSeats(),
                activeSeatIndex = 0,
                trickState = TrickState(
                    leadSeatIndex = 0,
                    lastWinningSeatIndex = 0,
                    currentCombination = null,
                    roundNumber = 1,
                ),
                playHistory = listOf("You win"),
                result = RoundResult(
                    winnerSeatIndex = 0,
                    ranking = listOf(0, 1, 2, 3),
                    scoreSummary = ScoreSummary(
                        summaryLines = listOf(
                            "1. You (0 cards left)",
                            "2. AI 1 (1 cards left)",
                            "3. AI 2 (1 cards left)",
                            "4. AI 3 (1 cards left)",
                        ),
                        roundScores = listOf(
                            RoundScore(
                                seatId = 0,
                                playerName = "You",
                                remainingCards = 0,
                                roundScore = 3,
                            ),
                            RoundScore(
                                seatId = 1,
                                playerName = "AI 1",
                                remainingCards = 1,
                                roundScore = -1,
                            ),
                            RoundScore(
                                seatId = 2,
                                playerName = "AI 2",
                                remainingCards = 1,
                                roundScore = -1,
                            ),
                            RoundScore(
                                seatId = 3,
                                playerName = "AI 3",
                                remainingCards = 1,
                                roundScore = -1,
                            ),
                        ),
                    ),
                ),
            )
        }

        private fun baseSeats(): List<Seat> {
            return listOf(
                Seat(
                    seatId = 0,
                    displayName = "You",
                    controllerType = SeatControllerType.HUMAN,
                    hand = emptyList(),
                    status = SeatStatus.FINISHED,
                    finishOrder = 1,
                ),
                Seat(
                    seatId = 1,
                    displayName = "AI 1",
                    controllerType = SeatControllerType.RULE_BASED_AI,
                    hand = listOf(Card(rank = CardRank.FOUR, suit = CardSuit.CLUBS)),
                    status = SeatStatus.FINISHED,
                    finishOrder = 2,
                ),
                Seat(
                    seatId = 2,
                    displayName = "AI 2",
                    controllerType = SeatControllerType.RULE_BASED_AI,
                    hand = listOf(Card(rank = CardRank.FIVE, suit = CardSuit.HEARTS)),
                    status = SeatStatus.FINISHED,
                    finishOrder = 3,
                ),
                Seat(
                    seatId = 3,
                    displayName = "AI 3",
                    controllerType = SeatControllerType.RULE_BASED_AI,
                    hand = listOf(Card(rank = CardRank.SIX, suit = CardSuit.SPADES)),
                    status = SeatStatus.FINISHED,
                    finishOrder = 4,
                ),
            )
        }
    }
}
