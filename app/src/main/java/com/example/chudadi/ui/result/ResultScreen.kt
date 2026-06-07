package com.example.chudadi.ui.result

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chudadi.model.game.entity.RoundScore
import com.example.chudadi.model.game.snapshot.MatchUiState
import com.example.chudadi.ui.ComposeTestTags
import com.example.chudadi.ui.components.ChuButton
import com.example.chudadi.ui.components.ChuButtonStyle
import com.example.chudadi.ui.theme.LocalChuUiPalette

private val BgOuter: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalChuUiPalette.current.outer
private val BgCard: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalChuUiPalette.current.card
private val BgCardBorder: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalChuUiPalette.current.cardBorder
private val BgCardShadow: Dp
    @Composable
    @ReadOnlyComposable
    get() = LocalChuUiPalette.current.cardShadow
private val GoldAccent: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalChuUiPalette.current.goldAccent
private val RowBg: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalChuUiPalette.current.row
private val RowBorder: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalChuUiPalette.current.rowBorder
private val HeaderBg: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalChuUiPalette.current.section
private val ResultSectionShadow: Dp
    @Composable
    @ReadOnlyComposable
    get() = LocalChuUiPalette.current.sectionShadow
private val TextPrimary: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalChuUiPalette.current.textPrimary
private val TextSecondary: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalChuUiPalette.current.textSecondary
private val TextMuted: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalChuUiPalette.current.textMuted
private val ScorePositive: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalChuUiPalette.current.success
private val ScoreNegative: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalChuUiPalette.current.error
private val BaopeiRowBg: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalChuUiPalette.current.baopeiRow
private val BaopeiRowBorder: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalChuUiPalette.current.baopeiBorder
private val BaopeiTagBg: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalChuUiPalette.current.baopeiTag
private val DividerColor: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalChuUiPalette.current.divider
private val RankGold: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalChuUiPalette.current.goldAccent
private val RankSilver: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalChuUiPalette.current.rankSilver
private val RankBronze: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalChuUiPalette.current.rankBronze
private val LeftPanelStart: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalChuUiPalette.current.leftPanelStart
private val LeftPanelEnd: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalChuUiPalette.current.leftPanelEnd
private val WinnerGlow: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalChuUiPalette.current.subtleGlow
private val WinnerTileBg: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalChuUiPalette.current.iconTile
private val WinnerTileBorder: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalChuUiPalette.current.avatarBorder

@Composable
fun ResultScreen(
    uiState: MatchUiState,
    onReturnToRoom: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BgOuter)
            .testTag(ComposeTestTags.RESULT_SCREEN),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.90f)
                .fillMaxHeight(0.90f)
                .shadow(BgCardShadow, RoundedCornerShape(24.dp))
                .clip(RoundedCornerShape(24.dp))
                .background(BgCard)
                .border(1.dp, BgCardBorder, RoundedCornerShape(24.dp)),
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                ResultLeftPanel(
                    winnerName = uiState.resultSummary?.winnerName ?: "-",
                    modifier = Modifier
                        .weight(0.36f)
                        .fillMaxHeight(),
                )
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight(0.85f)
                        .align(Alignment.CenterVertically)
                        .background(DividerColor),
                )
                ResultRightPanel(
                    uiState = uiState,
                    onReturnToRoom = onReturnToRoom,
                    modifier = Modifier
                        .weight(0.64f)
                        .fillMaxHeight(),
                )
            }
        }
    }
}

@Composable
private fun ResultLeftPanel(
    winnerName: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(
                Brush.verticalGradient(
                    colors = listOf(LeftPanelStart, LeftPanelEnd),
                ),
            )
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(WinnerGlow, Color.Transparent),
                        ),
                    ),
            )
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(WinnerTileBg)
                    .border(1.5.dp, WinnerTileBorder, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text("🏆", fontSize = 32.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "本局结束",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            letterSpacing = 2.sp,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "胜者",
            style = MaterialTheme.typography.labelMedium,
            color = TextMuted,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = winnerName,
            style = MaterialTheme.typography.headlineSmall,
            color = GoldAccent,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(20.dp))

        Box(
            modifier = Modifier
                .width(48.dp)
                .height(1.dp)
                .background(DividerColor),
        )
    }
}

@Composable
private fun ResultRightPanel(
    uiState: MatchUiState,
    onReturnToRoom: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "本局结算",
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
        )

        val scores = uiState.resultSummary?.roundScores.orEmpty()
        if (scores.isNotEmpty()) {
            ScoreTableHeader()
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                scores.sortedByDescending { it.roundScore }.forEachIndexed { index, score ->
                    ScoreRow(rank = index + 1, score = score)
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(HeaderBg)
                    .border(1.dp, RowBorder, RoundedCornerShape(10.dp))
                    .padding(20.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("暂无结算数据", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        ChuButton(
            text = "返回房间",
            onClick = onReturnToRoom,
            style = ChuButtonStyle.PRIMARY,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(ComposeTestTags.RETURN_TO_ROOM_BUTTON),
        )
    }
}

@Composable
private fun ScoreTableHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(HeaderBg)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("#", style = MaterialTheme.typography.labelMedium, color = TextMuted, modifier = Modifier.width(28.dp))
        Text("玩家", style = MaterialTheme.typography.labelMedium, color = TextMuted, modifier = Modifier.weight(1f))
        Text("剩余牌",
            style = MaterialTheme.typography.labelMedium,
            color = TextMuted,
            modifier = Modifier.width(52.dp),
            textAlign = TextAlign.Center,
        )
        Text("本局得分",
            style = MaterialTheme.typography.labelMedium,
            color = TextMuted,
            modifier = Modifier.width(64.dp),
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun ScoreRow(rank: Int, score: RoundScore) {
    val rankColor = when (rank) {
        1 -> RankGold
        2 -> RankSilver
        3 -> RankBronze
        else -> TextMuted
    }
    val rankLabel = when (rank) {
        1 -> "1"
        2 -> "2"
        3 -> "3"
        else -> "$rank"
    }
    val scoreColor = if (score.roundScore >= 0) ScorePositive else ScoreNegative
    val scoreText = if (score.roundScore >= 0) "+${score.roundScore}" else "${score.roundScore}"

    val rowBg = if (score.isBaopei) BaopeiRowBg else RowBg
    val rowBorder = if (score.isBaopei) BaopeiRowBorder else RowBorder

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(ResultSectionShadow, RoundedCornerShape(10.dp))
            .clip(RoundedCornerShape(10.dp))
            .background(rowBg)
            .border(1.dp, rowBorder, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = rankLabel,
            style = MaterialTheme.typography.labelMedium,
            color = rankColor,
            modifier = Modifier.width(28.dp),
        )
        Text(
            text = score.playerName,
            style = MaterialTheme.typography.bodyMedium,
            color = TextPrimary,
            modifier = Modifier.weight(1f),
        )
        if (score.isBaopei) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(BaopeiTagBg)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    text = "包赔",
                    style = MaterialTheme.typography.labelSmall,
                    color = ScoreNegative,
                    fontSize = 10.sp,
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
        }
        Text(
            text = "${score.remainingCards} 张",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            modifier = Modifier.width(52.dp),
            textAlign = TextAlign.Center,
        )
        Text(
            text = scoreText,
            style = MaterialTheme.typography.bodyMedium,
            color = scoreColor,
            modifier = Modifier.width(64.dp),
            textAlign = TextAlign.End,
        )
    }
}
