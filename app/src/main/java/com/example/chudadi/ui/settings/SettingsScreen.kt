package com.example.chudadi.ui.settings

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.chudadi.R
import com.example.chudadi.ui.components.ChuButton
import com.example.chudadi.ui.ComposeTestTags
import com.example.chudadi.ui.components.ChuButtonStyle
import com.example.chudadi.ui.theme.ChuUiTokens


private data class SettingsActions(
    val onEnterEditName: () -> Unit,
    val onNameChanged: (String) -> Unit,
    val onConfirmName: () -> Unit,
    val onCancelEditName: () -> Unit,
    val onNightModeChanged: (Boolean) -> Unit,
    val onSoundEnabledChanged: (Boolean) -> Unit,
    val onBgmEnabledChanged: (Boolean) -> Unit,
)

private data class SettingsContentState(
    val playerName: String,
    val avatarResId: Int,
    val nightMode: Boolean,
    val soundEnabled: Boolean,
    val bgmEnabled: Boolean,
    val uiState: SettingsUiState,
)

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playerName by viewModel.playerName.collectAsState()
    val avatarResId by viewModel.avatarResId.collectAsState()
    val nightMode by viewModel.nightMode.collectAsState()
    val soundEnabled by viewModel.soundEnabled.collectAsState()
    val bgmEnabled by viewModel.bgmEnabled.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val actions = remember(viewModel) {
        SettingsActions(
            onEnterEditName = viewModel::onEnterEditName,
            onNameChanged = viewModel::onNameChanged,
            onConfirmName = viewModel::onConfirmName,
            onCancelEditName = viewModel::onCancelEditName,
            onNightModeChanged = viewModel::onNightModeChanged,
            onSoundEnabledChanged = viewModel::onSoundEnabledChanged,
            onBgmEnabledChanged = viewModel::onBgmEnabledChanged,
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ChuUiTokens.Outer),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.88f)
                .clip(RoundedCornerShape(24.dp))
                .background(ChuUiTokens.Card)
                .border(1.dp, ChuUiTokens.CardBorder, RoundedCornerShape(24.dp)),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                SettingsTopBar(
                    onBack = onNavigateBack,
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(ChuUiTokens.Divider),
                )
                SettingsContent(
                    state = SettingsContentState(
                        playerName = playerName,
                        avatarResId = avatarResId,
                        nightMode = nightMode,
                        soundEnabled = soundEnabled,
                        bgmEnabled = bgmEnabled,
                        uiState = uiState,
                    ),
                    actions = actions,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(24.dp),
                )
            }
        }
    }
}

@Composable
private fun SettingsTopBar(
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                tint = ChuUiTokens.TextSecondary,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "设置",
            style = MaterialTheme.typography.titleMedium,
            color = ChuUiTokens.TextPrimary,
        )
    }
}

@Composable
private fun SettingsContent(
    state: SettingsContentState,
    actions: SettingsActions,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = modifier
            .verticalScroll(scrollState)
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        // 玩家信息卡片
        PlayerInfoCard(
            playerName = state.playerName,
            avatarResId = state.avatarResId,
            uiState = state.uiState,
            actions = actions,
        )

        AppearanceSettingsCard(
            nightMode = state.nightMode,
            onNightModeChanged = actions.onNightModeChanged,
        )

        AudioSettingsCard(
            soundEnabled = state.soundEnabled,
            bgmEnabled = state.bgmEnabled,
            onSoundEnabledChanged = actions.onSoundEnabledChanged,
            onBgmEnabledChanged = actions.onBgmEnabledChanged,
        )

        // 版本信息
        Text(
            text = "版本 1.0",
            style = MaterialTheme.typography.bodySmall,
            color = ChuUiTokens.TextMuted,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun AppearanceSettingsCard(
    nightMode: Boolean,
    onNightModeChanged: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(ChuUiTokens.Section)
            .border(1.dp, ChuUiTokens.SectionBorder, RoundedCornerShape(16.dp))
            .clickable { onNightModeChanged(!nightMode) }
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "外观设置",
                style = MaterialTheme.typography.titleMedium,
                color = ChuUiTokens.TextPrimary,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "夜间模式",
                style = MaterialTheme.typography.bodyLarge,
                color = ChuUiTokens.TextPrimary,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "深灰蓝黑背景 · 金色边框高光",
                style = MaterialTheme.typography.bodySmall,
                color = ChuUiTokens.TextSecondary,
            )
        }
        Switch(
            checked = nightMode,
            onCheckedChange = onNightModeChanged,
            colors = SwitchDefaults.colors(
                checkedThumbColor = ChuUiTokens.TextPrimary,
                checkedTrackColor = ChuUiTokens.GoldAccent,
                checkedBorderColor = ChuUiTokens.GoldAccent,
                uncheckedThumbColor = ChuUiTokens.TextSecondary,
                uncheckedTrackColor = ChuUiTokens.Input,
                uncheckedBorderColor = ChuUiTokens.InputBorder,
            ),
            modifier = Modifier.testTag(ComposeTestTags.NIGHT_MODE_SWITCH),
        )
    }
}

@Composable
private fun AudioSettingsCard(
    soundEnabled: Boolean,
    bgmEnabled: Boolean,
    onSoundEnabledChanged: (Boolean) -> Unit,
    onBgmEnabledChanged: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(ChuUiTokens.Section)
            .border(1.dp, ChuUiTokens.SectionBorder, RoundedCornerShape(16.dp))
            .padding(20.dp),
    ) {
        Text(
            text = "音频设置",
            style = MaterialTheme.typography.titleMedium,
            color = ChuUiTokens.TextPrimary,
        )
        Spacer(modifier = Modifier.height(16.dp))
        AudioSwitchRow(
            label = "音效",
            description = "出牌、轮次、胜负等游戏音效",
            checked = soundEnabled,
            onCheckedChange = onSoundEnabledChanged,
        )
        Spacer(modifier = Modifier.height(12.dp))
        AudioSwitchRow(
            label = "背景音乐",
            description = "菜单和游戏中的背景音乐",
            checked = bgmEnabled,
            onCheckedChange = onBgmEnabledChanged,
        )
    }
}

@Composable
private fun AudioSwitchRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ChuUiTokens.Input)
            .border(1.dp, ChuUiTokens.InputBorder, RoundedCornerShape(12.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = ChuUiTokens.TextPrimary,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = ChuUiTokens.TextSecondary,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = ChuUiTokens.TextPrimary,
                checkedTrackColor = ChuUiTokens.GoldAccent,
                checkedBorderColor = ChuUiTokens.GoldAccent,
                uncheckedThumbColor = ChuUiTokens.TextSecondary,
                uncheckedTrackColor = ChuUiTokens.Input,
                uncheckedBorderColor = ChuUiTokens.InputBorder,
            ),
        )
    }
}

@Composable
private fun PlayerInfoCard(
    playerName: String,
    avatarResId: Int,
    uiState: SettingsUiState,
    actions: SettingsActions,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(ChuUiTokens.Section)
            .border(1.dp, ChuUiTokens.SectionBorder, RoundedCornerShape(16.dp))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "玩家信息",
            style = MaterialTheme.typography.titleMedium,
            color = ChuUiTokens.TextPrimary,
            modifier = Modifier.align(Alignment.Start),
        )

        Spacer(modifier = Modifier.height(20.dp))

        // 头像
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(ChuUiTokens.AvatarBg)
                .border(2.dp, ChuUiTokens.AvatarBorder, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            val actualResId = if (avatarResId != 0) avatarResId else R.drawable.avatar
            Image(
                painter = painterResource(actualResId),
                contentDescription = "头像",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }

        Text(
            text = "头像（暂不支持自定义）",
            style = MaterialTheme.typography.labelSmall,
            color = ChuUiTokens.TextMuted,
            modifier = Modifier.padding(top = 8.dp),
        )

        Spacer(modifier = Modifier.height(20.dp))

        // 名称编辑区
        if (uiState.isEditingName) {
            NameEditField(
                value = uiState.editingName,
                onValueChange = actions.onNameChanged,
                error = uiState.nameError,
                onConfirm = actions.onConfirmName,
                onCancel = actions.onCancelEditName,
            )
        } else {
            NameDisplayField(
                playerName = playerName,
                onEdit = actions.onEnterEditName,
            )
        }
    }
}

@Composable
private fun NameDisplayField(
    playerName: String,
    onEdit: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ChuUiTokens.Input)
            .border(1.dp, ChuUiTokens.InputBorder, RoundedCornerShape(12.dp))
            .clickable { onEdit() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "显示名称",
                style = MaterialTheme.typography.labelSmall,
                color = ChuUiTokens.TextMuted,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = playerName,
                style = MaterialTheme.typography.bodyLarge,
                color = ChuUiTokens.TextPrimary,
            )
        }
        Icon(
            Icons.Default.Edit,
            contentDescription = "编辑",
            tint = ChuUiTokens.GoldAccent,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun NameEditField(
    value: String,
    onValueChange: (String) -> Unit,
    error: String?,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                if (it.length <= com.example.chudadi.data.repository.PlayerPreferencesRepository.MAX_NAME_LENGTH) {
                    onValueChange(it)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = {
                Text(
                    text = "显示名称",
                    color = ChuUiTokens.TextMuted,
                )
            },
            placeholder = {
                Text(
                    text = "请输入玩家名称",
                    color = ChuUiTokens.TextMuted,
                )
            },
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = ChuUiTokens.TextPrimary),
            isError = error != null,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onConfirm() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = ChuUiTokens.TextPrimary,
                unfocusedTextColor = ChuUiTokens.TextPrimary,
                focusedContainerColor = ChuUiTokens.Input,
                unfocusedContainerColor = ChuUiTokens.Input,
                errorContainerColor = ChuUiTokens.Input,
                focusedBorderColor = ChuUiTokens.InputBorderFocused,
                unfocusedBorderColor = ChuUiTokens.InputBorder,
                errorBorderColor = ChuUiTokens.Error,
                focusedLabelColor = ChuUiTokens.GoldAccent,
                unfocusedLabelColor = ChuUiTokens.TextMuted,
                cursorColor = ChuUiTokens.GoldAccent,
            ),
        )

        if (error != null) {
            Text(
                text = error,
                style = MaterialTheme.typography.labelSmall,
                color = ChuUiTokens.Error,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ChuButton(
                text = "取消",
                onClick = onCancel,
                style = ChuButtonStyle.SECONDARY,
                modifier = Modifier.weight(1f),
            )
            ChuButton(
                text = "保存",
                onClick = onConfirm,
                style = ChuButtonStyle.PRIMARY,
                enabled = error == null && value.isNotBlank(),
                modifier = Modifier.weight(1f),
            )
        }
    }
}
