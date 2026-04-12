package com.example.chudadi

import android.bluetooth.BluetoothAdapter
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
import com.example.chudadi.data.repository.PlayerPreferencesRepository
import com.example.chudadi.navigation.ChuDaDiNavGraph
import com.example.chudadi.network.bluetooth.BluetoothPermissionUtils
import com.example.chudadi.network.room.BluetoothRoomRepository
import com.example.chudadi.ui.room.RoomViewModel
import com.example.chudadi.ui.theme.ChuDaDiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val playerPrefsRepository = PlayerPreferencesRepository(applicationContext)
        val bluetoothRoomRepository = BluetoothRoomRepository(applicationContext)
        val roomViewModelFactory = RoomViewModel.factory(
            playerPrefsRepository = playerPrefsRepository,
            bluetoothRoomRepository = bluetoothRoomRepository,
        )
        val roomViewModel = ViewModelProvider(this, roomViewModelFactory)[RoomViewModel::class.java]

        enableEdgeToEdge()
        setContent {
            ChuDaDiTheme {
                val enableBluetoothLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult(),
                ) { }
                val permissionsLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions(),
                ) { }
                ChuDaDiNavGraph(
                    roomViewModel = roomViewModel,
                    playerPreferencesRepository = playerPrefsRepository,
                    localDeviceName = deviceName(),
                    onRequestBluetoothEnable = {
                        enableBluetoothLauncher.launch(android.content.Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                    },
                    onRequestBluetoothPermissions = {
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
