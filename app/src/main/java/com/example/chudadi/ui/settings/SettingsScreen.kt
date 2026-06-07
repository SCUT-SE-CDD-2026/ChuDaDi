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
import androidx.compose.runtime.ReadOnlyComposable
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

private val SectionBg: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalChuUiPalette.current.section

private val SectionBorder: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalChuUiPalette.current.sectionBorder

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

private val InputBg: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalChuUiPalette.current.input

private val InputBorder: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalChuUiPalette.current.inputBorder

private val InputBorderFocused: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalChuUiPalette.current.inputBorderFocused

private val GoldAccent: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalChuUiPalette.current.goldAccent

private val DividerColor: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalChuUiPalette.current.divider

private val AvatarBg: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalChuUiPalette.current.avatarBg

private val AvatarBorder: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalChuUiPalette.current.avatarBorder

private val ErrorColor: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalChuUiPalette.current.error

private data class SettingsActions(
    val onEnterEditName: () -> Unit,
    val onNameChanged: (String) -> Unit,
    val onConfirmName: () -> Unit,
    val onCancelEditName: () -> Unit,
    val onNightModeChanged: (Boolean) -> Unit,
)

private data class SettingsContentState(
    val playerName: String,
    val avatarResId: Int,
    val nightMode: Boolean,
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
    val uiState by viewModel.uiState.collectAsState()
    val actions = remember(viewModel) {
        SettingsActions(
            onEnterEditName = viewModel::onEnterEditName,
            onNameChanged = viewModel::onNameChanged,
            onConfirmName = viewModel::onConfirmName,
            onCancelEditName = viewModel::onCancelEditName,
            onNightModeChanged = viewModel::onNightModeChanged,
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BgOuter),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.88f)
                .clip(RoundedCornerShape(24.dp))
                .background(BgCard)
                .border(1.dp, BgCardBorder, RoundedCornerShape(24.dp)),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                SettingsTopBar(
                    onBack = onNavigateBack,
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(DividerColor),
                )
                SettingsContent(
                    state = SettingsContentState(
                        playerName = playerName,
                        avatarResId = avatarResId,
                        nightMode = nightMode,
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
                tint = TextSecondary,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "设置",
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
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

        // 版本信息
        Text(
            text = "版本 1.0",
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
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
            .background(SectionBg)
            .border(1.dp, SectionBorder, RoundedCornerShape(16.dp))
            .clickable { onNightModeChanged(!nightMode) }
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "外观设置",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "夜间模式",
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "深灰蓝黑背景 · 金色边框高光",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
        }
        Switch(
            checked = nightMode,
            onCheckedChange = onNightModeChanged,
            colors = SwitchDefaults.colors(
                checkedThumbColor = TextPrimary,
                checkedTrackColor = GoldAccent,
                checkedBorderColor = GoldAccent,
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = InputBg,
                uncheckedBorderColor = InputBorder,
            ),
            modifier = Modifier.testTag(ComposeTestTags.NIGHT_MODE_SWITCH),
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
            .background(SectionBg)
            .border(1.dp, SectionBorder, RoundedCornerShape(16.dp))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "玩家信息",
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
            modifier = Modifier.align(Alignment.Start),
        )

        Spacer(modifier = Modifier.height(20.dp))

        // 头像
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(AvatarBg)
                .border(2.dp, AvatarBorder, CircleShape),
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
            color = TextMuted,
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
            .background(InputBg)
            .border(1.dp, InputBorder, RoundedCornerShape(12.dp))
            .clickable { onEdit() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "显示名称",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = playerName,
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary,
            )
        }
        Icon(
            Icons.Default.Edit,
            contentDescription = "编辑",
            tint = GoldAccent,
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
                    color = TextMuted,
                )
            },
            placeholder = {
                Text(
                    text = "请输入玩家名称",
                    color = TextMuted,
                )
            },
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = TextPrimary),
            isError = error != null,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onConfirm() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedContainerColor = InputBg,
                unfocusedContainerColor = InputBg,
                errorContainerColor = InputBg,
                focusedBorderColor = InputBorderFocused,
                unfocusedBorderColor = InputBorder,
                errorBorderColor = ErrorColor,
                focusedLabelColor = GoldAccent,
                unfocusedLabelColor = TextMuted,
                cursorColor = GoldAccent,
            ),
        )

        if (error != null) {
            Text(
                text = error,
                style = MaterialTheme.typography.labelSmall,
                color = ErrorColor,
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
