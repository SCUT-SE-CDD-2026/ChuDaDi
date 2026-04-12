package com.example.chudadi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import com.example.chudadi.data.repository.PlayerPreferencesRepository
import com.example.chudadi.navigation.ChuDaDiNavGraph
import com.example.chudadi.ui.room.RoomViewModel
import com.example.chudadi.ui.theme.ChuDaDiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 创建 PlayerPreferencesRepository（Application 级单例）
        val playerPrefsRepository = PlayerPreferencesRepository(applicationContext)

        // 通过 factory 创建 RoomViewModel，注入 repository
        val roomViewModelFactory = RoomViewModel.factory(playerPrefsRepository)
        val roomViewModel = ViewModelProvider(this, roomViewModelFactory)[RoomViewModel::class.java]

        enableEdgeToEdge()
        setContent {
            ChuDaDiTheme {
                ChuDaDiNavGraph(
                    roomViewModel = roomViewModel,
                    playerPreferencesRepository = playerPrefsRepository,
                )
            }
        }
    }
}
