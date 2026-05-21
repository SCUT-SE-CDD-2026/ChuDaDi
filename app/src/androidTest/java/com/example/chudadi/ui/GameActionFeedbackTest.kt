package com.example.chudadi.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.example.chudadi.R
import com.example.chudadi.model.game.entity.Card
import com.example.chudadi.model.game.entity.CardRank
import com.example.chudadi.model.game.entity.CardSuit
import com.example.chudadi.model.game.entity.MatchPhase
import com.example.chudadi.model.game.snapshot.MatchUiState
import com.example.chudadi.model.game.snapshot.OpponentSummary
import com.example.chudadi.model.game.snapshot.TablePlaySummary
import com.example.chudadi.model.game.snapshot.ViewSeat
import com.example.chudadi.ui.game.GameScreen
import com.example.chudadi.ui.game.GameScreenActions
import org.junit.Rule
import org.junit.Test

class GameActionFeedbackTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun gameScreen_showsActionFeedbackAndDisabledButtons() {
        composeRule.setContent {
            GameScreen(
                uiState = MatchUiState(
                    phase = MatchPhase.PLAYER_TURN,
                    playerHand = listOf(Card(rank = CardRank.THREE, suit = CardSuit.DIAMONDS)),
                    opponentSummaries = listOf(
                        OpponentSummary(
                            viewSeat = ViewSeat.LEFT,
                            authoritySeatId = 1,
                            displayName = "AI 1",
                            avatarResId = R.drawable.avatar,
                            remainingCards = 5,
                            isCurrentActor = false,
                            hasPassed = true,
                        ),
                    ),
                    currentActorName = "You",
                    currentTablePlay = TablePlaySummary(
                        playId = "test-play",
                        ownerViewSeat = ViewSeat.LEFT,
                        ownerName = "AI 1",
                        combinationLabel = "Single",
                        cardLabels = listOf("5♠"),
                        stackOrder = 0,
                    ),
                    lastActionMessage = "当前牌不够大",
                    canSubmitPlay = false,
                    canPass = false,
                    isHumanTurn = true,
                ),
                actions = GameScreenActions(
                    onToggleCardSelection = {},
                    onClearSelection = {},
                    onSubmitSelectedCards = {},
                    onPassTurn = {},
                ),
            )
        }

        composeRule.onNodeWithTag(ComposeTestTags.ACTION_MESSAGE).assertIsDisplayed()
        composeRule.onNodeWithTag(ComposeTestTags.SUBMIT_BUTTON).assertIsNotEnabled()
        composeRule.onNodeWithTag(ComposeTestTags.PASS_BUTTON).assertIsNotEnabled()
    }
}
