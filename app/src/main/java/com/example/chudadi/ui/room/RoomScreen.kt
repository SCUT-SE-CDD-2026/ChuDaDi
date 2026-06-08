@file:Suppress("TooManyFunctions", "FunctionNaming")

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.chudadi.BuildConfig
import com.example.chudadi.ui.ComposeTestTags
import com.example.chudadi.ui.components.ChuButton
import com.example.chudadi.ui.components.ChuButtonStyle
import com.example.chudadi.ui.theme.ChuUiTokens
































private val HostActionButtonHeight = 44.dp
private val MemberActionButtonHeight = 44.dp
private val BroadcastButtonHeight = 28.dp
private val BroadcastButtonWidth = 100.dp

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
            .background(ChuUiTokens.Outer)
            .testTag(ComposeTestTags.ROOM_SCREEN),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.92f)
                .shadow(ChuUiTokens.CardShadow, RoundedCornerShape(24.dp))
                .clip(RoundedCornerShape(24.dp))
                .background(ChuUiTokens.Card)
                .border(1.dp, ChuUiTokens.CardBorder, RoundedCornerShape(24.dp)),
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
                        .background(ChuUiTokens.Divider),
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

        if (uiState.showRoomAiDifficultyDialog) {
            RoomAiDifficultyDialog(
                step = uiState.aiSelectionStep,
                selectedAiType = uiState.selectedAiType,
                onSelectType = { onAction(RoomAction.SelectAiType(it)) },
                onOpenExtendedAi = { onAction(RoomAction.OpenExtendedAiSelection) },
                onSelectDifficulty = { diff ->
                    onAction(RoomAction.AddAiToSlot(uiState.aiDialogTargetSlot, diff))
                },
                onBack = { onAction(RoomAction.BackToAiTypeSelection) },
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
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = ChuUiTokens.TextSecondary)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = if (uiState.isHost) "我的房间（房主）" else "房间（成员）",
            style = MaterialTheme.typography.titleMedium,
            color = ChuUiTokens.TextPrimary,
        )
        Spacer(modifier = Modifier.width(8.dp))
        if (uiState.isHost) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(ChuUiTokens.SubtleGlow)
                    .border(1.dp, ChuUiTokens.GoldHighlight, RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Text("房主", style = MaterialTheme.typography.labelSmall, color = ChuUiTokens.GoldAccent)
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        val filledCount = uiState.slots.count { it.occupantType != null }
        Text(
            text = "$filledCount / 4",
            style = MaterialTheme.typography.bodyMedium,
            color = ChuUiTokens.TextSecondary,
        )
        Spacer(modifier = Modifier.width(16.dp))
        if (uiState.totalGamesPlayed > 0) {
            Text(
                text = "总局数 ${uiState.totalGamesPlayed}",
                style = MaterialTheme.typography.labelMedium,
                color = ChuUiTokens.TextMuted,
            )
            Spacer(modifier = Modifier.width(12.dp))
        }
        if (uiState.isHost) {
            TextButton(onClick = onResetScores) {
                Text("重置分数", style = MaterialTheme.typography.labelMedium, color = ChuUiTokens.TextMuted)
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
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
            SlotCard(
                slot = uiState.slots[1],
                isHost = uiState.isHost,
                showActionMenu = uiState.showSlotActionMenu && uiState.slotActionMenuTarget == 1,
                onAction = onAction,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
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
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
            SlotCard(
                slot = uiState.slots[3],
                isHost = uiState.isHost,
                showActionMenu = uiState.showSlotActionMenu && uiState.slotActionMenuTarget == 3,
                onAction = onAction,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
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
        isHostSlot -> ChuUiTokens.InputBorderFocused
        isEmpty -> ChuUiTokens.RowBorder
        else -> ChuUiTokens.RowBorder
    }
    val bgColor = if (isEmpty) ChuUiTokens.Section else ChuUiTokens.Row

    Box(
        modifier = modifier
            .shadow(ChuUiTokens.SectionShadow, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .border(
                width = if (isHostSlot) 1.5.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(14.dp),
            )
            .clickable { onAction(RoomAction.OpenSlotActionMenu(slot.slotIndex)) },
        contentAlignment = Alignment.Center,
    ) {
        if (isEmpty) {
            EmptySlotContent(slotIndex = slot.slotIndex)
        } else {
            FilledSlotContent(slot = slot)
        }

        if (showActionMenu) {
            Box(modifier = Modifier.align(Alignment.TopEnd)) {
                SlotActionMenu(slot = slot, isHost = isHost, onAction = onAction)
            }
        }
    }
}

@Composable
private fun SlotActionMenu(
    slot: SlotState,
    isHost: Boolean,
    onAction: (RoomAction) -> Unit,
) {
    DropdownMenu(
        expanded = true,
        onDismissRequest = { onAction(RoomAction.DismissSlotActionMenu) },
        containerColor = ChuUiTokens.Card,
    ) {
        if (slot.occupantType == null) {
            if (isHost) {
                DropdownMenuItem(
                    text = { Text("添加 AI", color = ChuUiTokens.GoldAccent) },
                    onClick = {
                        onAction(RoomAction.DismissSlotActionMenu)
                        onAction(RoomAction.OpenAiDialog(slot.slotIndex))
                    },
                )
            }
            DropdownMenuItem(
                text = { Text("请求换位", color = ChuUiTokens.GoldAccent) },
                onClick = {
                    onAction(RoomAction.DismissSlotActionMenu)
                    onAction(RoomAction.RequestSwapWithSlot(slot.slotIndex))
                },
            )
        }
        if (slot.occupantType != null && !slot.isLocalPlayer) {
            DropdownMenuItem(
                text = { Text("请求换位", color = ChuUiTokens.GoldAccent) },
                onClick = {
                    onAction(RoomAction.DismissSlotActionMenu)
                    onAction(RoomAction.RequestSwapWithSlot(slot.slotIndex))
                },
            )
        }
        val canRemoveOccupant = isHost && slot.occupantType != null &&
            (!slot.isLocalPlayer || BuildConfig.DEBUG)
        if (canRemoveOccupant) {
            DropdownMenuItem(
                text = { Text("移除", color = ChuUiTokens.Error) },
                onClick = { onAction(RoomAction.RemoveSlotOccupant(slot.slotIndex)) },
            )
        }
        DropdownMenuItem(
            text = { Text("取消", color = ChuUiTokens.TextSecondary) },
            onClick = { onAction(RoomAction.DismissSlotActionMenu) },
        )
    }
}

@Composable
private fun EmptySlotContent(slotIndex: Int) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(ChuUiTokens.IconTile)
                .border(1.dp, ChuUiTokens.RowBorder, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = null,
                tint = ChuUiTokens.TextMuted,
                modifier = Modifier.size(24.dp),
            )
        }
        Text(
            text = "位置 ${slotIndex + 1}",
            style = MaterialTheme.typography.labelMedium,
            color = ChuUiTokens.TextMuted,
        )
        Text(
            text = "空位 / 可加入",
            style = MaterialTheme.typography.labelSmall,
            color = ChuUiTokens.TextMuted,
        )
    }
}

@Suppress("CyclomaticComplexMethod", "LongMethod")
@Composable
private fun FilledSlotContent(slot: SlotState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(ChuUiTokens.AvatarBg)
                .border(
                    width = if (slot.isLocalPlayer) 2.dp else 1.5.dp,
                    color = if (slot.isLocalPlayer) ChuUiTokens.InputBorderFocused else ChuUiTokens.AvatarBorder,
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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = slot.displayName,
                style = MaterialTheme.typography.bodyMedium,
                color = ChuUiTokens.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )

            val scoreColor = if (slot.cumulativeScore >= 0) ChuUiTokens.Success else ChuUiTokens.Error
            Text(
                text = if (slot.cumulativeScore >= 0) "+${slot.cumulativeScore}" else "${slot.cumulativeScore}",
                style = MaterialTheme.typography.labelMedium,
                color = scoreColor,
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val typeLabel = when (slot.occupantType) {
                SlotOccupantType.AI -> {
                    val aiLabel = slot.aiType?.let { it.shortLabel + " AI" } ?: "AI"
                    aiLabel
                }
                SlotOccupantType.HUMAN_HOST -> "房主"
                SlotOccupantType.HUMAN_MEMBER -> "成员"
                null -> ""
            }
            if (typeLabel.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(ChuUiTokens.IconTile)
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                ) {
                    Text(typeLabel, style = MaterialTheme.typography.labelSmall, color = ChuUiTokens.TextSecondary)
                }
            }

            val (statusDot, statusText) = when (slot.connectionStatus) {
                MemberConnectionStatus.READY -> ChuUiTokens.Success to "已准备"
                MemberConnectionStatus.NOT_READY -> ChuUiTokens.Warning to "未准备"
                MemberConnectionStatus.DISCONNECTED -> ChuUiTokens.TextMuted to "掉线"
                MemberConnectionStatus.CONNECTED -> ChuUiTokens.GoldAccent to "已连接"
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
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
    valueColor: Color = ChuUiTokens.TextSecondary,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = ChuUiTokens.TextMuted)
        Text(value, style = MaterialTheme.typography.bodySmall, color = valueColor)
    }
}

@Suppress("LongMethod")
@Composable
private fun ControlPanel(
    uiState: RoomUiState,
    onAction: (RoomAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .shadow(ChuUiTokens.SectionShadow, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(ChuUiTokens.Section)
            .border(1.dp, ChuUiTokens.SectionBorder, RoundedCornerShape(16.dp))
            .padding(20.dp),
    ) {
        Text("房间信息", style = MaterialTheme.typography.titleMedium, color = ChuUiTokens.TextPrimary)
        Spacer(modifier = Modifier.height(6.dp))
        InfoRow(label = "房主设备", value = uiState.hostDeviceName.ifEmpty { "本机" })
        Spacer(modifier = Modifier.height(2.dp))
        InfoRow(label = "当前规则", value = uiState.currentRule.label)
        Spacer(modifier = Modifier.height(2.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("蓝牙状态", style = MaterialTheme.typography.bodySmall, color = ChuUiTokens.TextMuted)
                Text(
                    text = if (uiState.bluetoothVisible) "已开启" else "未开启",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (uiState.bluetoothVisible) ChuUiTokens.Success else ChuUiTokens.TextMuted,
                )
            }
            if (uiState.canEnableBroadcast) {
                Spacer(modifier = Modifier.width(12.dp))
                ChuButton(
                    text = "启动蓝牙",
                    onClick = { onAction(RoomAction.StartHostListening) },
                    style = ChuButtonStyle.SECONDARY,
                    modifier = Modifier
                        .width(BroadcastButtonWidth)
                        .height(BroadcastButtonHeight),
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(ChuUiTokens.Divider),
        )
        Spacer(modifier = Modifier.height(4.dp))

        if (uiState.isHost) {
            Text("房主操作", style = MaterialTheme.typography.labelLarge, color = ChuUiTokens.TextSecondary)
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChuButton(
                    text = "规则: ${uiState.currentRule.label}",
                    onClick = { onAction(RoomAction.ToggleRule) },
                    style = ChuButtonStyle.SECONDARY,
                    modifier = Modifier
                        .weight(1f)
                        .height(HostActionButtonHeight),
                )
                ChuButton(
                    text = "AI速度: ${uiState.aiPlaySpeed.label}",
                    onClick = { onAction(RoomAction.ToggleAiPlaySpeed) },
                    style = ChuButtonStyle.SECONDARY,
                    modifier = Modifier
                        .weight(1f)
                        .height(HostActionButtonHeight),
                )
            }

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
                modifier = Modifier
                    .fillMaxWidth()
                    .height(HostActionButtonHeight)
                    .testTag(ComposeTestTags.START_GAME_BUTTON),
            )
        } else {
            val localSlot = uiState.slots.firstOrNull { it.isLocalPlayer }
            val isReady = localSlot?.connectionStatus == MemberConnectionStatus.READY

            Text("成员操作", style = MaterialTheme.typography.labelLarge, color = ChuUiTokens.TextSecondary)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "已连接 ${uiState.hostDeviceName.ifEmpty { "房主" }}",
                style = MaterialTheme.typography.bodySmall,
                color = ChuUiTokens.TextSecondary,
            )

            Spacer(modifier = Modifier.weight(1f))

            ChuButton(
                text = if (isReady) "取消准备" else "准备",
                onClick = { onAction(RoomAction.ToggleReady) },
                style = if (isReady) ChuButtonStyle.SECONDARY else ChuButtonStyle.PRIMARY,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(MemberActionButtonHeight),
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (uiState.connectionHint.isNotEmpty()) {
            Text(
                text = uiState.connectionHint,
                style = MaterialTheme.typography.bodySmall,
                color = ChuUiTokens.TextMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Suppress("LongMethod", "UnusedParameter")
@Composable
private fun RoomAiDifficultyDialog(
    step: AiSelectionStep,
    selectedAiType: AIType?,
    onSelectType: (AIType) -> Unit,
    onOpenExtendedAi: () -> Unit,
    onSelectDifficulty: (RoomAiDifficulty) -> Unit,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
) {
    val onnxDifficulties = RoomAiDifficulty.entries.filter { it.aiType == AIType.ONNX_RL }
    val isOnnxAvailable = BuildConfig.ONNX_AVAILABLE

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when (step) {
                    AiSelectionStep.SELECT_TYPE -> "选择 AI 类型"
                    AiSelectionStep.SELECT_DIFFICULTY -> "选择 AI 难度"
                    AiSelectionStep.SELECT_EXTENDED_AI -> "选择扩展 AI"
                },
                style = MaterialTheme.typography.titleMedium,
                color = ChuUiTokens.TextPrimary,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (step) {
                    AiSelectionStep.SELECT_TYPE -> {
                        ChuButton(
                            text = "规则型 AI",
                            onClick = {
                                onSelectType(AIType.RULE_BASED)
                                onSelectDifficulty(RoomAiDifficulty.RULE_NORMAL)
                            },
                            style = ChuButtonStyle.SECONDARY,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        ChuButton(
                            text = if (isOnnxAvailable) "RL训练 AI" else "RL训练 AI（暂不可用）",
                            onClick = { onSelectType(AIType.ONNX_RL) },
                            style = ChuButtonStyle.SECONDARY,
                            enabled = isOnnxAvailable,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        ChuButton(
                            text = if (isOnnxAvailable) "扩展 AI" else "扩展 AI（暂不可用）",
                            onClick = onOpenExtendedAi,
                            style = ChuButtonStyle.SECONDARY,
                            enabled = isOnnxAvailable,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    AiSelectionStep.SELECT_DIFFICULTY -> {
                        onnxDifficulties.forEach { diff ->
                            ChuButton(
                                text = diff.difficultyLevel.symbol + " " + diff.label.substringAfter(" - "),
                                onClick = { onSelectDifficulty(diff) },
                                style = ChuButtonStyle.SECONDARY,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    AiSelectionStep.SELECT_EXTENDED_AI -> {
                        listOf(RoomAiDifficulty.ONNX_V2, RoomAiDifficulty.ONNX_V3).forEach { diff ->
                            ChuButton(
                                text = diff.label,
                                onClick = { onSelectDifficulty(diff) },
                                style = ChuButtonStyle.SECONDARY,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (step == AiSelectionStep.SELECT_DIFFICULTY || step == AiSelectionStep.SELECT_EXTENDED_AI) {
                TextButton(onClick = onBack) {
                    Text("返回", color = ChuUiTokens.TextSecondary)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = ChuUiTokens.TextSecondary)
            }
        },
        containerColor = ChuUiTokens.Card,
        titleContentColor = ChuUiTokens.TextPrimary,
        textContentColor = ChuUiTokens.TextSecondary,
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
        title = { Text("换位请求", style = MaterialTheme.typography.titleMedium, color = ChuUiTokens.TextPrimary) },
        text = {
            Text(
                "${request.requesterName} 请求与你换位，是否同意？",
                style = MaterialTheme.typography.bodyMedium,
                color = ChuUiTokens.TextSecondary,
            )
        },
        confirmButton = {
            ChuButton(text = "同意", onClick = onConfirm, style = ChuButtonStyle.PRIMARY)
        },
        dismissButton = {
            ChuButton(text = "拒绝", onClick = onDecline, style = ChuButtonStyle.SECONDARY)
        },
        containerColor = ChuUiTokens.Card,
        shape = RoundedCornerShape(16.dp),
    )
}
