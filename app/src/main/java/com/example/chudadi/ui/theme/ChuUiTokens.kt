package com.example.chudadi.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp

object ChuUiTokens {
    val Outer: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalChuUiPalette.current.outer

    val Card: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalChuUiPalette.current.card

    val CardBorder: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalChuUiPalette.current.cardBorder

    val CardShadow: Dp
        @Composable
        @ReadOnlyComposable
        get() = LocalChuUiPalette.current.cardShadow

    val Section: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalChuUiPalette.current.section

    val SectionBorder: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalChuUiPalette.current.sectionBorder

    val SectionShadow: Dp
        @Composable
        @ReadOnlyComposable
        get() = LocalChuUiPalette.current.sectionShadow

    val Row: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalChuUiPalette.current.row

    val RowBorder: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalChuUiPalette.current.rowBorder

    val Input: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalChuUiPalette.current.input

    val InputBorder: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalChuUiPalette.current.inputBorder

    val InputBorderFocused: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalChuUiPalette.current.inputBorderFocused

    val Divider: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalChuUiPalette.current.divider

    val TextPrimary: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalChuUiPalette.current.textPrimary

    val TextSecondary: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalChuUiPalette.current.textSecondary

    val TextMuted: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalChuUiPalette.current.textMuted

    val GoldAccent: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalChuUiPalette.current.goldAccent

    val GoldHighlight: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalChuUiPalette.current.goldHighlight

    val AvatarBg: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalChuUiPalette.current.avatarBg

    val AvatarBorder: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalChuUiPalette.current.avatarBorder

    val Success: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalChuUiPalette.current.success

    val Warning: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalChuUiPalette.current.warning

    val Error: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalChuUiPalette.current.error

    val Disabled: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalChuUiPalette.current.disabled

    val LeftPanelStart: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalChuUiPalette.current.leftPanelStart

    val LeftPanelEnd: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalChuUiPalette.current.leftPanelEnd

    val IconTile: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalChuUiPalette.current.iconTile

    val SubtleGlow: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalChuUiPalette.current.subtleGlow

    val BaopeiRow: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalChuUiPalette.current.baopeiRow

    val BaopeiBorder: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalChuUiPalette.current.baopeiBorder

    val BaopeiTag: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalChuUiPalette.current.baopeiTag

    val RankSilver: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalChuUiPalette.current.rankSilver

    val RankBronze: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalChuUiPalette.current.rankBronze
}
