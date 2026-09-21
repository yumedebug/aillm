package com.goldmedal.aillm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.goldmedal.aillm.core.design.AillmAmbientBackground
import com.goldmedal.aillm.core.design.AillmTheme
import com.goldmedal.aillm.ui.AppNav
import com.goldmedal.aillm.ui.AppViewModel
import com.goldmedal.aillm.ui.setup.SetupScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val appViewModel: AppViewModel = hiltViewModel()
            val themeMode by appViewModel.themeMode.collectAsState()
            val onboarded by appViewModel.isOnboarded.collectAsState()

            AillmTheme(themeMode = themeMode) {
                // The whole app is one frosted-glass surface over this layer, so
                // screens keep transparent Scaffolds instead of painting a fill.
                Box(modifier = Modifier.fillMaxSize()) {
                    AillmAmbientBackground()
                    when (onboarded) {
                        null -> Box(Modifier.fillMaxSize())
                        false -> SetupScreen()
                        true -> AppNav()
                    }
                }
            }
        }
    }
}
