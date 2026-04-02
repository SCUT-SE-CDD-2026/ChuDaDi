@file:Suppress("TooManyFunctions")

package com.example.chudadi.ui.game

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.chudadi.R
import com.example.chudadi.model.game.entity.Card as GameCard
import com.example.chudadi.model.game.snapshot.MatchUiState
import com.example.chudadi.model.game.snapshot.OpponentSummary
import com.example.chudadi.model.game.snapshot.TablePlaySummary
import com.example.chudadi.ui.ComposeTestTags
import com.example.chudadi.ui.theme.CardCenterSuitTextStyle
import com.example.chudadi.ui.theme.CardRankTextStyle
import com.example.chudadi.ui.theme.CardSuitTextStyle
import com.example.chudadi.ui.theme.CompactCardCenterSuitTextStyle
import com.example.chudadi.ui.theme.CompactCardRankTextStyle
import com.example.chudadi.ui.theme.CompactCardSuitTextStyle

private val TableFelt = Color(0xFF163726)
private val TableBorder = Color(0xFF8D6A33)
private val TableOuter = Color(0xFF241612)
private val WoodTint = Color(0xFF2A1B15)
private val InfoBadge = Color(0xAA221A18)
private val InfoBadgeStroke = Color(0x55F7E8C2)
private val CardFace = Color(0xFFF7F1E4)
private val CardFaceHighlight = Color(0xFFFFFBF0)
private val CardStroke = Color(0xFFD9C9A6)
private val CardGlow = Color(0x20F7E2A4)
private val CardGlowFill = Color(0x26F7E2A4)
private val CardShadow = Color(0x2E180E09)
private val CardShadowFill = Color(0x14180E09)
private val CardShadowSelected = Color(0x5222140E)
private val CardBack = Color(0xFF7D4B32)
private val CardBackHighlight = Color(0xFF9E6A4E)
private val CardRed = Color(0xFFB42318)
private val CardBlack = Color(0xFF2C1C13)
private val HumanPlayOffsetX = 26.dp
private val OpponentPlayOffsetX = 26.dp
private const val LEFT_SEAT_ID = 1
private const val TOP_SEAT_ID = 2
private const val RIGHT_SEAT_ID = 3
private const val HUMAN_SEAT_ID = 0
private val TableShape = RoundedCornerShape(28.dp)
private val CardShape = RoundedCornerShape(10.dp)

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
        containerColor = WoodTint,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(WoodTint)
                .padding(0.dp),
        ) {
            val layoutSpec = rememberGameLayoutSpec(maxWidth = maxWidth, maxHeight = maxHeight)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(layoutSpec.outerPadding)
                    .clip(TableShape)
                    .background(TableOuter)
                    .padding(6.dp),
            ) {
                TableSurface(
                    layoutSpec = layoutSpec,
                    modifier = Modifier.fillMaxSize(),
                )
                GameTableLayout(
                    uiState = uiState,
                    actions = actions,
                    layoutSpec = layoutSpec,
                    modifier = Modifier.fillMaxSize(),
                )
                uiState.lastActionMessage?.let { message ->
                    ActionMessageBanner(
                        message = message,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(
                                start = layoutSpec.tableInnerHorizontalPadding,
                                top = layoutSpec.tableInnerVerticalPadding,
                            )
                            .testTag(ComposeTestTags.ACTION_MESSAGE),
                    )
                }
            }
        }
    }
}

@Composable
private fun TableSurface(
    layoutSpec: GameLayoutSpec,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(TableShape)
            .background(TableFelt)
            .border(width = 2.dp, color = TableBorder, shape = TableShape)
            .padding(
                horizontal = layoutSpec.tableInnerHorizontalPadding,
                vertical = layoutSpec.tableInnerVerticalPadding,
            ),
    )
}

@Composable
private fun GameTableLayout(
    uiState: MatchUiState,
    actions: GameScreenActions,
    layoutSpec: GameLayoutSpec,
    modifier: Modifier = Modifier,
) {
    val topOpponent = uiState.opponentSummaries.firstOrNull { it.seatId == TOP_SEAT_ID }
    val leftOpponent = uiState.opponentSummaries.firstOrNull { it.seatId == LEFT_SEAT_ID }
    val rightOpponent = uiState.opponentSummaries.firstOrNull { it.seatId == RIGHT_SEAT_ID }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(layoutSpec.areaSpacing.coerceAtMost(6.dp)),
    ) {
        TopSeatArea(
            opponent = topOpponent,
            layoutSpec = layoutSpec,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = layoutSpec.topAreaHeight),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .heightIn(min = layoutSpec.middleAreaMinHeight),
            horizontalArrangement = Arrangement.spacedBy(layoutSpec.areaSpacing),
            verticalAlignment = Alignment.Top,
        ) {
            SideSeatArea(
                opponent = leftOpponent,
                seatSide = SeatSide.Left,
                layoutSpec = layoutSpec,
                modifier = Modifier
                    .width(layoutSpec.sideSeatWidth)
                    .fillMaxHeight(),
            )
            CenterPlayArea(
                uiState = uiState,
                layoutSpec = layoutSpec,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
            SideSeatArea(
                opponent = rightOpponent,
                seatSide = SeatSide.Right,
                layoutSpec = layoutSpec,
                modifier = Modifier
                    .width(layoutSpec.sideSeatWidth)
                    .fillMaxHeight(),
            )
        }
        BottomHandArea(
            uiState = uiState,
            actions = actions,
            layoutSpec = layoutSpec,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = layoutSpec.bottomAreaMinHeight)
                .heightIn(max = layoutSpec.bottomAreaHeight + 16.dp),
        )
    }
}

@Composable
private fun TopSeatArea(
    opponent: OpponentSummary?,
    layoutSpec: GameLayoutSpec,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.TopCenter,
    ) {
        opponent?.let {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(layoutSpec.areaSpacing),
            ) {
                OpponentInfoBadge(
                    opponent = it,
                    layoutSpec = layoutSpec,
                    alignEnd = true,
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    OpponentAvatar(opponent = it, layoutSpec = layoutSpec)
                    OpponentCardStack(
                        cardCount = it.remainingCards,
                        orientation = StackOrientation.Horizontal,
                        layoutSpec = layoutSpec,
                    )
                }
            }
        }
    }
}

private enum class SeatSide {
    Left,
    Right,
}

@Composable
private fun SideSeatArea(
    opponent: OpponentSummary?,
    seatSide: SeatSide,
    layoutSpec: GameLayoutSpec,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = if (seatSide == SeatSide.Left) Alignment.CenterStart else Alignment.CenterEnd,
    ) {
        opponent?.let {
            val visuals = @Composable {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(layoutSpec.areaSpacing),
                ) {
                    if (seatSide == SeatSide.Right) {
                        OpponentCardStack(
                            cardCount = it.remainingCards,
                            orientation = StackOrientation.Vertical,
                            layoutSpec = layoutSpec,
                        )
                        OpponentMetaColumn(
                            opponent = it,
                            layoutSpec = layoutSpec,
                            alignEnd = true,
                        )
                    } else {
                        OpponentMetaColumn(
                            opponent = it,
                            layoutSpec = layoutSpec,
                            alignEnd = false,
                        )
                        OpponentCardStack(
                            cardCount = it.remainingCards,
                            orientation = StackOrientation.Vertical,
                            layoutSpec = layoutSpec,
                        )
                    }
                }
            }
            visuals()
        }
    }
}

@Composable
private fun OpponentMetaColumn(
    opponent: OpponentSummary,
    layoutSpec: GameLayoutSpec,
    alignEnd: Boolean,
) {
    Column(
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        OpponentAvatar(opponent = opponent, layoutSpec = layoutSpec)
        OpponentInfoBadge(
            opponent = opponent,
            layoutSpec = layoutSpec,
            alignEnd = alignEnd,
        )
    }
}

@Composable
private fun CenterPlayArea(
    uiState: MatchUiState,
    layoutSpec: GameLayoutSpec,
    modifier: Modifier = Modifier,
) {
    val tablePlays =
        if (uiState.tablePlays.isNotEmpty()) {
            uiState.tablePlays
        } else {
            listOfNotNull(uiState.currentTablePlay)
        }

    Column(
        modifier = modifier.widthIn(min = layoutSpec.centerPlayMinWidth),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(bottom = layoutSpec.centerPlayBottomClearance),
        ) {
            tablePlays.forEach { tablePlay ->
                TablePlaySlot(
                    tablePlay = tablePlay,
                    layoutSpec = layoutSpec,
                    modifier = Modifier.align(playAlignment(tablePlay.ownerSeatId)),
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

private fun playAlignment(ownerSeatId: Int): Alignment {
    return when (ownerSeatId) {
        LEFT_SEAT_ID -> Alignment.CenterStart
        TOP_SEAT_ID -> Alignment.TopCenter
        RIGHT_SEAT_ID -> Alignment.CenterEnd
        HUMAN_SEAT_ID -> Alignment.BottomCenter
        else -> Alignment.Center
    }
}

private fun playOffset(ownerSeatId: Int): Dp {
    return when (ownerSeatId) {
        HUMAN_SEAT_ID -> HumanPlayOffsetX
        TOP_SEAT_ID -> -OpponentPlayOffsetX
        else -> 0.dp
    }
}

@Composable
private fun TablePlaySlot(
    tablePlay: TablePlaySummary,
    layoutSpec: GameLayoutSpec,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            modifier = Modifier.offset(x = playOffset(tablePlay.ownerSeatId)),
            horizontalArrangement = Arrangement.spacedBy(layoutSpec.tableCardSpacing),
        ) {
            tablePlay.cardLabels.forEach { label ->
                TableCard(
                    label = label,
                    layoutSpec = layoutSpec,
                )
            }
        }
    }
}

@Composable
private fun BottomHandArea(
    uiState: MatchUiState,
    actions: GameScreenActions,
    layoutSpec: GameLayoutSpec,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(layoutSpec.areaSpacing),
    ) {
        GameActionButtons(
            uiState = uiState,
            actions = actions,
            layoutSpec = layoutSpec,
        )
        PlayerHandRow(
            uiState = uiState,
            layoutSpec = layoutSpec,
            onToggleCardSelection = actions.onToggleCardSelection,
        )
        Spacer(modifier = Modifier.height(4.dp))
    }
}

@Composable
private fun ActionMessageBanner(
    message: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(InfoBadge)
            .border(1.dp, InfoBadgeStroke, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            text = message,
            color = Color.White,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun OpponentAvatar(
    opponent: OpponentSummary,
    layoutSpec: GameLayoutSpec,
) {
    Image(
        painter = painterResource(opponent.avatarResId),
        contentDescription = opponent.displayName,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .size(layoutSpec.opponentAvatarSize)
            .clip(CircleShape)
            .background(Color(0xFFEEE2C0), CircleShape)
            .border(
                width = if (opponent.isCurrentActor) 2.dp else 1.dp,
                color = if (opponent.isCurrentActor) CardGlow else Color(0xAAFFF7DD),
                shape = CircleShape,
            ),
    )
}

@Composable
private fun OpponentInfoBadge(
    opponent: OpponentSummary,
    layoutSpec: GameLayoutSpec,
    alignEnd: Boolean,
) {
    val status =
        when {
            opponent.isCurrentActor -> stringResource(R.string.opponent_current_actor)
            opponent.hasPassed -> stringResource(R.string.opponent_passed)
            else -> stringResource(R.string.opponent_waiting)
        }

    Column(
        modifier = Modifier
            .widthIn(min = layoutSpec.opponentInfoMinWidth, max = layoutSpec.opponentInfoMaxWidth)
            .clip(RoundedCornerShape(14.dp))
            .background(InfoBadge)
            .border(1.dp, InfoBadgeStroke, RoundedCornerShape(14.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start,
    ) {
        Text(
            text = opponent.displayName,
            color = Color(0xFFC6F6D5),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            textAlign = if (alignEnd) TextAlign.End else TextAlign.Start,
        )
        Text(
            text = stringResource(R.string.opponent_cards_left, opponent.remainingCards),
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            textAlign = if (alignEnd) TextAlign.End else TextAlign.Start,
        )
        Text(
            text = status,
            color = Color(0xFFE7D7B1),
            style = MaterialTheme.typography.labelSmall,
            textAlign = if (alignEnd) TextAlign.End else TextAlign.Start,
        )
    }
}

private enum class StackOrientation {
    Horizontal,
    Vertical,
}

@Composable
private fun OpponentCardStack(
    cardCount: Int,
    orientation: StackOrientation,
    layoutSpec: GameLayoutSpec,
) {
    if (cardCount <= 0) {
        return
    }

    val width =
        if (orientation == StackOrientation.Horizontal) {
            layoutSpec.opponentHorizontalCardWidth +
                layoutSpec.opponentHorizontalCardStep * (cardCount - 1).coerceAtLeast(0)
        } else {
            layoutSpec.opponentVerticalCardWidth
        }
    val height =
        if (orientation == StackOrientation.Horizontal) {
            layoutSpec.opponentHorizontalCardHeight
        } else {
            layoutSpec.opponentVerticalCardHeight +
                layoutSpec.opponentVerticalCardStep * (cardCount - 1).coerceAtLeast(0)
        }

    Box(
        modifier = Modifier
            .width(width)
            .height(height),
    ) {
        repeat(cardCount) { index ->
            val cardModifier =
                if (orientation == StackOrientation.Horizontal) {
                    Modifier.offset(x = layoutSpec.opponentHorizontalCardStep * index)
                } else {
                    Modifier.offset(y = layoutSpec.opponentVerticalCardStep * index)
                }

            Box(
                modifier = cardModifier
                    .size(
                        width =
                            if (orientation == StackOrientation.Horizontal) {
                                layoutSpec.opponentHorizontalCardWidth
                            } else {
                                layoutSpec.opponentVerticalCardWidth
                            },
                        height =
                            if (orientation == StackOrientation.Horizontal) {
                                layoutSpec.opponentHorizontalCardHeight
                            } else {
                                layoutSpec.opponentVerticalCardHeight
                            },
                    )
                    .clip(RoundedCornerShape(3.dp))
                    .background(CardBack)
                    .border(1.dp, CardBackHighlight, RoundedCornerShape(3.dp)),
            )
        }
    }
}

@Composable
private fun PlayerHandRow(
    uiState: MatchUiState,
    layoutSpec: GameLayoutSpec,
    onToggleCardSelection: (String) -> Unit,
) {
    val cards = uiState.playerHand
    if (cards.isEmpty()) {
        Spacer(modifier = Modifier.height(layoutSpec.playerCardHeight))
        return
    }

    val topEffectAllowance = 18.dp
    val bottomEffectAllowance = 22.dp
    val handRowHeight =
        topEffectAllowance +
            layoutSpec.playerCardHeight +
            layoutSpec.selectedCardLift +
            bottomEffectAllowance

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = handRowHeight),
    ) {
        val layout = calculatePlayerHandLayout(
            input =
                PlayerHandLayoutInput(
                    cards = cards,
                    selectedCardIds = uiState.selectedCards,
                    availableWidth = maxWidth,
                    cardWidth = layoutSpec.playerCardWidth,
                    preferredStep = layoutSpec.playerCardPreferredStep,
                    minStep = layoutSpec.playerCardMinStep,
                    selectedShift = layoutSpec.selectedCardShift,
                    selectedLift = layoutSpec.selectedCardLift,
                ),
        )

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = topEffectAllowance - 32.dp)
                .width(layout.contentWidth.coerceAtMost(maxWidth))
                .height(handRowHeight),
        ) {
            cards.forEachIndexed { index, card ->
                val placement = layout.placements[index]
                PlayerCardChip(
                    uiState =
                        PlayerCardUiState(
                            card = card,
                            isSelected = card.id in uiState.selectedCards,
                            enabled = uiState.isHumanTurn,
                            width = layoutSpec.playerCardWidth,
                            height = layoutSpec.playerCardHeight,
                            onToggle = { onToggleCardSelection(card.id) },
                        ),
                    modifier = Modifier.offset(x = placement.x, y = placement.y),
                )
            }
        }
    }
}

private data class CardPlacement(
    val x: Dp,
    val y: Dp,
)

private data class PlayerHandLayoutResult(
    val contentWidth: Dp,
    val placements: List<CardPlacement>,
)

private data class PlayerHandLayoutInput(
    val cards: List<GameCard>,
    val selectedCardIds: Set<String>,
    val availableWidth: Dp,
    val cardWidth: Dp,
    val preferredStep: Dp,
    val minStep: Dp,
    val selectedShift: Dp,
    val selectedLift: Dp,
)

private data class ActionButtonUiState(
    val text: String,
    val enabled: Boolean,
    val onClick: () -> Unit,
    val tag: String,
    val minHeight: Dp,
    val primary: Boolean = false,
)

private data class PlayerCardUiState(
    val card: GameCard,
    val isSelected: Boolean,
    val enabled: Boolean,
    val width: Dp,
    val height: Dp,
    val onToggle: () -> Unit,
)

private fun calculatePlayerHandLayout(
    input: PlayerHandLayoutInput,
): PlayerHandLayoutResult {
    val overlapCount = input.cards.lastIndex.coerceAtLeast(0)
    val baseStep =
        if (overlapCount == 0) {
            0.dp
        } else {
            ((input.availableWidth - input.cardWidth) / overlapCount).coerceIn(
                input.minStep,
                input.preferredStep,
            )
        }
    val placements =
        input.cards.mapIndexed { index, card ->
            val selectedBeforeCount = input.cards.take(index).count { it.id in input.selectedCardIds }
            CardPlacement(
                x = baseStep * index + input.selectedShift * selectedBeforeCount,
                y = if (card.id in input.selectedCardIds) 0.dp else input.selectedLift,
            )
        }
    val contentWidth =
        input.cardWidth + baseStep * overlapCount + input.selectedShift * input.selectedCardIds.size
    return PlayerHandLayoutResult(
        contentWidth = contentWidth,
        placements = placements,
    )
}

@Composable
private fun GameActionButtons(
    uiState: MatchUiState,
    actions: GameScreenActions,
    layoutSpec: GameLayoutSpec,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Row(
            modifier = Modifier.widthIn(max = layoutSpec.actionButtonsMaxWidth),
            horizontalArrangement = Arrangement.spacedBy(layoutSpec.actionButtonSpacing),
        ) {
            ActionButton(
                uiState =
                    ActionButtonUiState(
                        text = stringResource(R.string.clear_selection),
                        enabled = uiState.selectedCards.isNotEmpty(),
                        onClick = actions.onClearSelection,
                        tag = ComposeTestTags.CLEAR_SELECTION_BUTTON,
                        minHeight = layoutSpec.actionButtonMinHeight,
                    ),
            )
            ActionButton(
                uiState =
                    ActionButtonUiState(
                        text = stringResource(R.string.pass_turn),
                        enabled = uiState.canPass,
                        onClick = actions.onPassTurn,
                        tag = ComposeTestTags.PASS_BUTTON,
                        minHeight = layoutSpec.actionButtonMinHeight,
                    ),
            )
            ActionButton(
                uiState =
                    ActionButtonUiState(
                        text = stringResource(R.string.submit_play),
                        enabled = uiState.canSubmitPlay,
                        onClick = actions.onSubmitSelectedCards,
                        tag = ComposeTestTags.SUBMIT_BUTTON,
                        minHeight = layoutSpec.actionButtonMinHeight,
                        primary = true,
                    ),
            )
        }
    }
}

@Composable
private fun RowScope.ActionButton(
    uiState: ActionButtonUiState,
) {
    val buttonModifier = Modifier
        .weight(1f, fill = true)
        .sizeIn(minHeight = uiState.minHeight)
        .testTag(uiState.tag)
    if (uiState.primary) {
        Button(
            modifier = buttonModifier,
            onClick = uiState.onClick,
            enabled = uiState.enabled,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xCC3A281F),
                contentColor = Color.White,
                disabledContainerColor = Color(0x883A281F),
                disabledContentColor = Color(0xFFD6D0C6),
            ),
        ) {
            Text(
                text = uiState.text,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    } else {
        OutlinedButton(
            modifier = buttonModifier,
            onClick = uiState.onClick,
            enabled = uiState.enabled,
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = Color(0xCC1D1A19),
                contentColor = Color.White,
                disabledContainerColor = Color(0x881D1A19),
                disabledContentColor = Color(0xFFD6D0C6),
            ),
        ) {
            Text(
                text = uiState.text,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun TableCard(
    label: String,
    layoutSpec: GameLayoutSpec,
) {
    Box(
        modifier = Modifier
            .size(width = layoutSpec.tableCardWidth, height = layoutSpec.tableCardHeight)
            .clip(CardShape)
            .background(CardFace)
            .border(1.dp, CardStroke, CardShape),
    ) {
        CardFaceContent(
            label = label,
            compact = true,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun PlayerCardChip(
    uiState: PlayerCardUiState,
    modifier: Modifier = Modifier,
) {
    val shadowColor = if (uiState.isSelected) CardShadowSelected else CardShadow
    val cardShadowElevation = if (uiState.isSelected) 12.dp else 8.dp
    val cardShapeOuter = RoundedCornerShape(18.dp)

    Box(
        modifier = modifier
            .zIndex(if (uiState.isSelected) 1f else 0f)
            .size(
                width = if (uiState.isSelected) uiState.width + 16.dp else uiState.width + 8.dp,
                height = if (uiState.isSelected) uiState.height + 38.dp else uiState.height + 16.dp,
            ),
    ) {
        if (uiState.isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(width = uiState.width + 12.dp, height = uiState.height + 12.dp)
                    .background(CardGlowFill, shape = cardShapeOuter)
                    .shadow(
                        elevation = 22.dp,
                        shape = cardShapeOuter,
                        ambientColor = CardGlow,
                        spotColor = CardGlow,
                        clip = false,
                    )
            )
//            阴影会干扰高光，先注释掉
//            Box(
//                modifier = Modifier
//                    .align(Alignment.Center)
//                    .offset(x = (-4).dp, y = 8.dp)
//                    .size(width = uiState.width + 10.dp, height = uiState.height + 30.dp)
//                    .background(CardShadowSelectedFill, shape = cardShapeOuter)
//                    .shadow(
//                        elevation = 24.dp,
//                        shape = cardShapeOuter,
//                        ambientColor = CardShadowSelected,
//                        spotColor = CardShadowSelected,
//                        clip = false,
//                    )
//            )
        } else {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(x = (-2).dp, y = 4.dp)
                    .size(width = uiState.width + 4.dp, height = uiState.height + 14.dp)
                    .background(CardShadowFill, shape = RoundedCornerShape(14.dp))
                    .shadow(
                        elevation = 12.dp,
                        shape = RoundedCornerShape(14.dp),
                        ambientColor = CardShadow,
                        spotColor = CardShadow,
                        clip = false,
                    )
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(width = uiState.width, height = uiState.height)
                .shadow(
                    elevation = cardShadowElevation,
                    shape = CardShape,
                    ambientColor = shadowColor,
                    spotColor = shadowColor,
                    clip = false,
                )
                .clip(CardShape)
                .background(if (uiState.isSelected) CardFaceHighlight else CardFace)
                .border(
                    width = if (uiState.isSelected) 1.5.dp else 1.dp,
                    color = if (uiState.isSelected) Color(0xFFF1E8C8) else CardStroke,
                    shape = CardShape,
                )
                .clickable(enabled = uiState.enabled, onClick = uiState.onToggle)
                .semantics {
                    contentDescription = uiState.card.displayName
                }
                .testTag("${ComposeTestTags.PLAYER_CARD_PREFIX}${uiState.card.id}"),
        ) {
            CardFaceContent(
                label = uiState.card.displayName,
                compact = false,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun CardFaceContent(
    label: String,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val suit = label.takeLast(1)
    val rank = label.dropLast(1)
    val suitColor = if (suit == "\u2665" || suit == "\u2666") CardRed else CardBlack

    Box(modifier = modifier.padding(if (compact) 4.dp else 6.dp)) {
        CornerPip(
            rank = rank,
            suit = suit,
            suitColor = suitColor,
            compact = compact,
            modifier = Modifier.align(Alignment.TopStart),
        )
        Text(
            text = suit,
            color = suitColor,
            style = if (compact) CompactCardCenterSuitTextStyle else CardCenterSuitTextStyle,
            modifier = Modifier.align(Alignment.Center),
        )
        CornerPip(
            rank = rank,
            suit = suit,
            suitColor = suitColor,
            compact = compact,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .rotate(180f),
        )
    }
}

@Composable
private fun CornerPip(
    rank: String,
    suit: String,
    suitColor: Color,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy((-1).dp),
    ) {
        Text(
            text = rank,
            color = CardBlack,
            style = if (compact) CompactCardRankTextStyle else CardRankTextStyle,
        )
        Text(
            text = suit,
            color = suitColor,
            style = if (compact) CompactCardSuitTextStyle else CardSuitTextStyle,
        )
    }
}
