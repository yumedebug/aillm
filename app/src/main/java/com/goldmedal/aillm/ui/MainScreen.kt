package com.goldmedal.aillm.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.goldmedal.aillm.ai.modelmanager.ModelManager
import com.goldmedal.aillm.chat.ui.ChatScreen
import com.goldmedal.aillm.memory.ui.MemoryScreen
import com.goldmedal.aillm.settings.ui.SettingsScreen
import com.goldmedal.aillm.ui.models.ModelsScreen

/**
 * Bottom navigation destinations.
 * AI chat is the hero. History / Models / Settings orbit around it.
 * Memory & Files & Web Search intentionally stay out of the top level.
 */
enum class MainTab(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    Chat("chat", "Chat", Icons.Default.Chat),
    History("history", "History", Icons.Default.History),
    Models("models", "Models", Icons.Default.Memory),
    Settings("settings", "Settings", Icons.Default.Settings)
}

@Composable
fun MainScreen(
    onOpenMemory: () -> Unit = {},
    onOpenFiles: () -> Unit = {}
) {
    var currentTab by rememberSaveable { mutableStateOf(MainTab.Chat) }
    var showMemory by rememberSaveable { mutableStateOf(false) }

    if (showMemory) {
        MemoryScreen(onBackClick = { showMemory = false })
        return
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                MainTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = currentTab == tab,
                        onClick = { currentTab = tab },
                        icon = {
                            Icon(tab.icon, contentDescription = tab.label)
                        },
                        label = { Text(tab.label) },
                        colors = NavigationBarItemDefaults.colors()
                    )
                }
            }
        }
    ) { padding ->
        val contentModifier = Modifier
            .fillMaxSize()
            .padding(padding)

        when (currentTab) {
            MainTab.Chat -> ChatScreen(
                onMenuClick = { onOpenMemory() }
            )
            MainTab.History -> HistoryScreen(
                onBackClick = { },
                onOpenChat = { }
            )
            MainTab.Models -> ModelsScreen()
            MainTab.Settings -> SettingsScreen(onBackClick = {})
        }
    }
}
