package com.example.chudadi.ui.room

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.chudadi.ui.components.ChuButton
import com.example.chudadi.ui.components.ChuButtonStyle

private val SearchBgOuter = Color(0xFF1A1008)
private val SearchBgCard = Color(0xFF241912)
private val SearchCardBorder = Color(0x44C8A96A)
private val SearchSectionBg = Color(0x22C8A96A)
private val SearchSectionBorder = Color(0x33C8A96A)
private val SearchTextPrimary = Color(0xFFF7F1E4)
private val SearchTextSecondary = Color(0xFFB8A882)
private val SearchTextMuted = Color(0xFF7A6A50)
private val SearchDivider = Color(0x33C8A96A)
private val SearchHighlight = Color(0xFFD4A85A)

@Composable
fun BluetoothSearchScreen(
    uiState: RoomUiState,
    onAction: (RoomAction) -> Unit,
    onNavigateBack: () -> Unit,
    onDismissJoinError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SearchBgOuter),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.92f)
                .shadow(24.dp, RoundedCornerShape(24.dp))
                .clip(RoundedCornerShape(24.dp))
                .background(SearchBgCard)
                .border(1.dp, SearchCardBorder, RoundedCornerShape(24.dp)),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                SearchTopBar(
                    searchState = uiState.searchState,
                    onNavigateBack = onNavigateBack,
                    onRefresh = { onAction(RoomAction.StartBluetoothDiscovery) },
                    refreshEnabled = uiState.searchState != BluetoothSearchState.CONNECTING,
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(SearchDivider),
                )
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    SearchGuidePanel(
                        hint = uiState.connectionHint,
                        modifier = Modifier
                            .weight(0.34f)
                            .fillMaxHeight(),
                    )
                    SearchDevicePanel(
                        uiState = uiState,
                        onConnect = { address -> onAction(RoomAction.ConnectToBluetoothDevice(address)) },
                        onRefresh = { onAction(RoomAction.StartBluetoothDiscovery) },
                        modifier = Modifier
                            .weight(0.66f)
                            .fillMaxHeight(),
                    )
                }
            }
        }

        uiState.joinErrorMessage?.let { message ->
            AlertDialog(
                onDismissRequest = onDismissJoinError,
                title = {
                    Text(
                        text = uiState.joinErrorTitle,
                        color = SearchTextPrimary,
                    )
                },
                text = {
                    Text(
                        text = message,
                        color = SearchTextSecondary,
                    )
                },
                confirmButton = {
                    TextButton(onClick = onDismissJoinError) {
                        Text(text = "知道了", color = SearchHighlight)
                    }
                },
                containerColor = SearchBgCard,
            )
        }
    }
}

@Composable
private fun SearchTopBar(
    searchState: BluetoothSearchState,
    onNavigateBack: () -> Unit,
    onRefresh: () -> Unit,
    refreshEnabled: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onNavigateBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                tint = SearchTextSecondary,
            )
        }
        Text(
            text = "加入房间",
            style = MaterialTheme.typography.titleMedium,
            color = SearchTextPrimary,
        )
        Box(modifier = Modifier.weight(1f))
        Text(
            text = when (searchState) {
                BluetoothSearchState.IDLE -> "待扫描"
                BluetoothSearchState.SCANNING -> "扫描中"
                BluetoothSearchState.CONNECTING -> "连接中"
                BluetoothSearchState.FAILED -> "连接失败"
            },
            style = MaterialTheme.typography.labelMedium,
            color = if (searchState == BluetoothSearchState.FAILED) Color(0xFFE57373) else SearchHighlight,
        )
        IconButton(onClick = onRefresh, enabled = refreshEnabled) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "重新扫描",
                tint = if (refreshEnabled) SearchTextSecondary else SearchTextMuted,
            )
        }
    }
}

@Composable
private fun SearchGuidePanel(
    hint: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .shadow(4.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(SearchSectionBg)
            .border(1.dp, SearchSectionBorder, RoundedCornerShape(16.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0x33C8A96A)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = SearchHighlight,
                    modifier = Modifier.size(28.dp),
                )
            }
            Text(
                text = "连接说明",
                style = MaterialTheme.typography.titleMedium,
                color = SearchTextPrimary,
            )
            Text(
                text = "1. 让房主先点击“创建房间”\n2. 选择下方设备建立连接\n3. 连接成功后自动进入房间页",
                style = MaterialTheme.typography.bodyMedium,
                color = SearchTextSecondary,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "状态提示",
                style = MaterialTheme.typography.labelLarge,
                color = SearchTextSecondary,
            )
            Text(
                text = hint.ifBlank { "准备开始扫描周围房间" },
                style = MaterialTheme.typography.bodySmall,
                color = SearchTextMuted,
            )
        }
    }
}

@Composable
private fun SearchDevicePanel(
    uiState: RoomUiState,
    onConnect: (String) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .shadow(4.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(SearchSectionBg)
            .border(1.dp, SearchSectionBorder, RoundedCornerShape(16.dp))
            .padding(20.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "可连接设备",
                style = MaterialTheme.typography.titleMedium,
                color = SearchTextPrimary,
            )
            Box(modifier = Modifier.weight(1f))
            ChuButton(
                text = "重新扫描",
                onClick = onRefresh,
                enabled = uiState.searchState != BluetoothSearchState.CONNECTING,
                style = ChuButtonStyle.SECONDARY,
                modifier = Modifier.width(120.dp),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(SearchDivider)
                .padding(top = 12.dp),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(top = 12.dp),
        ) {
            if (uiState.discoveredDevices.isEmpty()) {
                EmptyDeviceList(searchState = uiState.searchState)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(uiState.discoveredDevices, key = { it.address }) { device ->
                        DeviceRow(
                            device = device,
                            isConnecting = uiState.selectedDeviceAddress == device.address &&
                                uiState.searchState == BluetoothSearchState.CONNECTING,
                            enabled = uiState.searchState != BluetoothSearchState.CONNECTING,
                            onConnect = { onConnect(device.address) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyDeviceList(searchState: BluetoothSearchState) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = when (searchState) {
                BluetoothSearchState.SCANNING -> "正在搜索可加入的房间..."
                BluetoothSearchState.CONNECTING -> "正在建立连接..."
                BluetoothSearchState.FAILED -> "连接失败，请重新扫描"
                BluetoothSearchState.IDLE -> "暂无设备，点击右上角重新扫描"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = SearchTextMuted,
        )
    }
}

@Composable
private fun DeviceRow(
    device: DiscoveredDeviceUiState,
    isConnecting: Boolean,
    enabled: Boolean,
    onConnect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(74.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xAA1D1A14))
            .border(1.dp, SearchSectionBorder, RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0x33C8A96A)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = SearchHighlight,
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp, end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = device.name,
                style = MaterialTheme.typography.bodyLarge,
                color = SearchTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = device.address,
                style = MaterialTheme.typography.bodySmall,
                color = SearchTextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (device.isBonded) {
            Text(
                text = "已配对",
                style = MaterialTheme.typography.labelSmall,
                color = SearchHighlight,
                modifier = Modifier.padding(end = 12.dp),
            )
        }
        ChuButton(
            text = if (isConnecting) "连接中" else "连接",
            onClick = onConnect,
            enabled = enabled,
            style = if (isConnecting) ChuButtonStyle.SECONDARY else ChuButtonStyle.PRIMARY,
            modifier = Modifier.width(108.dp),
        )
    }
}
