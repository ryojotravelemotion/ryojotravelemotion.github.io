package io.github.ryojotravelemotion.jikokuhyo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.ryojotravelemotion.jikokuhyo.ui.MainViewModel
import io.github.ryojotravelemotion.jikokuhyo.ui.NearbyScreen
import io.github.ryojotravelemotion.jikokuhyo.ui.SettingsScreen
import io.github.ryojotravelemotion.jikokuhyo.ui.TimetableScreen
import io.github.ryojotravelemotion.jikokuhyo.ui.theme.JikokuhyoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            JikokuhyoTheme {
                val vm: MainViewModel = viewModel()
                // 画面は三つだけなので、ナビゲーションのライブラリは使わずに切り替える。
                // "settings"、"station:<駅のID>"、null（近くの駅）のどれか
                var screen by rememberSaveable { mutableStateOf<String?>(null) }

                BackHandler(enabled = screen != null) { screen = null }

                val current = screen
                when {
                    current == "settings" -> SettingsScreen(vm, onBack = { screen = null })
                    current != null && current.startsWith("station:") -> TimetableScreen(
                        vm = vm,
                        stationId = current.removePrefix("station:"),
                        onBack = { screen = null },
                    )
                    else -> NearbyScreen(
                        vm = vm,
                        onOpenStation = { screen = "station:$it" },
                        onOpenSettings = { screen = "settings" },
                    )
                }
            }
        }
    }
}
