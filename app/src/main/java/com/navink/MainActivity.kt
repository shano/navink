package com.navink

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import com.navink.data.repository.SettingsRepository
import com.navink.player.PlayerController
import com.navink.ui.theme.NavinkTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var playerController: PlayerController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch { playerController.connect() }
        setContent {
            NavinkTheme {
                NavGraph(settingsRepository = settingsRepository)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        playerController.disconnect()
    }
}
