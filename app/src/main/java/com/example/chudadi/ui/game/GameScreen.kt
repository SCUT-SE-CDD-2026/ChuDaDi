@file:Suppress("TooManyFunctions")

package com.example.chudadi.ui.game

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
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
import android.os.Build
import android.graphics.BlurMaskFilter
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
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
private val CardGlow = Color(0x8CF7E2A4)
private val CardShadow = Color(0x3A180E09)
private val CardShadowSelected = Color(0x661F130E)
private val CardBack = Color(0xFF7D4B32)
private val CardBackHighlight = Color(0xFF9E6A4E)
private val SubmitButtonGold = Color(0xFFBA8C43)
private val SubmitButtonGoldDisabled = Color(0xFF6B5530)
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
private const val HandCardMoveDurationMs = 150
private const val ButtonStateDurationMs = 110
private const val ButtonPressDurationMs = 70
private const val ActorPulseDurationMs = 1400
private const val TableCardEnterDurationMs = 170
private const val TableCardEnterDelayMs = 45
private const val OpponentCardExitDurationMs = 150
private const val HumanTurnPromptDurationMs = 220

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

private fun playVerticalOffset(
    ownerSeatId: Int,
    layoutSpec: GameLayoutSpec,
): Dp {
    return when (ownerSeatId) {
        HUMAN_SEAT_ID -> layoutSpec.tableCardHeight / 4
        else -> 0.dp
    }
}

@Composable
private fun TablePlaySlot(
    tablePlay: TablePlaySummary,
    layoutSpec: GameLayoutSpec,
    modifier: Modifier = Modifier,
) {
    var animationKey by remember(tablePlay.ownerSeatId) { mutableIntStateOf(0) }
    var previousSignature by remember(tablePlay.ownerSeatId) { mutableStateOf<String?>(null) }
    val tablePlaySignature = remember(tablePlay.cardLabels) { tablePlay.cardLabels.joinToString(separator = "|") }

    LaunchedEffect(tablePlaySignature) {
        if (tablePlay.cardLabels.isNotEmpty() && previousSignature != tablePlaySignature) {
            animationKey += 1
            previousSignature = tablePlaySignature
        }
    }

    val tableCardOuterWidth = layoutSpec.tableCardWidth + 6.dp
    val tableCardOuterHeight = layoutSpec.tableCardHeight + 14.dp
    val tableCardStep = layoutSpec.tableCardWidth * 0.85f
    val contentWidth =
        tableCardOuterWidth + tableCardStep * (tablePlay.cardLabels.size - 1).coerceAtLeast(0)

    Column(
        modifier = modifier.padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Box(
            modifier = Modifier
                .offset(
                    x = playOffset(tablePlay.ownerSeatId),
                    y = playVerticalOffset(tablePlay.ownerSeatId, layoutSpec),
                )
                .width(contentWidth)
                .height(tableCardOuterHeight),
        ) {
            tablePlay.cardLabels.forEachIndexed { index, label ->
                TableCard(
                    label = label,
                    layoutSpec = layoutSpec,
                    entryKey = animationKey,
                    entryIndex = index,
                    modifier = Modifier.offset(x = tableCardStep * index),
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
    val humanTurnPrompt = rememberHumanTurnPrompt(uiState.isHumanTurn)

    Column(
        modifier = modifier
            .alpha(0.96f + 0.04f * humanTurnPrompt)
            .scale(1f + 0.01f * humanTurnPrompt),
        verticalArrangement = Arrangement.spacedBy(layoutSpec.areaSpacing),
    ) {
        GameActionButtons(
            uiState = uiState,
            actions = actions,
            layoutSpec = layoutSpec,
            humanTurnPrompt = humanTurnPrompt,
        )
        PlayerHandRow(
            uiState = uiState,
            layoutSpec = layoutSpec,
            onToggleCardSelection = actions.onToggleCardSelection,
            humanTurnPrompt = humanTurnPrompt,
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
    val pulse = rememberActorPulse(opponent.isCurrentActor)
    val ringColor =
        if (opponent.isCurrentActor) {
            CardGlow.copy(alpha = 0.56f + 0.22f * pulse)
        } else {
            Color(0xAAFFF7DD)
        }
    val ringWidth = if (opponent.isCurrentActor) 2.dp + 1.dp * pulse else 1.dp
    val ringScale = if (opponent.isCurrentActor) 1.05f + 0.03f * pulse else 1f

    Image(
        painter = painterResource(opponent.avatarResId),
        contentDescription = opponent.displayName,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .scale(ringScale)
            .size(layoutSpec.opponentAvatarSize)
            .clip(CircleShape)
            .background(Color(0xFFEEE2C0), CircleShape)
            .border(
                width = ringWidth,
                color = ringColor,
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
    val pulse = rememberActorPulse(opponent.isCurrentActor)
    val status =
        when {
            opponent.isCurrentActor -> stringResource(R.string.opponent_current_actor)
            opponent.hasPassed -> stringResource(R.string.opponent_passed)
            else -> stringResource(R.string.opponent_waiting)
        }
    val badgeBorderColor =
        if (opponent.isCurrentActor) {
            InfoBadgeStroke.copy(alpha = 0.45f + 0.20f * pulse)
        } else {
            InfoBadgeStroke
        }
    val nameColor =
        if (opponent.isCurrentActor) {
            Color(0xFFC6F6D5).copy(alpha = 0.88f + 0.12f * pulse)
        } else {
            Color(0xFFC6F6D5)
        }
    val statusColor =
        if (opponent.isCurrentActor) {
            Color(0xFFE7D7B1).copy(alpha = 0.84f + 0.16f * pulse)
        } else {
            Color(0xFFE7D7B1)
        }

    Column(
        modifier = Modifier
            .widthIn(min = layoutSpec.opponentInfoMinWidth, max = layoutSpec.opponentInfoMaxWidth)
            .clip(RoundedCornerShape(14.dp))
            .background(InfoBadge)
            .border(1.dp, badgeBorderColor, RoundedCornerShape(14.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start,
    ) {
        Text(
            text = opponent.displayName,
            color = nameColor,
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
            color = statusColor,
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

    var displayedCount by remember(orientation) { mutableIntStateOf(cardCount) }
    val exitingIndices = remember(orientation) { mutableStateListOf<Int>() }

    LaunchedEffect(cardCount) {
        if (cardCount < displayedCount) {
            val removedCount = displayedCount - cardCount
            exitingIndices.clear()
            exitingIndices.addAll((0 until removedCount).map { cardCount + it })
            kotlinx.coroutines.delay(OpponentCardExitDurationMs.toLong())
            displayedCount = cardCount
            exitingIndices.clear()
        } else {
            displayedCount = cardCount
            exitingIndices.clear()
        }
    }

    val width =
        if (orientation == StackOrientation.Horizontal) {
            layoutSpec.opponentHorizontalCardWidth +
                layoutSpec.opponentHorizontalCardStep * (displayedCount - 1).coerceAtLeast(0)
        } else {
            layoutSpec.opponentVerticalCardWidth
        }
    val height =
        if (orientation == StackOrientation.Horizontal) {
            layoutSpec.opponentHorizontalCardHeight
        } else {
            layoutSpec.opponentVerticalCardHeight +
                layoutSpec.opponentVerticalCardStep * (displayedCount - 1).coerceAtLeast(0)
        }

    Box(
        modifier = Modifier
            .width(width)
            .height(height),
    ) {
        repeat(displayedCount) { index ->
            val cardModifier =
                if (orientation == StackOrientation.Horizontal) {
                    Modifier.offset(x = layoutSpec.opponentHorizontalCardStep * index)
                } else {
                    Modifier.offset(y = layoutSpec.opponentVerticalCardStep * index)
                }

            OpponentBackCard(
                orientation = orientation,
                layoutSpec = layoutSpec,
                modifier = cardModifier,
            )
        }

        exitingIndices.forEach { index ->
            val progress = rememberOpponentCardExitProgress(key = "$orientation-$index-$cardCount")
            val cardModifier =
                if (orientation == StackOrientation.Horizontal) {
                    Modifier.offset(x = layoutSpec.opponentHorizontalCardStep * index)
                } else {
                    Modifier.offset(y = layoutSpec.opponentVerticalCardStep * index)
                }

            OpponentBackCard(
                orientation = orientation,
                layoutSpec = layoutSpec,
                modifier = cardModifier
                    .alpha(1f - progress)
                    .scale(1f - 0.14f * progress),
            )
        }
    }
}

@Composable
private fun OpponentBackCard(
    orientation: StackOrientation,
    layoutSpec: GameLayoutSpec,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
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

@Composable
private fun PlayerHandRow(
    uiState: MatchUiState,
    layoutSpec: GameLayoutSpec,
    onToggleCardSelection: (String) -> Unit,
    humanTurnPrompt: Float,
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
                .scale(1f + 0.01f * humanTurnPrompt)
                .width(layout.contentWidth.coerceAtMost(maxWidth))
                .height(handRowHeight),
        ) {
            cards.forEachIndexed { index, card ->
                val placement = layout.placements[index]
                val animatedX =
                    animateDpAsState(
                        targetValue = placement.x,
                        animationSpec = tween(durationMillis = HandCardMoveDurationMs),
                        label = "player-card-x",
                    )
                val animatedY =
                    animateDpAsState(
                        targetValue = placement.y,
                        animationSpec = tween(durationMillis = HandCardMoveDurationMs),
                        label = "player-card-y",
                    )
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
                    modifier = Modifier.offset(x = animatedX.value, y = animatedY.value),
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

private data class AnimatedActionButtonColors(
    val containerColor: Color,
    val contentColor: Color,
    val borderColor: Color? = null,
)

private data class PlayerCardUiState(
    val card: GameCard,
    val isSelected: Boolean,
    val enabled: Boolean,
    val width: Dp,
    val height: Dp,
    val onToggle: () -> Unit,
)

private data class BlurredRoundRectSpec(
    val color: Color,
    val widthFactor: Float,
    val heightFactor: Float,
    val offsetX: Dp,
    val offsetY: Dp,
    val cornerRadius: Dp,
    val blurRadius: Dp,
)

private data class PlayerCardVisualSpec(
    val shadowColor: Color,
    val cardShadowElevation: Dp,
    val outerWidth: Dp,
    val outerHeight: Dp,
    val selectedGlowSpec: BlurredRoundRectSpec,
    val selectedShadowSpec: BlurredRoundRectSpec,
    val baseShadowSpec: BlurredRoundRectSpec,
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
    humanTurnPrompt: Float,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Row(
            modifier = Modifier
                .widthIn(max = layoutSpec.actionButtonsMaxWidth)
                .scale(1f + 0.012f * humanTurnPrompt),
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
    if (uiState.primary) {
        PrimaryActionButton(uiState = uiState)
    } else {
        SecondaryActionButton(uiState = uiState)
    }
}

@Composable
private fun RowScope.PrimaryActionButton(uiState: ActionButtonUiState) {
    val interactionSource = rememberButtonInteractionSource()
    val buttonModifier = animatedActionButtonModifier(uiState = uiState, interactionSource = interactionSource)
    val colors = animatedPrimaryButtonColors(enabled = uiState.enabled)

    Button(
        modifier = buttonModifier,
        onClick = uiState.onClick,
        enabled = uiState.enabled,
        interactionSource = interactionSource,
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.containerColor,
            contentColor = colors.contentColor,
            disabledContainerColor = colors.containerColor,
            disabledContentColor = colors.contentColor,
        ),
    ) {
        Text(
            text = uiState.text,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun RowScope.SecondaryActionButton(uiState: ActionButtonUiState) {
    val interactionSource = rememberButtonInteractionSource()
    val buttonModifier = animatedActionButtonModifier(uiState = uiState, interactionSource = interactionSource)
    val colors = animatedSecondaryButtonColors(enabled = uiState.enabled)

    OutlinedButton(
        modifier = buttonModifier,
        onClick = uiState.onClick,
        enabled = uiState.enabled,
        interactionSource = interactionSource,
        border = BorderStroke(1.dp, colors.borderColor ?: Color.Transparent),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = colors.containerColor,
            contentColor = colors.contentColor,
            disabledContainerColor = colors.containerColor,
            disabledContentColor = colors.contentColor,
        ),
    ) {
        Text(
            text = uiState.text,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun rememberButtonInteractionSource(): MutableInteractionSource = remember { MutableInteractionSource() }

@Composable
private fun RowScope.animatedActionButtonModifier(
    uiState: ActionButtonUiState,
    interactionSource: MutableInteractionSource,
): Modifier {
    val isPressed = interactionSource.collectIsPressedAsState()
    val scale =
        animateFloatAsState(
            targetValue = if (isPressed.value && uiState.enabled) 0.98f else 1f,
            animationSpec = tween(durationMillis = ButtonPressDurationMs),
            label = "action-button-scale",
        )
    return Modifier
        .weight(1f, fill = true)
        .sizeIn(minHeight = uiState.minHeight)
        .testTag(uiState.tag)
        .scale(scale.value)
}

@Composable
private fun animatedPrimaryButtonColors(enabled: Boolean): AnimatedActionButtonColors {
    val containerColor =
        animateColorAsState(
            targetValue = if (enabled) SubmitButtonGold else SubmitButtonGoldDisabled,
            animationSpec = tween(durationMillis = ButtonStateDurationMs),
            label = "primary-button-container",
        )
    val contentColor =
        animateColorAsState(
            targetValue = if (enabled) Color(0xFFFDF7EA) else Color(0xFFD6D0C6),
            animationSpec = tween(durationMillis = ButtonStateDurationMs),
            label = "primary-button-content",
        )

    return AnimatedActionButtonColors(
        containerColor = containerColor.value,
        contentColor = contentColor.value,
    )
}

@Composable
private fun animatedSecondaryButtonColors(enabled: Boolean): AnimatedActionButtonColors {
    val containerColor =
        animateColorAsState(
            targetValue = if (enabled) Color(0xCC1D1A19) else Color(0x881D1A19),
            animationSpec = tween(durationMillis = ButtonStateDurationMs),
            label = "secondary-button-container",
        )
    val contentColor =
        animateColorAsState(
            targetValue = if (enabled) Color.White else Color(0xFFD6D0C6),
            animationSpec = tween(durationMillis = ButtonStateDurationMs),
            label = "secondary-button-content",
        )
    val borderColor =
        animateColorAsState(
            targetValue = if (enabled) Color(0x66F7E8C2) else Color(0x33F7E8C2),
            animationSpec = tween(durationMillis = ButtonStateDurationMs),
            label = "secondary-button-border",
        )

    return AnimatedActionButtonColors(
        containerColor = containerColor.value,
        contentColor = contentColor.value,
        borderColor = borderColor.value,
    )
}

@Composable
private fun TableCard(
    label: String,
    layoutSpec: GameLayoutSpec,
    entryKey: Int,
    entryIndex: Int,
    modifier: Modifier = Modifier,
) {
    val entryProgress = rememberTableCardEntryProgress(entryKey = entryKey, entryIndex = entryIndex)
    val baseShadowModifier =
        Modifier.fillMaxSize().blurredRoundRectModifier(
            spec =
                BlurredRoundRectSpec(
                    color = CardShadow.copy(alpha = 0.30f + 0.14f * entryProgress),
                    widthFactor = 0.78f,
                    heightFactor = 0.84f,
                    offsetX = (-2).dp,
                    offsetY = 5.dp,
                    cornerRadius = 18.dp,
                    blurRadius = 10.dp,
                ),
        )

    Box(
        modifier = modifier
            .alpha(entryProgress)
            .scale(1.08f - 0.08f * entryProgress)
            .size(
                width = layoutSpec.tableCardWidth + 6.dp,
                height = layoutSpec.tableCardHeight + 14.dp,
            ),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .then(baseShadowModifier),
        )
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(width = layoutSpec.tableCardWidth, height = layoutSpec.tableCardHeight)
                .shadow(
                    elevation = 6.dp,
                    shape = CardShape,
                    ambientColor = CardShadow,
                    spotColor = CardShadow,
                    clip = false,
                )
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
}

@Composable
private fun PlayerCardChip(
    uiState: PlayerCardUiState,
    modifier: Modifier = Modifier,
) {
    val visualSpec = animatedPlayerCardVisualSpec(uiState)
    val selectedGlowModifier =
        Modifier.fillMaxSize().blurredRoundRectModifier(
            spec = visualSpec.selectedGlowSpec,
        )
    val selectedShadowModifier =
        Modifier.fillMaxSize().blurredRoundRectModifier(
            spec = visualSpec.selectedShadowSpec,
        )
    val baseShadowModifier =
        Modifier.fillMaxSize().blurredRoundRectModifier(
            spec = visualSpec.baseShadowSpec,
        )

    Box(
        modifier = modifier
            .zIndex(if (uiState.isSelected) 1f else 0f)
            .size(width = visualSpec.outerWidth, height = visualSpec.outerHeight),
    ) {
        PlayerCardEffects(
            isSelected = uiState.isSelected,
            selectedGlowModifier = selectedGlowModifier,
            selectedShadowModifier = selectedShadowModifier,
            baseShadowModifier = baseShadowModifier,
        )
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(width = uiState.width, height = uiState.height)
                .shadow(
                    elevation = visualSpec.cardShadowElevation,
                    shape = CardShape,
                    ambientColor = visualSpec.shadowColor,
                    spotColor = visualSpec.shadowColor,
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
private fun animatedPlayerCardVisualSpec(uiState: PlayerCardUiState): PlayerCardVisualSpec {
    val cardSelectionProgress =
        animateFloatAsState(
            targetValue = if (uiState.isSelected) 1f else 0f,
            animationSpec = tween(durationMillis = HandCardMoveDurationMs),
            label = "card-selection-progress",
        )
    val shadowColor =
        animateColorAsState(
            targetValue = if (uiState.isSelected) CardShadowSelected else CardShadow,
            animationSpec = tween(durationMillis = HandCardMoveDurationMs),
            label = "card-shadow-color",
        )
    val cardShadowElevation =
        animateDpAsState(
            targetValue = if (uiState.isSelected) 10.dp else 6.dp,
            animationSpec = tween(durationMillis = HandCardMoveDurationMs),
            label = "card-shadow-elevation",
        )
    val outerWidth =
        animateDpAsState(
            targetValue = if (uiState.isSelected) uiState.width + 12.dp else uiState.width + 6.dp,
            animationSpec = tween(durationMillis = HandCardMoveDurationMs),
            label = "card-outer-width",
        )
    val outerHeight =
        animateDpAsState(
            targetValue = if (uiState.isSelected) uiState.height + 34.dp else uiState.height + 14.dp,
            animationSpec = tween(durationMillis = HandCardMoveDurationMs),
            label = "card-outer-height",
        )

    return PlayerCardVisualSpec(
        shadowColor = shadowColor.value,
        cardShadowElevation = cardShadowElevation.value,
        outerWidth = outerWidth.value,
        outerHeight = outerHeight.value,
        selectedGlowSpec =
            BlurredRoundRectSpec(
                color = CardGlow.copy(alpha = 0.60f * cardSelectionProgress.value),
                widthFactor = 0.87f,
                heightFactor = 0.87f,
                offsetX = 0.dp,
                offsetY = 0.dp,
                cornerRadius = 24.dp,
                blurRadius = 18.dp,
            ),
        selectedShadowSpec =
            BlurredRoundRectSpec(
                color = CardShadowSelected.copy(alpha = 0.28f * cardSelectionProgress.value),
                widthFactor = 0.80f,
                heightFactor = 0.88f,
                offsetX = (-3).dp,
                offsetY = 8.dp,
                cornerRadius = 24.dp,
                blurRadius = 12.dp,
            ),
        baseShadowSpec =
            BlurredRoundRectSpec(
                color =
                    CardShadow.copy(
                        alpha = 0.48f - 0.30f * cardSelectionProgress.value,
                    ),
                widthFactor = 0.78f,
                heightFactor = 0.84f,
                offsetX = (-2).dp,
                offsetY = 5.dp,
                cornerRadius = 18.dp,
                blurRadius = 10.dp,
            ),
    )
}

@Composable
private fun BoxScope.PlayerCardEffects(
    isSelected: Boolean,
    selectedGlowModifier: Modifier,
    selectedShadowModifier: Modifier,
    baseShadowModifier: Modifier,
) {
    Box(
        modifier = Modifier
            .align(Alignment.Center)
            .then(baseShadowModifier),
    )
    if (isSelected) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .then(selectedGlowModifier),
        )
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .then(selectedShadowModifier),
        )
    }
}

@Composable
private fun rememberActorPulse(isCurrentActor: Boolean): Float {
    if (!isCurrentActor) {
        return 0f
    }

    val transition = rememberInfiniteTransition(label = "actor-pulse")
    val pulse =
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec =
                infiniteRepeatable(
                    animation =
                        keyframes {
                            durationMillis = ActorPulseDurationMs
                            0f at 0 using LinearEasing
                            1f at ActorPulseDurationMs / 2 using LinearEasing
                            0f at ActorPulseDurationMs using LinearEasing
                        },
                    repeatMode = RepeatMode.Restart,
                ),
            label = "actor-pulse-value",
        )
    return pulse.value
}

@Composable
private fun rememberTableCardEntryProgress(
    entryKey: Int,
    entryIndex: Int,
): Float {
    val progress = remember(entryKey, entryIndex) { Animatable(0f) }

    LaunchedEffect(entryKey, entryIndex) {
        progress.snapTo(0f)
        kotlinx.coroutines.delay((entryIndex * TableCardEnterDelayMs).toLong())
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = TableCardEnterDurationMs),
        )
    }

    return progress.value
}

@Composable
private fun rememberOpponentCardExitProgress(key: String): Float {
    val progress = remember(key) { Animatable(0f) }

    LaunchedEffect(key) {
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = OpponentCardExitDurationMs),
        )
    }

    return progress.value
}

@Composable
private fun rememberHumanTurnPrompt(isHumanTurn: Boolean): Float {
    val progress = remember { Animatable(0f) }

    LaunchedEffect(isHumanTurn) {
        if (!isHumanTurn) {
            progress.snapTo(0f)
            return@LaunchedEffect
        }

        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = HumanTurnPromptDurationMs),
        )
        progress.animateTo(
            targetValue = 0f,
            animationSpec = tween(durationMillis = HumanTurnPromptDurationMs),
        )
    }

    return progress.value
}

private fun Modifier.blurredRoundRectModifier(spec: BlurredRoundRectSpec): Modifier =
    fillMaxSize().drawWithCache {
        val width = size.width * spec.widthFactor
        val height = size.height * spec.heightFactor
        val left = (size.width - width) / 2f + spec.offsetX.toPx()
        val top = (size.height - height) / 2f + spec.offsetY.toPx()
        val radius = spec.cornerRadius.toPx()
        val blurRadius = spec.blurRadius.toPx()
        onDrawBehind {
            drawBlurredRoundRect(
                spec =
                    DrawBlurredRoundRectSpec(
                        color = spec.color,
                        left = left,
                        top = top,
                        width = width,
                        height = height,
                        cornerRadius = radius,
                        blurRadius = blurRadius,
                    ),
            )
        }
    }

private data class DrawBlurredRoundRectSpec(
    val color: Color,
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
    val cornerRadius: Float,
    val blurRadius: Float,
)

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBlurredRoundRect(
    spec: DrawBlurredRoundRectSpec,
) {
    drawIntoCanvas { canvas ->
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
            this.color = spec.color.toArgb()
            maskFilter = BlurMaskFilter(spec.blurRadius, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.nativeCanvas.drawRoundRect(
            spec.left,
            spec.top,
            spec.left + spec.width,
            spec.top + spec.height,
            spec.cornerRadius,
            spec.cornerRadius,
            paint,
        )
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
