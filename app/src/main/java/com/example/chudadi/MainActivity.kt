package com.example.chudadi

import android.bluetooth.BluetoothAdapter
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModelProvider
import com.example.chudadi.data.repository.PlayerPreferencesRepository
import com.example.chudadi.data.repository.ReconnectSessionRepository
import com.example.chudadi.navigation.ChuDaDiNavGraph
import com.example.chudadi.network.bluetooth.BluetoothPermissionUtils
import com.example.chudadi.network.room.BluetoothRoomRepository
import com.example.chudadi.ui.room.RoomViewModel
import com.example.chudadi.ui.theme.ChuDaDiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val playerPrefsRepository = PlayerPreferencesRepository(applicationContext)
        val reconnectSessionRepository = ReconnectSessionRepository(applicationContext)
        val bluetoothRoomRepository = BluetoothRoomRepository(applicationContext, reconnectSessionRepository)
        val roomViewModelFactory = RoomViewModel.factory(
            playerPrefsRepository = playerPrefsRepository,
            bluetoothRoomRepository = bluetoothRoomRepository,
            reconnectSessionRepository = reconnectSessionRepository,
        )
        val roomViewModel = ViewModelProvider(this, roomViewModelFactory)[RoomViewModel::class.java]

        enableEdgeToEdge()
        setContent {
            val nightMode by playerPrefsRepository.nightMode.collectAsState(initial = false)
            ChuDaDiTheme(nightMode = nightMode) {
                var pendingEnableCallback by remember { mutableStateOf<(() -> Unit)?>(null) }
                var pendingPermissionCallback by remember { mutableStateOf<(() -> Unit)?>(null) }
                val latestEnableCallback by rememberUpdatedState(pendingEnableCallback)
                val latestPermissionCallback by rememberUpdatedState(pendingPermissionCallback)
                val enableBluetoothLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult(),
                ) {
                    latestEnableCallback?.invoke()
                    pendingEnableCallback = null
                }
                val permissionsLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions(),
                ) {
                    latestPermissionCallback?.invoke()
                    pendingPermissionCallback = null
                }
                ChuDaDiNavGraph(
                    roomViewModel = roomViewModel,
                    playerPreferencesRepository = playerPrefsRepository,
                    localDeviceName = deviceName(),
                    onRequestBluetoothEnable = { onComplete ->
                        pendingEnableCallback = onComplete
                        enableBluetoothLauncher.launch(android.content.Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                    },
                    onRequestBluetoothPermissions = { onComplete ->
                        pendingPermissionCallback = onComplete
                        permissionsLauncher.launch(BluetoothPermissionUtils.requiredRuntimePermissions())
                    },
                )
            }
        }
    }

    private fun deviceName(): String {
        return Build.MODEL.takeIf { it.isNotBlank() } ?: "本机"
    }
}
