package com.example.chudadi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.chudadi.navigation.ChuDaDiNavGraph
import com.example.chudadi.ui.theme.ChuDaDiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ChuDaDiTheme {
                ChuDaDiNavGraph()
            }
        }
    }
}
