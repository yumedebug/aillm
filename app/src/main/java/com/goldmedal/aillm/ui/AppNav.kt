package com.goldmedal.aillm.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.goldmedal.aillm.BuildConfig
import com.goldmedal.aillm.chat.ui.ChatScreen
import com.goldmedal.aillm.core.design.AillmGlass
import com.goldmedal.aillm.core.design.Spacing
import com.goldmedal.aillm.core.design.glassBorderColor
import com.goldmedal.aillm.files.ui.FileScreen
import com.goldmedal.aillm.memory.ui.MemoryScreen
import com.goldmedal.aillm.settings.ui.AboutScreen
import com.goldmedal.aillm.settings.ui.AdvancedScreen
import com.goldmedal.aillm.settings.ui.AiSettingsScreen
import com.goldmedal.aillm.settings.ui.AppearanceScreen
import com.goldmedal.aillm.settings.ui.OnlineSourcesScreen
import com.goldmedal.aillm.settings.ui.SettingsDestination
import com.goldmedal.aillm.settings.ui.SettingsScreen
import com.goldmedal.aillm.settings.ui.StorageScreen
import com.goldmedal.aillm.ui.history.HistoryScreen
import com.goldmedal.aillm.ui.models.ModelsScreen

object AppRoutes {
    const val CHAT = "chat"
    const val CHAT_WITH_ID = "chat/{chatId}"
    const val HISTORY = "history"
    const val MODELS = "models"
    const val SETTINGS = "settings"
    const val MEMORY = "memory"
    const val FILES = "files"
    const val STORAGE = "settings/storage"
    const val APPEARANCE = "settings/appearance"
    const val AI = "settings/ai"
    const val ADVANCED = "settings/advanced"
    const val ONLINE = "settings/online"
    const val ABOUT = "settings/about"

    fun chat(chatId: Long) = "chat/$chatId"
}

private val topLevelRoutes = setOf(
    AppRoutes.CHAT,
    AppRoutes.HISTORY,
    AppRoutes.MODELS,
    AppRoutes.SETTINGS
)

@Composable
fun AppNav() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in topLevelRoutes

    fun navigateTopLevel(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun openChat(chatId: Long) {
        navController.navigate(AppRoutes.chat(chatId)) { launchSingleTop = true }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBottomBar) {
                AppBottomBar(currentRoute = currentRoute, onSelect = ::navigateTopLevel)
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = AppRoutes.CHAT,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(AppRoutes.CHAT) {
                ChatScreen(
                    onOpenModels = { navigateTopLevel(AppRoutes.MODELS) },
                    onOpenMemory = { navController.navigate(AppRoutes.MEMORY) }
                )
            }
            composable(
                route = AppRoutes.CHAT_WITH_ID,
                arguments = listOf(navArgument("chatId") { type = NavType.LongType })
            ) { entry ->
                ChatScreen(
                    chatId = entry.arguments?.getLong("chatId"),
                    onOpenModels = { navigateTopLevel(AppRoutes.MODELS) },
                    onOpenMemory = { navController.navigate(AppRoutes.MEMORY) }
                )
            }
            composable(AppRoutes.HISTORY) {
                HistoryScreen(onOpenChat = ::openChat)
            }
            composable(AppRoutes.MODELS) {
                ModelsScreen()
            }
            composable(AppRoutes.MEMORY) {
                MemoryScreen(onBack = { navController.popBackStack() })
            }
            composable(AppRoutes.FILES) {
                FileScreen(onBack = { navController.popBackStack() })
            }
            composable(AppRoutes.STORAGE) {
                StorageScreen(onBack = { navController.popBackStack() })
            }
            composable(AppRoutes.SETTINGS) {
                SettingsScreen(
                    onBack = {},
                    appVersion = BuildConfig.VERSION_NAME,
                    onNavigate = { destination ->
                        val route = when (destination) {
                            SettingsDestination.APPEARANCE -> AppRoutes.APPEARANCE
                            SettingsDestination.AI -> AppRoutes.AI
                            SettingsDestination.MODELS -> AppRoutes.MODELS
                            SettingsDestination.MEMORY -> AppRoutes.MEMORY
                            SettingsDestination.FILES -> AppRoutes.FILES
                            SettingsDestination.STORAGE -> AppRoutes.STORAGE
                            SettingsDestination.ADVANCED -> AppRoutes.ADVANCED
                            SettingsDestination.ONLINE_SOURCES -> AppRoutes.ONLINE
                            SettingsDestination.ABOUT -> AppRoutes.ABOUT
                        }
                        navController.navigate(route) { launchSingleTop = true }
                    }
                )
            }
            composable(AppRoutes.APPEARANCE) {
                AppearanceScreen(onBack = { navController.popBackStack() })
            }
            composable(AppRoutes.AI) {
                AiSettingsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenModels = { navigateTopLevel(AppRoutes.MODELS) }
                )
            }
            composable(AppRoutes.ADVANCED) {
                AdvancedScreen(
                    onBack = { navController.popBackStack() },
                    onOpenOnlineSources = { navController.navigate(AppRoutes.ONLINE) }
                )
            }
            composable(AppRoutes.ONLINE) {
                OnlineSourcesScreen(onBack = { navController.popBackStack() })
            }
            composable(AppRoutes.ABOUT) {
                AboutScreen(
                    appVersion = BuildConfig.VERSION_NAME,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}

/**
 * A floating glass pill rather than a full-width bar: four destinations only,
 * and the ambient gradient stays visible around it.
 */
@Composable
private fun AppBottomBar(currentRoute: String?, onSelect: (String) -> Unit) {
    val items = listOf(
        BottomItem(AppRoutes.CHAT, "Chat", Icons.Default.ChatBubbleOutline),
        BottomItem(AppRoutes.HISTORY, "History", Icons.Default.History),
        BottomItem(AppRoutes.MODELS, "Models", Icons.Default.Memory),
        BottomItem(AppRoutes.SETTINGS, "Settings", Icons.Default.Settings)
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.md)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = MaterialTheme.shapes.extraLarge,
            border = BorderStroke(AillmGlass.borderWidth, glassBorderColor()),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEach { item ->
                    val selected = currentRoute == item.route
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(MaterialTheme.shapes.large)
                            .background(
                                if (selected) MaterialTheme.colorScheme.primaryContainer
                                else Color.Transparent
                            )
                            .clickable { onSelect(item.route) }
                            .padding(vertical = Spacing.sm),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.label,
                                tint = if (selected) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(18.dp)
                            )
                            if (selected) {
                                Spacer(Modifier.width(Spacing.xs))
                                Text(
                                    text = item.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class BottomItem(val route: String, val label: String, val icon: ImageVector)
