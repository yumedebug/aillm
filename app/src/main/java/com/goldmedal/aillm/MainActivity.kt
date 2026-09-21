package com.goldmedal.aillm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.goldmedal.aillm.ui.AILLMTheme
import com.goldmedal.aillm.ui.MainScreen
import com.goldmedal.aillm.ui.setup.SetupScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AILLMTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val prefs: SharedPreferences =
                        androidx.compose.ui.platform.LocalContext.current
                            .getSharedPreferences("aillm_onboarding", MODE_PRIVATE)
                    var showSetup by remember { mutableStateOf(prefs.getBoolean("done", false).not()) }

                    if (showSetup) {
                        SetupScreen(
                            onFinish = {
                                prefs.edit().putBoolean("done", true).apply()
                                showSetup = false
                            }
                        )
                    } else {
                        MainScreen()
                    }
                }
            }
        }
    }
}
