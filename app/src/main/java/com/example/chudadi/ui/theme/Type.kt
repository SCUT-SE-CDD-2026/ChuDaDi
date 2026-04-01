package com.example.chudadi.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.chudadi.R

private val ChuDaDiSans = FontFamily(
    Font(R.font.inter_18pt_medium, FontWeight.Medium),
    Font(R.font.inter_18pt_extrabold, FontWeight.ExtraBold),
    Font(R.font.inter_24pt_medium, FontWeight.Normal),
    Font(R.font.inter_24pt_extrabold, FontWeight.Bold),
    Font(R.font.source_han_sans_cn_medium, FontWeight.Medium),
    Font(R.font.source_han_sans_cn_heavy, FontWeight.ExtraBold),
)

private val DisplayTextStyle = TextStyle(
    fontFamily = ChuDaDiSans,
    fontWeight = FontWeight.ExtraBold,
    letterSpacing = 0.sp,
)

private val TitleTextStyle = TextStyle(
    fontFamily = ChuDaDiSans,
    fontWeight = FontWeight.Bold,
    letterSpacing = 0.sp,
)

private val BodyTextStyle = TextStyle(
    fontFamily = ChuDaDiSans,
    fontWeight = FontWeight.Medium,
    letterSpacing = 0.1.sp,
)

private val LabelTextStyle = TextStyle(
    fontFamily = ChuDaDiSans,
    fontWeight = FontWeight.Medium,
    letterSpacing = 0.2.sp,
)

val Typography = Typography(
    headlineMedium = DisplayTextStyle.copy(
        fontSize = 30.sp,
        lineHeight = 38.sp,
    ),
    headlineSmall = DisplayTextStyle.copy(
        fontSize = 24.sp,
        lineHeight = 32.sp,
    ),
    titleLarge = TitleTextStyle.copy(
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleMedium = TitleTextStyle.copy(
        fontSize = 18.sp,
        lineHeight = 24.sp,
    ),
    bodyLarge = BodyTextStyle.copy(
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = BodyTextStyle.copy(
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = BodyTextStyle.copy(
        fontSize = 12.sp,
        lineHeight = 18.sp,
    ),
    labelLarge = LabelTextStyle.copy(
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelMedium = LabelTextStyle.copy(
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelSmall = LabelTextStyle.copy(
        fontSize = 11.sp,
        lineHeight = 14.sp,
    ),
)

val CardRankTextStyle = TextStyle(
    fontFamily = ChuDaDiSans,
    fontWeight = FontWeight.ExtraBold,
    fontSize = 22.sp,
    lineHeight = 24.sp,
    letterSpacing = 0.sp,
)

val CardSuitTextStyle = TextStyle(
    fontFamily = ChuDaDiSans,
    fontWeight = FontWeight.Bold,
    fontSize = 20.sp,
    lineHeight = 22.sp,
    letterSpacing = 0.sp,
)

val CompactCardRankTextStyle = TextStyle(
    fontFamily = ChuDaDiSans,
    fontWeight = FontWeight.ExtraBold,
    fontSize = 14.sp,
    lineHeight = 16.sp,
    letterSpacing = 0.sp,
)

val CompactCardSuitTextStyle = TextStyle(
    fontFamily = ChuDaDiSans,
    fontWeight = FontWeight.Bold,
    fontSize = 14.sp,
    lineHeight = 16.sp,
    letterSpacing = 0.sp,
)
