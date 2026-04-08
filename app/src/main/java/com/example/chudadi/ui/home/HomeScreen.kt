@file:Suppress("LongParameterList")

package com.example.chudadi.ui.home

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chudadi.R
import com.example.chudadi.ui.ComposeTestTags
import com.example.chudadi.ui.components.ChuButton
import com.example.chudadi.ui.components.ChuButtonStyle

private val BgOuter = Color(0xFF1A1008)
private val BgCard = Color(0xFF241912)
private val BgCardBorder = Color(0x44C8A96A)
private val LeftPanelBg = Color(0x22C8A96A)
private val PlayerInfoBg = Color(0xAA1A1008)
private val PlayerInfoBorder = Color(0x55F7E8C2)
private val TextPrimary = Color(0xFFF7F1E4)
private val TextSecondary = Color(0xFFB8A882)
private val DividerColor = Color(0x33C8A96A)

@Composable
fun HomeScreen(
    onStartLocalMatch: () -> Unit,
    onCreateRoom: () -> Unit = {},
    onJoinRoom: () -> Unit = {},
    onOnlineGame: () -> Unit = {},
    onSettings: () -> Unit = {},
    onRules: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BgOuter)
            .testTag(ComposeTestTags.HOME_SCREEN),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.88f)
                .shadow(elevation = 24.dp, shape = RoundedCornerShape(24.dp))
                .clip(RoundedCornerShape(24.dp))
                .background(BgCard)
                .border(width = 1.dp, color = BgCardBorder, shape = RoundedCornerShape(24.dp)),
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                LeftPanel(
                    modifier = Modifier
                        .weight(0.42f)
                        .fillMaxHeight(),
                )
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight(0.85f)
                        .align(Alignment.CenterVertically)
                        .background(DividerColor),
                )
                RightPanel(
                    onCreateRoom = onCreateRoom,
                    onJoinRoom = onJoinRoom,
                    onOnlineGame = onOnlineGame,
                    onSettings = onSettings,
                    onRules = onRules,
                    onStartLocalMatch = onStartLocalMatch,
                    modifier = Modifier
                        .weight(0.58f)
                        .fillMaxHeight(),
                )
            }
        }
    }
}

@Composable
private fun LeftPanel(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(
                Brush.horizontalGradient(
                    colors = listOf(LeftPanelBg, Color.Transparent),
                ),
            )
            .padding(horizontal = 32.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.weight(1f),
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            Image(
                painter = painterResource(R.drawable.main_logo),
                contentDescription = "锄大地",
                modifier = Modifier
                    .widthIn(max = 260.dp)
                    .fillMaxWidth(0.85f),
                contentScale = ContentScale.Fit,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "锄大地",
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary,
                fontSize = 28.sp,
            )
            Text(
                text = "The Big Two",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                letterSpacing = 2.sp,
            )
        }
        PlayerInfoBlock()
    }
}

@Composable
private fun PlayerInfoBlock() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(3.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(PlayerInfoBg)
            .border(1.dp, PlayerInfoBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color(0xFF3A2A1A))
                .border(1.5.dp, Color(0x88F7E8C2), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.avatar),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "玩家",
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary,
            )
            Text(
                text = "南方规则 · v1.0",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
            )
        }
    }
}

@Composable
private fun RightPanel(
    onCreateRoom: () -> Unit,
    onJoinRoom: () -> Unit,
    onOnlineGame: () -> Unit,
    onSettings: () -> Unit,
    onRules: () -> Unit,
    onStartLocalMatch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "选择模式",
            style = MaterialTheme.typography.titleMedium,
            color = TextSecondary,
            letterSpacing = 1.sp,
        )
        Spacer(modifier = Modifier.height(16.dp))

        ChuButton(
            text = "创建房间",
            onClick = onCreateRoom,
            style = ChuButtonStyle.PRIMARY,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(10.dp))
        ChuButton(
            text = "加入房间",
            onClick = onJoinRoom,
            style = ChuButtonStyle.SECONDARY,
            enabled = false,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(10.dp))
        ChuButton(
            text = "本地对局（测试）",
            onClick = onStartLocalMatch,
            style = ChuButtonStyle.SECONDARY,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(ComposeTestTags.START_MATCH_BUTTON),
        )
        Spacer(modifier = Modifier.height(10.dp))
        ChuButton(
            text = "联网游戏（开发中）",
            onClick = onOnlineGame,
            style = ChuButtonStyle.GHOST,
            enabled = false,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ChuButton(
                text = "设置",
                onClick = onSettings,
                style = ChuButtonStyle.GHOST,
                modifier = Modifier.weight(1f),
            )
            ChuButton(
                text = "规则说明",
                onClick = onRules,
                style = ChuButtonStyle.GHOST,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
