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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chudadi.model.game.entity.RoundScore
import com.example.chudadi.model.game.snapshot.MatchUiState
import com.example.chudadi.ui.ComposeTestTags
import com.example.chudadi.ui.components.ChuButton
import com.example.chudadi.ui.components.ChuButtonStyle

private val BgOuter = Color(0xFF1A1008)
private val BgCard = Color(0xFF241912)
private val BgCardBorder = Color(0x44C8A96A)
private val GoldAccent = Color(0xFFD4A85A)
private val RowBg = Color(0xAA1D1A14)
private val RowBorder = Color(0x44C8A96A)
private val HeaderBg = Color(0x22C8A96A)
private val TextPrimary = Color(0xFFF7F1E4)
private val TextSecondary = Color(0xFFB8A882)
private val TextMuted = Color(0xFF7A6A50)
private val ScorePositive = Color(0xFF7EC87E)
private val ScoreNegative = Color(0xFFE07070)
private val DividerColor = Color(0x33C8A96A)
private val RankGold = Color(0xFFD4A85A)
private val RankSilver = Color(0xFF94A3B8)
private val RankBronze = Color(0xFFCD7F32)

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
                .shadow(12.dp, RoundedCornerShape(24.dp))
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
                    colors = listOf(Color(0xFF2A1A08), Color(0xFF1A1008)),
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
                            colors = listOf(Color(0x20D4A85A), Color.Transparent),
                        ),
                    ),
            )
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Color(0x33C8A96A))
                    .border(1.5.dp, Color(0x88C8A96A), CircleShape),
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

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(10.dp))
            .clip(RoundedCornerShape(10.dp))
            .background(RowBg)
            .border(1.dp, RowBorder, RoundedCornerShape(10.dp))
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
