package com.goldmedal.aillm.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.goldmedal.aillm.chat.ui.ChatScreen
import com.goldmedal.aillm.settings.ui.SettingsScreen
import kotlinx.coroutines.launch

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    data object Chat : Screen("chat", "Chat", Icons.Default.Chat)
    data object Settings : Screen("settings", "Settings", Icons.Default.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Chat) }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Text(
                    text = "AI Assistant",
                    modifier = Modifier.padding(16.dp),
                    style = androidx.compose.material3.MaterialTheme.typography.headlineMedium
                )
                Screen.entries.forEach { screen ->
                    NavigationDrawerItem(
                        label = { Text(screen.title) },
                        selected = currentScreen == screen,
                        onClick = {
                            currentScreen = screen
                            scope.launch { drawerState.close() }
                        },
                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                }
            }
        }
    ) {
        when (currentScreen) {
            Screen.Chat -> ChatScreen(
                onMenuClick = { scope.launch { drawerState.open() } }
            )
            Screen.Settings -> SettingsScreen(
                onBackClick = {
                    currentScreen = Screen.Chat
                    scope.launch { drawerState.open() }
                }
            )
        }
    }
}
