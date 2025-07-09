package com.example.networktest

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.networktest.ui.navigation.AppNavigation
import com.example.networktest.ui.theme.NetworkTestTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NetworkTestTheme {
                AppNavigation()
            }
        }
    }
}
