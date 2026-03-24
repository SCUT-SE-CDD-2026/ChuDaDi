@file:Suppress("TooManyFunctions")

package com.example.chudadi.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.chudadi.R
import com.example.chudadi.model.game.entity.Card as GameCard
import com.example.chudadi.model.game.snapshot.MatchUiState
import com.example.chudadi.model.game.snapshot.OpponentSummary
import com.example.chudadi.model.game.snapshot.TablePlaySummary
import com.example.chudadi.ui.ComposeTestTags

private val TableFelt = Color(0xFF163726)
private val TableBorder = Color(0xFF8D6A33)
private val WoodTint = Color(0xFF2A1B15)
private val SeatPanel = Color(0xFF2F201A)
private val SeatPanelBorder = Color(0xFF8C6A43)
private val CardFace = Color(0xFFF5EEDB)
private val CardStroke = Color(0xFFD9C9A6)
private val CardBack = Color(0xFF7D4B32)
private val CardRed = Color(0xFFB42318)
private val CardBlack = Color(0xFF2C1C13)
private const val LEFT_SEAT_ID = 1
private const val TOP_SEAT_ID = 2
private const val RIGHT_SEAT_ID = 3

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(
    uiState: MatchUiState,
    actions: GameScreenActions,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag(ComposeTestTags.GAME_SCREEN),
        bottomBar = {
            BottomActionBar(
                uiState = uiState,
                actions = actions,
            )
        },
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(WoodTint)
                .padding(innerPadding)
                .padding(start = 12.dp, top = 6.dp, end = 12.dp, bottom = 0.dp),
        ) {
            val tableHeight =
                when {
                    maxHeight < 420.dp -> 220.dp
                    maxHeight < 560.dp -> 280.dp
                    maxHeight < 680.dp -> 320.dp
                    maxHeight < 820.dp -> 356.dp
                    else -> 392.dp
                }

            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Bottom,
            ) {
                Spacer(modifier = Modifier.weight(1f))
                TableArena(
                    uiState = uiState,
                    modifier = Modifier.height(tableHeight),
                )
                Spacer(modifier = Modifier.height(2.dp))
                PlayerArea(
                    uiState = uiState,
                    onToggleCardSelection = actions.onToggleCardSelection,
                )
            }
        }
    }
}

@Composable
private fun TableArena(
    uiState: MatchUiState,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Color(0xFF241612))
            .padding(6.dp),
    ) {
        TableSurface(
            modifier = Modifier.fillMaxSize(),
        )
        uiState.opponentSummaries.firstOrNull { it.seatId == TOP_SEAT_ID }?.let { opponent ->
            OpponentSeatPanel(
                opponent = opponent,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 4.dp)
                    .offset(y = (-6).dp),
            )
        }
        uiState.opponentSummaries.firstOrNull { it.seatId == LEFT_SEAT_ID }?.let { opponent ->
            OpponentSeatPanel(
                opponent = opponent,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 2.dp)
                    .offset(x = (-6).dp, y = (-12).dp),
            )
        }
        uiState.opponentSummaries.firstOrNull { it.seatId == RIGHT_SEAT_ID }?.let { opponent ->
            OpponentSeatPanel(
                opponent = opponent,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 2.dp)
                    .offset(x = 6.dp, y = (-12).dp),
            )
        }
        val tablePlays =
            if (uiState.tablePlays.isNotEmpty()) {
                uiState.tablePlays
            } else {
                listOfNotNull(uiState.currentTablePlay)
            }
        tablePlays.forEach { tablePlay ->
            SeatTablePlay(
                tablePlay = tablePlay,
                tableWidth = maxWidth,
                tableHeight = maxHeight,
            )
        }
        uiState.lastActionMessage?.let { message ->
            ActionMessageBanner(
                message = message,
                modifier =
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 10.dp, top = 10.dp)
                        .testTag(ComposeTestTags.ACTION_MESSAGE),
            )
        }
    }
}

@Composable
private fun TableSurface(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(28.dp))
            .background(TableFelt)
            .border(width = 2.dp, color = TableBorder, shape = RoundedCornerShape(28.dp))
            .padding(16.dp),
    )
}

@Composable
private fun ActionMessageBanner(
    message: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xAA000000))
            .padding(horizontal = 8.dp, vertical = 5.dp),
    ) {
        Text(
            text = message,
            color = Color.White,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun BoxScope.SeatTablePlay(
    tablePlay: TablePlaySummary,
    tableWidth: Dp,
    tableHeight: Dp,
) {
    val sideX = tableWidth * 0.13f
    val sideY = tableHeight * 0.10f
    val topY = tableHeight * 0.24f
    val bottomY = tableHeight * 0.05f

    val alignment =
        when (tablePlay.ownerSeatId) {
            LEFT_SEAT_ID -> Alignment.CenterStart
            TOP_SEAT_ID -> Alignment.TopCenter
            RIGHT_SEAT_ID -> Alignment.CenterEnd
            else -> Alignment.BottomCenter
        }

    Row(
        modifier = Modifier
            .align(alignment)
            .offset(
                x =
                    when (tablePlay.ownerSeatId) {
                        LEFT_SEAT_ID -> sideX
                        RIGHT_SEAT_ID -> -sideX
                        else -> 0.dp
                    },
                y =
                    when (tablePlay.ownerSeatId) {
                        LEFT_SEAT_ID, RIGHT_SEAT_ID -> sideY
                        TOP_SEAT_ID -> topY
                        else -> -bottomY
                    },
            ),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        tablePlay.cardLabels.forEach { label ->
            TableCard(label = label)
        }
    }
}

@Composable
private fun OpponentSeatPanel(
    opponent: OpponentSummary,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(100.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(SeatPanel)
            .border(1.dp, SeatPanelBorder, RoundedCornerShape(14.dp))
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = opponent.displayName,
            color = Color(0xFFC6F6D5),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
        )
        Text(
            text = stringResource(R.string.opponent_cards_left, opponent.remainingCards),
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
        )
        OpponentCardBacks(cardCount = opponent.remainingCards)
        Text(
            text =
                when {
                    opponent.isCurrentActor -> stringResource(R.string.opponent_current_actor)
                    opponent.hasPassed -> stringResource(R.string.opponent_passed)
                    else -> stringResource(R.string.opponent_waiting)
                },
            color = Color(0xFFE7D7B1),
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun OpponentCardBacks(cardCount: Int) {
    val visibleCount = cardCount.coerceAtMost(6)
    Row(
        horizontalArrangement = Arrangement.spacedBy(1.dp),
        modifier = Modifier.wrapContentWidth(),
    ) {
        repeat(visibleCount) {
            Box(
                modifier = Modifier
                    .size(width = 9.dp, height = 15.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(CardBack)
                    .border(1.dp, CardStroke, RoundedCornerShape(2.dp)),
            )
        }
    }
}

@Composable
private fun PlayerArea(
    uiState: MatchUiState,
    onToggleCardSelection: (String) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(TableFelt)
            .border(2.dp, TableBorder, RoundedCornerShape(24.dp))
            .padding(horizontal = 12.dp, vertical = 14.dp),
    ) {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(uiState.playerHand, key = { it.id }) { card ->
                PlayerCardChip(
                    card = card,
                    isSelected = card.id in uiState.selectedCards,
                    enabled = uiState.isHumanTurn,
                    onToggle = { onToggleCardSelection(card.id) },
                )
            }
        }
    }
}

@Composable
private fun BottomActionBar(
    uiState: MatchUiState,
    actions: GameScreenActions,
) {
    Surface(
        color = WoodTint,
        tonalElevation = 2.dp,
        shadowElevation = 6.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 0.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            GameActionButtons(uiState = uiState, actions = actions)
        }
    }
}

@Composable
private fun GameActionButtons(
    uiState: MatchUiState,
    actions: GameScreenActions,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ActionButton(
            text = stringResource(R.string.clear_selection),
            enabled = uiState.selectedCards.isNotEmpty(),
            onClick = actions.onClearSelection,
            tag = ComposeTestTags.CLEAR_SELECTION_BUTTON,
        )
        ActionButton(
            text = stringResource(R.string.pass_turn),
            enabled = uiState.canPass,
            onClick = actions.onPassTurn,
            tag = ComposeTestTags.PASS_BUTTON,
        )
        ActionButton(
            text = stringResource(R.string.submit_play),
            enabled = uiState.canSubmitPlay,
            onClick = actions.onSubmitSelectedCards,
            tag = ComposeTestTags.SUBMIT_BUTTON,
            primary = true,
        )
    }
}

@Composable
private fun RowScope.ActionButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    tag: String,
    primary: Boolean = false,
) {
    val buttonModifier = Modifier.weight(1f).testTag(tag)
    if (primary) {
        Button(
            modifier = buttonModifier,
            onClick = onClick,
            enabled = enabled,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    } else {
        OutlinedButton(
            modifier = buttonModifier,
            onClick = onClick,
            enabled = enabled,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun TableCard(label: String) {
    Box(
        modifier = Modifier
            .size(width = 42.dp, height = 58.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(CardFace)
            .border(1.dp, CardStroke, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        CardFaceContent(
            label = label,
            compact = true,
        )
    }
}

@Composable
private fun PlayerCardChip(
    card: GameCard,
    isSelected: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    val modifier =
        Modifier
            .size(width = 60.dp, height = 86.dp)
            .offset(y = if (isSelected) (-10).dp else 0.dp)
            .clip(shape)
            .background(CardFace)
            .border(
                width = 2.dp,
                color = if (isSelected) TableBorder else CardStroke,
                shape = shape,
            )
            .clickable(enabled = enabled, onClick = onToggle)
            .semantics {
                contentDescription = card.displayName
            }
            .testTag("${ComposeTestTags.PLAYER_CARD_PREFIX}${card.id}")

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        CardFaceContent(
            label = card.displayName,
            compact = false,
        )
    }
}

@Composable
private fun CardFaceContent(
    label: String,
    compact: Boolean = false,
) {
    val suit = label.takeLast(1)
    val rank = label.dropLast(1)
    val suitColor = if (suit == "\u2665" || suit == "\u2666") CardRed else CardBlack

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = rank,
            color = CardBlack,
            style =
                if (compact) {
                    MaterialTheme.typography.bodyMedium
                } else {
                    MaterialTheme.typography.titleLarge
                },
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = suit,
            color = suitColor,
            style =
                if (compact) {
                    MaterialTheme.typography.bodyLarge
                } else {
                    MaterialTheme.typography.titleLarge
                },
            fontWeight = FontWeight.Bold,
        )
    }
}
