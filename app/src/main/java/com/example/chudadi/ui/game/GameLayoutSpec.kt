package com.example.chudadi.ui.game

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class GameLayoutSpec(
    val outerPadding: Dp,
    val tableInnerHorizontalPadding: Dp,
    val tableInnerVerticalPadding: Dp,
    val areaSpacing: Dp,
    val topAreaHeight: Dp,
    val middleAreaMinHeight: Dp,
    val bottomAreaHeight: Dp,
    val bottomAreaMinHeight: Dp,
    val sideSeatWidth: Dp,
    val centerPlayMinWidth: Dp,
    val centerPlayBottomClearance: Dp,
    val opponentAvatarSize: Dp,
    val opponentInfoMinWidth: Dp,
    val opponentInfoMaxWidth: Dp,
    val opponentHorizontalCardWidth: Dp,
    val opponentHorizontalCardHeight: Dp,
    val opponentHorizontalCardStep: Dp,
    val opponentVerticalCardWidth: Dp,
    val opponentVerticalCardHeight: Dp,
    val opponentVerticalCardStep: Dp,
    val playerCardWidth: Dp,
    val playerCardHeight: Dp,
    val playerCardMinStep: Dp,
    val playerCardPreferredStep: Dp,
    val selectedCardLift: Dp,
    val selectedCardShift: Dp,
    val tableCardWidth: Dp,
    val tableCardHeight: Dp,
    val tableCardSpacing: Dp,
    val actionButtonsMaxWidth: Dp,
    val actionButtonMinHeight: Dp,
    val actionButtonSpacing: Dp,
)

@Composable
fun rememberGameLayoutSpec(
    maxWidth: Dp,
    maxHeight: Dp,
): GameLayoutSpec {
    return remember(DpSize(maxWidth, maxHeight)) {
        val isShortHeight = maxHeight < 420.dp

        when {
            maxWidth < 760.dp -> compactGameLayoutSpec(isShortHeight)
            maxWidth < 980.dp -> mediumGameLayoutSpec(isShortHeight)
            else -> expandedGameLayoutSpec(isShortHeight)
        }
    }
}

private fun compactGameLayoutSpec(isShortHeight: Boolean): GameLayoutSpec =
    GameLayoutSpec(
        outerPadding = 4.dp,
        tableInnerHorizontalPadding = if (isShortHeight) 10.dp else 12.dp,
        tableInnerVerticalPadding = if (isShortHeight) 6.dp else 8.dp,
        areaSpacing = 6.dp,
        topAreaHeight = if (isShortHeight) 60.dp else 72.dp,
        middleAreaMinHeight = if (isShortHeight) 180.dp else 164.dp,
        bottomAreaHeight = if (isShortHeight) 138.dp else 164.dp,
        bottomAreaMinHeight = if (isShortHeight) 128.dp else 146.dp,
        sideSeatWidth = 104.dp,
        centerPlayMinWidth = 188.dp,
        centerPlayBottomClearance = if (isShortHeight) 6.dp else 24.dp,
        opponentAvatarSize = 34.dp,
        opponentInfoMinWidth = 72.dp,
        opponentInfoMaxWidth = 86.dp,
        opponentHorizontalCardWidth = 13.dp,
        opponentHorizontalCardHeight = 22.dp,
        opponentHorizontalCardStep = 3.dp,
        opponentVerticalCardWidth = 22.dp,
        opponentVerticalCardHeight = 13.dp,
        opponentVerticalCardStep = 3.dp,
        playerCardWidth = 54.dp,
        playerCardHeight = 84.dp,
        playerCardMinStep = 20.dp,
        playerCardPreferredStep = 44.dp,
        selectedCardLift = 12.dp,
        selectedCardShift = 6.dp,
        tableCardWidth = 40.dp,
        tableCardHeight = 58.dp,
        tableCardSpacing = 4.dp,
        actionButtonsMaxWidth = 320.dp,
        actionButtonMinHeight = 40.dp,
        actionButtonSpacing = 6.dp,
    )

private fun mediumGameLayoutSpec(isShortHeight: Boolean): GameLayoutSpec =
    GameLayoutSpec(
        outerPadding = 5.dp,
        tableInnerHorizontalPadding = if (isShortHeight) 12.dp else 14.dp,
        tableInnerVerticalPadding = if (isShortHeight) 7.dp else 9.dp,
        areaSpacing = 8.dp,
        topAreaHeight = if (isShortHeight) 64.dp else 78.dp,
        middleAreaMinHeight = if (isShortHeight) 196.dp else 188.dp,
        bottomAreaHeight = if (isShortHeight) 146.dp else 170.dp,
        bottomAreaMinHeight = if (isShortHeight) 134.dp else 154.dp,
        sideSeatWidth = 116.dp,
        centerPlayMinWidth = 236.dp,
        centerPlayBottomClearance = if (isShortHeight) 8.dp else 28.dp,
        opponentAvatarSize = if (isShortHeight) 34.dp else 36.dp,
        opponentInfoMinWidth = 76.dp,
        opponentInfoMaxWidth = 90.dp,
        opponentHorizontalCardWidth = 13.dp,
        opponentHorizontalCardHeight = 22.dp,
        opponentHorizontalCardStep = 3.dp,
        opponentVerticalCardWidth = 22.dp,
        opponentVerticalCardHeight = 13.dp,
        opponentVerticalCardStep = 3.dp,
        playerCardWidth = 58.dp,
        playerCardHeight = 90.dp,
        playerCardMinStep = 22.dp,
        playerCardPreferredStep = 47.dp,
        selectedCardLift = 14.dp,
        selectedCardShift = 6.dp,
        tableCardWidth = 44.dp,
        tableCardHeight = 62.dp,
        tableCardSpacing = 6.dp,
        actionButtonsMaxWidth = 360.dp,
        actionButtonMinHeight = 42.dp,
        actionButtonSpacing = 8.dp,
    )

private fun expandedGameLayoutSpec(isShortHeight: Boolean): GameLayoutSpec =
    GameLayoutSpec(
        outerPadding = 6.dp,
        tableInnerHorizontalPadding = if (isShortHeight) 14.dp else 16.dp,
        tableInnerVerticalPadding = if (isShortHeight) 8.dp else 10.dp,
        areaSpacing = 10.dp,
        topAreaHeight = if (isShortHeight) 72.dp else 88.dp,
        middleAreaMinHeight = if (isShortHeight) 210.dp else 208.dp,
        bottomAreaHeight = if (isShortHeight) 156.dp else 188.dp,
        bottomAreaMinHeight = if (isShortHeight) 142.dp else 166.dp,
        sideSeatWidth = 124.dp,
        centerPlayMinWidth = 272.dp,
        centerPlayBottomClearance = if (isShortHeight) 10.dp else 32.dp,
        opponentAvatarSize = if (isShortHeight) 36.dp else 40.dp,
        opponentInfoMinWidth = 82.dp,
        opponentInfoMaxWidth = 96.dp,
        opponentHorizontalCardWidth = 14.dp,
        opponentHorizontalCardHeight = 24.dp,
        opponentHorizontalCardStep = 3.dp,
        opponentVerticalCardWidth = 24.dp,
        opponentVerticalCardHeight = 14.dp,
        opponentVerticalCardStep = 3.dp,
        playerCardWidth = 62.dp,
        playerCardHeight = 96.dp,
        playerCardMinStep = 24.dp,
        playerCardPreferredStep = 50.dp,
        selectedCardLift = 16.dp,
        selectedCardShift = 6.dp,
        tableCardWidth = 48.dp,
        tableCardHeight = 68.dp,
        tableCardSpacing = 6.dp,
        actionButtonsMaxWidth = 400.dp,
        actionButtonMinHeight = 44.dp,
        actionButtonSpacing = 10.dp,
    )
