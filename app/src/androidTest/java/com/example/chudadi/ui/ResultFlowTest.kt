package com.example.chudadi.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.chudadi.controller.game.LocalMatchViewModel
import com.example.chudadi.model.game.engine.GameEngine
import com.example.chudadi.model.game.entity.Card
import com.example.chudadi.model.game.entity.CardRank
import com.example.chudadi.model.game.entity.CardSuit
import com.example.chudadi.model.game.entity.Match
import com.example.chudadi.model.game.entity.MatchPhase
import com.example.chudadi.model.game.entity.RoundResult
import com.example.chudadi.model.game.entity.RoundScore
import com.example.chudadi.model.game.entity.Seat
import com.example.chudadi.model.game.entity.SeatControllerType
import com.example.chudadi.model.game.entity.SeatStatus
import com.example.chudadi.model.game.entity.ScoreSummary
import com.example.chudadi.model.game.entity.TrickState
import com.example.chudadi.model.game.rule.GameRuleSet
import com.example.chudadi.navigation.ChuDaDiNavGraph
import com.example.chudadi.ui.room.AiDifficulty
import com.example.chudadi.ui.room.RoomAction
import com.example.chudadi.ui.room.RoomViewModel
import org.junit.Rule
import org.junit.Test

class ResultFlowTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun restartFromResult_navigatesToGame() {
        composeRule.setContent {
            ChuDaDiNavGraph(
                viewModel = LocalMatchViewModel(engine = ScriptedGameEngine(finishedMatch(), ongoingMatch())),
                roomViewModel = filledRoomViewModel(),
            )
        }

        composeRule.onNodeWithText("创建房间").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(ComposeTestTags.START_GAME_BUTTON).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(ComposeTestTags.RESULT_SCREEN).assertIsDisplayed()

        composeRule.onNodeWithTag(ComposeTestTags.RESTART_BUTTON).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(ComposeTestTags.GAME_SCREEN).assertIsDisplayed()
    }

    @Test
    fun exitFromResult_navigatesBackToHome() {
        composeRule.setContent {
            ChuDaDiNavGraph(
                viewModel = LocalMatchViewModel(engine = ScriptedGameEngine(finishedMatch())),
                roomViewModel = filledRoomViewModel(),
            )
        }

        composeRule.onNodeWithText("创建房间").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(ComposeTestTags.START_GAME_BUTTON).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(ComposeTestTags.RESULT_SCREEN).assertIsDisplayed()

        composeRule.onNodeWithTag(ComposeTestTags.EXIT_BUTTON).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(ComposeTestTags.HOME_SCREEN).assertIsDisplayed()
    }

    private fun filledRoomViewModel(): RoomViewModel {
        val vm = RoomViewModel()
        vm.dispatch(RoomAction.AddAiToSlot(1, AiDifficulty.RULE_BASED))
        vm.dispatch(RoomAction.AddAiToSlot(2, AiDifficulty.RULE_BASED))
        vm.dispatch(RoomAction.AddAiToSlot(3, AiDifficulty.RULE_BASED))
        return vm
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
                    passCount = 0,
                    roundNumber = 1,
                ),
                playHistory = listOf("You win"),
                totalBombCount = 0,
                result =
                    RoundResult(
                        winnerSeatIndex = 0,
                        ranking = listOf(0, 1, 2, 3),
                        scoreSummary = ScoreSummary(
                            summaryLines = listOf("1. You (0 cards left)"),
                            bombCount = 0,
                            roundScores = listOf(
                                RoundScore(
                                    seatId = 0,
                                    playerName = "You",
                                    remainingCards = 0,
                                    roundScore = 3,
                                ),
                            ),
                        ),
                    ),
            )
        }

        private fun ongoingMatch(): Match {
            return Match(
                matchId = "ongoing-match",
                ruleSet = GameRuleSet.SOUTHERN,
                phase = MatchPhase.PLAYER_TURN,
                seats = baseSeats(),
                activeSeatIndex = 0,
                trickState = TrickState(
                    leadSeatIndex = 0,
                    lastWinningSeatIndex = 0,
                    currentCombination = null,
                    passCount = 0,
                    roundNumber = 1,
                ),
                playHistory = listOf("You lead"),
                totalBombCount = 0,
                result = null,
            )
        }

        private fun baseSeats(): List<Seat> {
            return listOf(
                Seat(
                    seatId = 0,
                    displayName = "You",
                    controllerType = SeatControllerType.HUMAN,
                    hand = listOf(Card(rank = CardRank.THREE, suit = CardSuit.DIAMONDS)),
                    status = SeatStatus.ACTIVE,
                ),
                Seat(
                    seatId = 1,
                    displayName = "AI 1",
                    controllerType = SeatControllerType.RULE_BASED_AI,
                    hand = listOf(Card(rank = CardRank.FOUR, suit = CardSuit.CLUBS)),
                    status = SeatStatus.ACTIVE,
                ),
                Seat(
                    seatId = 2,
                    displayName = "AI 2",
                    controllerType = SeatControllerType.RULE_BASED_AI,
                    hand = listOf(Card(rank = CardRank.FIVE, suit = CardSuit.HEARTS)),
                    status = SeatStatus.ACTIVE,
                ),
                Seat(
                    seatId = 3,
                    displayName = "AI 3",
                    controllerType = SeatControllerType.RULE_BASED_AI,
                    hand = listOf(Card(rank = CardRank.SIX, suit = CardSuit.SPADES)),
                    status = SeatStatus.ACTIVE,
                ),
            )
        }
    }
}


class ResultFlowTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun restartFromResult_navigatesToGame() {
        composeRule.setContent {
            ChuDaDiNavGraph(
                viewModel = LocalMatchViewModel(engine = ScriptedGameEngine(finishedMatch(), ongoingMatch())),
                roomViewModel = filledRoomViewModel(),
            )
        }

        composeRule.onNodeWithText("创建房间").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(ComposeTestTags.START_GAME_BUTTON).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(ComposeTestTags.RESULT_SCREEN).assertIsDisplayed()

        composeRule.onNodeWithTag(ComposeTestTags.RESTART_BUTTON).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(ComposeTestTags.GAME_SCREEN).assertIsDisplayed()
    }

    @Test
    fun exitFromResult_navigatesBackToHome() {
        composeRule.setContent {
            ChuDaDiNavGraph(
                viewModel = LocalMatchViewModel(engine = ScriptedGameEngine(finishedMatch())),
                roomViewModel = filledRoomViewModel(),
            )
        }

        composeRule.onNodeWithText("创建房间").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(ComposeTestTags.START_GAME_BUTTON).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(ComposeTestTags.RESULT_SCREEN).assertIsDisplayed()

        composeRule.onNodeWithTag(ComposeTestTags.EXIT_BUTTON).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(ComposeTestTags.HOME_SCREEN).assertIsDisplayed()
    }

    private fun filledRoomViewModel(): RoomViewModel {
        val vm = RoomViewModel()
        vm.dispatch(RoomAction.AddAiToSlot(1, AiDifficulty.RULE_BASED))
        vm.dispatch(RoomAction.AddAiToSlot(2, AiDifficulty.RULE_BASED))
        vm.dispatch(RoomAction.AddAiToSlot(3, AiDifficulty.RULE_BASED))
        return vm
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
                    passCount = 0,
                    roundNumber = 1,
                ),
                playHistory = listOf("You win"),
                totalBombCount = 0,
                result =
                    RoundResult(
                        winnerSeatIndex = 0,
                        ranking = listOf(0, 1, 2, 3),
                        scoreSummary = ScoreSummary(
                            summaryLines = listOf("1. You (0 cards left)"),
                            bombCount = 0,
                            roundScores = listOf(
                                RoundScore(
                                    seatId = 0,
                                    playerName = "You",
                                    remainingCards = 0,
                                    roundScore = 3,
                                ),
                            ),
                        ),
                    ),
            )
        }

        private fun ongoingMatch(): Match {
            return Match(
                matchId = "ongoing-match",
                ruleSet = GameRuleSet.SOUTHERN,
                phase = MatchPhase.PLAYER_TURN,
                seats = baseSeats(),
                activeSeatIndex = 0,
                trickState = TrickState(
                    leadSeatIndex = 0,
                    lastWinningSeatIndex = 0,
                    currentCombination = null,
                    passCount = 0,
                    roundNumber = 1,
                ),
                playHistory = listOf("You lead"),
                totalBombCount = 0,
                result = null,
            )
        }

        private fun baseSeats(): List<Seat> {
            return listOf(
                Seat(
                    seatId = 0,
                    displayName = "You",
                    controllerType = SeatControllerType.HUMAN,
                    hand = listOf(Card(rank = CardRank.THREE, suit = CardSuit.DIAMONDS)),
                    status = SeatStatus.ACTIVE,
                ),
                Seat(
                    seatId = 1,
                    displayName = "AI 1",
                    controllerType = SeatControllerType.RULE_BASED_AI,
                    hand = listOf(Card(rank = CardRank.FOUR, suit = CardSuit.CLUBS)),
                    status = SeatStatus.ACTIVE,
                ),
                Seat(
                    seatId = 2,
                    displayName = "AI 2",
                    controllerType = SeatControllerType.RULE_BASED_AI,
                    hand = listOf(Card(rank = CardRank.FIVE, suit = CardSuit.HEARTS)),
                    status = SeatStatus.ACTIVE,
                ),
                Seat(
                    seatId = 3,
                    displayName = "AI 3",
                    controllerType = SeatControllerType.RULE_BASED_AI,
                    hand = listOf(Card(rank = CardRank.SIX, suit = CardSuit.SPADES)),
                    status = SeatStatus.ACTIVE,
                ),
            )
        }
    }
}
