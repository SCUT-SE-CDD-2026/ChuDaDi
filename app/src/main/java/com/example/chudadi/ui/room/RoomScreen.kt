@file:Suppress("TooManyFunctions")

package com.example.chudadi.ui.room

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.chudadi.R
import com.example.chudadi.ui.components.ChuButton
import com.example.chudadi.ui.components.ChuButtonStyle

private val BgOuter = Color(0xFF1A1008)
private val BgCard = Color(0xFF241912)
private val BgCardBorder = Color(0x44C8A96A)
private val SectionBg = Color(0x22C8A96A)
private val SectionBorder = Color(0x33C8A96A)
private val SlotEmptyBg = Color(0x14C8A96A)
private val SlotEmptyBorder = Color(0x44C8A96A)
private val SlotFilledBg = Color(0xAA1D1A14)
private val SlotFilledBorder = Color(0x66C8A96A)
private val SlotHostBorder = Color(0xAABA8C43)
private val TextPrimary = Color(0xFFF7F1E4)
private val TextSecondary = Color(0xFFB8A882)
private val TextMuted = Color(0xFF7A6A50)
private val StatusReady = Color(0xFF4CAF50)
private val StatusNotReady = Color(0xFFFF9800)
private val StatusDisconnected = Color(0xFF9E9E9E)
private val ScorePositive = Color(0xFF81C784)
private val ScoreNegative = Color(0xFFE57373)
private val DividerColor = Color(0x33C8A96A)
private val GoldAccent = Color(0xFFD4A85A)

@Composable
fun RoomScreen(
    uiState: RoomUiState,
    onAction: (RoomAction) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BgOuter),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.92f)
                .shadow(24.dp, RoundedCornerShape(24.dp))
                .clip(RoundedCornerShape(24.dp))
                .background(BgCard)
                .border(1.dp, BgCardBorder, RoundedCornerShape(24.dp)),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                RoomTopBar(
                    uiState = uiState,
                    onBack = onNavigateBack,
                    onResetScores = { onAction(RoomAction.ResetScores) },
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(DividerColor),
                )
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    SlotsGrid(
                        uiState = uiState,
                        onAction = onAction,
                        modifier = Modifier
                            .weight(0.62f)
                            .fillMaxHeight(),
                    )
                    ControlPanel(
                        uiState = uiState,
                        onAction = onAction,
                        modifier = Modifier
                            .weight(0.38f)
                            .fillMaxHeight(),
                    )
                }
            }
        }

        if (uiState.showAiDifficultyDialog) {
            AiDifficultyDialog(
                onSelect = { diff -> onAction(RoomAction.AddAiToSlot(uiState.aiDialogTargetSlot, diff)) },
                onDismiss = { onAction(RoomAction.DismissAiDialog) },
            )
        }

        uiState.pendingSwapRequest?.let { req ->
            SwapRequestDialog(
                request = req,
                onConfirm = { onAction(RoomAction.ConfirmSwap(req)) },
                onDecline = { onAction(RoomAction.DeclineSwap) },
            )
        }
    }
}

@Composable
private fun RoomTopBar(
    uiState: RoomUiState,
    onBack: () -> Unit,
    onResetScores: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = TextSecondary)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = if (uiState.isHost) "我的房间（房主）" else "房间（成员）",
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
        )
        Spacer(modifier = Modifier.width(8.dp))
        if (uiState.isHost) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0x44BA8C43))
                    .border(1.dp, Color(0x88BA8C43), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Text("房主", style = MaterialTheme.typography.labelSmall, color = GoldAccent)
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        val filledCount = uiState.slots.count { it.occupantType != null }
        Text(
            text = "$filledCount / 4",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
        )
        Spacer(modifier = Modifier.width(16.dp))
        if (uiState.isHost) {
            TextButton(onClick = onResetScores) {
                Text("重置分数", style = MaterialTheme.typography.labelMedium, color = TextMuted)
            }
        }
    }
}

@Composable
private fun SlotsGrid(
    uiState: RoomUiState,
    onAction: (RoomAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SlotCard(
                slot = uiState.slots[0],
                isHost = uiState.isHost,
                showActionMenu = uiState.showSlotActionMenu && uiState.slotActionMenuTarget == 0,
                onAction = onAction,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            SlotCard(
                slot = uiState.slots[1],
                isHost = uiState.isHost,
                showActionMenu = uiState.showSlotActionMenu && uiState.slotActionMenuTarget == 1,
                onAction = onAction,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SlotCard(
                slot = uiState.slots[2],
                isHost = uiState.isHost,
                showActionMenu = uiState.showSlotActionMenu && uiState.slotActionMenuTarget == 2,
                onAction = onAction,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            SlotCard(
                slot = uiState.slots[3],
                isHost = uiState.isHost,
                showActionMenu = uiState.showSlotActionMenu && uiState.slotActionMenuTarget == 3,
                onAction = onAction,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun SlotCard(
    slot: SlotState,
    isHost: Boolean,
    showActionMenu: Boolean,
    onAction: (RoomAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isEmpty = slot.occupantType == null
    val isHostSlot = slot.occupantType == SlotOccupantType.HUMAN_HOST
    val borderColor = when {
        isHostSlot -> SlotHostBorder
        isEmpty -> SlotEmptyBorder
        else -> SlotFilledBorder
    }
    val bgColor = if (isEmpty) SlotEmptyBg else SlotFilledBg
    val handleClick = {
        if (isEmpty) {
            if (isHost) onAction(RoomAction.OpenAiDialog(slot.seatIndex))
            else onAction(RoomAction.RequestSwapWithSlot(slot.seatIndex))
        } else {
            onAction(RoomAction.OpenSlotActionMenu(slot.seatIndex))
        }
    }

    Box(
        modifier = modifier
            .shadow(if (isEmpty) 2.dp else 6.dp, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .border(
                width = if (isHostSlot) 1.5.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(14.dp),
            )
            .clickable { handleClick() },
        contentAlignment = Alignment.Center,
    ) {
        if (isEmpty) {
            EmptySlotContent(seatIndex = slot.seatIndex)
        } else {
            FilledSlotContent(slot = slot)
        }

        if (showActionMenu && !isEmpty) {
            Box(modifier = Modifier.align(Alignment.TopEnd)) {
                SlotActionMenu(slot = slot, isHost = isHost, onAction = onAction)
            }
        }
    }
}

@Composable
private fun SlotActionMenu(slot: SlotState, isHost: Boolean, onAction: (RoomAction) -> Unit) {
    DropdownMenu(
        expanded = true,
        onDismissRequest = { onAction(RoomAction.DismissSlotActionMenu) },
        containerColor = Color(0xFF2A1F14),
    ) {
        if (!slot.isLocalPlayer) {
            DropdownMenuItem(
                text = { Text("请求换位", color = GoldAccent) },
                onClick = {
                    onAction(RoomAction.DismissSlotActionMenu)
                    onAction(RoomAction.RequestSwapWithSlot(slot.seatIndex))
                },
            )
        }
        if (isHost && !slot.isLocalPlayer) {
            DropdownMenuItem(
                text = { Text("移除", color = Color(0xFFE57373)) },
                onClick = { onAction(RoomAction.RemoveSlotOccupant(slot.seatIndex)) },
            )
        }
        DropdownMenuItem(
            text = { Text("取消", color = TextSecondary) },
            onClick = { onAction(RoomAction.DismissSlotActionMenu) },
        )
    }
}

@Composable
private fun EmptySlotContent(seatIndex: Int) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Color(0x22C8A96A))
                .border(1.dp, SlotEmptyBorder, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Add, contentDescription = null, tint = TextMuted, modifier = Modifier.size(24.dp))
        }
        Text(
            text = "位置 ${seatIndex + 1}",
            style = MaterialTheme.typography.labelMedium,
            color = TextMuted,
        )
    }
}

@Suppress("CyclomaticComplexMethod")
@Composable
private fun FilledSlotContent(slot: SlotState) {
    Column(
        modifier = Modifier.fillMaxSize().padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Color(0xFF3A2A1A))
                .border(
                    width = if (slot.isLocalPlayer) 2.dp else 1.5.dp,
                    color = if (slot.isLocalPlayer) Color(0xAABA8C43) else Color(0x66F7E8C2),
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            slot.avatarResId?.let {
                Image(
                    painter = painterResource(it),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }

        Text(
            text = slot.displayName,
            style = MaterialTheme.typography.bodyMedium,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val typeLabel = when (slot.occupantType) {
                SlotOccupantType.AI -> "AI"
                SlotOccupantType.HUMAN_HOST -> "房主"
                SlotOccupantType.HUMAN_MEMBER -> "成员"
                null -> ""
            }
            if (typeLabel.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0x33C8A96A))
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                ) {
                    Text(typeLabel, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                }
            }

            val (statusDot, statusText) = when (slot.connectionStatus) {
                MemberConnectionStatus.READY -> StatusReady to "已准备"
                MemberConnectionStatus.NOT_READY -> StatusNotReady to "未准备"
                MemberConnectionStatus.DISCONNECTED -> StatusDisconnected to "掉线"
                MemberConnectionStatus.CONNECTED -> StatusReady to "已连接"
                null -> Color.Transparent to ""
            }
            if (statusText.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(statusDot),
                )
                Text(statusText, style = MaterialTheme.typography.labelSmall, color = statusDot)
            }
        }

        val scoreColor = if (slot.cumulativeScore >= 0) ScorePositive else ScoreNegative
        Text(
            text = if (slot.cumulativeScore >= 0) "+${slot.cumulativeScore}" else "${slot.cumulativeScore}",
            style = MaterialTheme.typography.labelMedium,
            color = scoreColor,
        )
    }
}

@Composable
private fun ControlPanel(
    uiState: RoomUiState,
    onAction: (RoomAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Use Column with explicit Spacer instead of Arrangement.spacedBy to avoid
    // weight(1f) interaction issues that cause button height distortion
    Column(
        modifier = modifier
            .shadow(4.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(SectionBg)
            .border(1.dp, SectionBorder, RoundedCornerShape(16.dp))
            .padding(20.dp),
    ) {
        Text("房间信息", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        Spacer(modifier = Modifier.height(6.dp))
        InfoRow(label = "房主设备", value = uiState.hostDeviceName.ifEmpty { "本机" })
        Spacer(modifier = Modifier.height(2.dp))
        InfoRow(label = "当前规则", value = uiState.currentRule.label)
        Spacer(modifier = Modifier.height(2.dp))
        InfoRow(
            label = "蓝牙状态",
            value = if (uiState.bluetoothVisible) "可被发现" else "未广播",
            valueColor = if (uiState.bluetoothVisible) StatusReady else TextMuted,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(DividerColor))
        Spacer(modifier = Modifier.height(6.dp))

        if (uiState.isHost) {
            Text("房主操作", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
            Spacer(modifier = Modifier.height(6.dp))
            ChuButton(
                text = "切换: ${uiState.currentRule.label}",
                onClick = { onAction(RoomAction.ToggleRule) },
                style = ChuButtonStyle.SECONDARY,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.weight(1f))

            val filledCount = uiState.slots.count { it.occupantType != null }
            val notReadyCount = uiState.slots.count {
                it.occupantType != null && it.connectionStatus != MemberConnectionStatus.READY
            }
            val startButtonText = when {
                filledCount < 4 -> "还差 ${4 - filledCount} 人"
                notReadyCount > 0 -> "等待 $notReadyCount 人准备"
                else -> "开始游戏"
            }
            ChuButton(
                text = startButtonText,
                onClick = { onAction(RoomAction.StartGame) },
                style = ChuButtonStyle.PRIMARY,
                enabled = uiState.canStartGame,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            val localSlot = uiState.slots.firstOrNull { it.isLocalPlayer }
            val isReady = localSlot?.connectionStatus == MemberConnectionStatus.READY

            Text("成员操作", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "已连接: ${uiState.hostDeviceName.ifEmpty { "房主" }}",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )

            Spacer(modifier = Modifier.weight(1f))

            ChuButton(
                text = if (isReady) "取消准备" else "准备",
                onClick = { onAction(RoomAction.ToggleReady) },
                style = if (isReady) ChuButtonStyle.SECONDARY else ChuButtonStyle.PRIMARY,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (uiState.connectionHint.isNotEmpty()) {
            Text(
                text = uiState.connectionHint,
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
    valueColor: Color = TextSecondary,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = TextMuted)
        Text(value, style = MaterialTheme.typography.bodySmall, color = valueColor)
    }
}

@Composable
private fun AiDifficultyDialog(
    onSelect: (AiDifficulty) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择 AI 类型", style = MaterialTheme.typography.titleMedium, color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AiDifficulty.entries.forEach { diff ->
                    ChuButton(
                        text = diff.label,
                        onClick = { onSelect(diff) },
                        style = ChuButtonStyle.SECONDARY,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = TextSecondary)
            }
        },
        containerColor = Color(0xFF2A1F14),
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        shape = RoundedCornerShape(16.dp),
    )
}

@Composable
private fun SwapRequestDialog(
    request: SwapRequest,
    onConfirm: () -> Unit,
    onDecline: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDecline,
        title = { Text("换位请求", style = MaterialTheme.typography.titleMedium, color = TextPrimary) },
        text = {
            Text(
                "${request.requesterName} 请求与你换位，是否同意？",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
        },
        confirmButton = {
            ChuButton(text = "同意", onClick = onConfirm, style = ChuButtonStyle.PRIMARY)
        },
        dismissButton = {
            ChuButton(text = "拒绝", onClick = onDecline, style = ChuButtonStyle.SECONDARY)
        },
        containerColor = Color(0xFF2A1F14),
        shape = RoundedCornerShape(16.dp),
    )
}
