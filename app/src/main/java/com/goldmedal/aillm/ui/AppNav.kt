package com.goldmedal.aillm.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
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
import com.goldmedal.aillm.core.design.AillmMotion
import com.goldmedal.aillm.core.design.LiquidGlassSurface
import com.goldmedal.aillm.core.design.Spacing
import com.goldmedal.aillm.core.design.liquidPress
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
import com.goldmedal.aillm.ui.decision.DecisionScreen
import com.goldmedal.aillm.ui.history.HistoryScreen
import com.goldmedal.aillm.ui.models.ModelsScreen

object AppRoutes {
    // VON is the app's front door: the decision screen is where the app opens.
    const val DECISION = "decision"
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
    AppRoutes.DECISION,
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

    fun openDecision() {
        navController.navigate(AppRoutes.DECISION) { launchSingleTop = true }
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
            startDestination = AppRoutes.DECISION,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(AppRoutes.DECISION) {
                DecisionScreen()
            }
            composable(AppRoutes.CHAT) {
                ChatScreen(
                    onOpenModels = { navigateTopLevel(AppRoutes.MODELS) },
                    onOpenMemory = { navController.navigate(AppRoutes.MEMORY) },
                    onOpenDecision = ::openDecision
                )
            }
            composable(
                route = AppRoutes.CHAT_WITH_ID,
                arguments = listOf(navArgument("chatId") { type = NavType.LongType })
            ) { entry ->
                ChatScreen(
                    chatId = entry.arguments?.getLong("chatId"),
                    onOpenModels = { navigateTopLevel(AppRoutes.MODELS) },
                    onOpenMemory = { navController.navigate(AppRoutes.MEMORY) },
                    onOpenDecision = ::openDecision
                )
            }
            composable(AppRoutes.HISTORY) {
                HistoryScreen(
                    onOpenChat = ::openChat,
                    // The controller bumped by HistoryViewModel clears the Chat
                    // tab, so landing there is a genuinely new conversation.
                    onNewChat = { navigateTopLevel(AppRoutes.CHAT) }
                )
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
 *
 * The selected destination is a translucent accent pill whose colour and width
 * spring in, so switching tabs reads as liquid filling the space rather than a
 * hard swap of backgrounds.
 */
@Composable
private fun AppBottomBar(currentRoute: String?, onSelect: (String) -> Unit) {
    val items = listOf(
        BottomItem(AppRoutes.DECISION, "VON", Icons.Default.ThumbUp),
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
        LiquidGlassSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            animatedSheen = true
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
                    val interactionSource = remember { MutableInteractionSource() }
                    val fill by animateFloatAsState(
                        targetValue = if (selected) 1f else 0f,
                        animationSpec = spring(
                            dampingRatio = 0.55f,
                            stiffness = Spring.StiffnessMediumLow
                        ),
                        label = "bottomBarSelection"
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(MaterialTheme.shapes.large)
                            .liquidPress(interactionSource, pressedScale = 0.94f)
                            .clickable(interactionSource = interactionSource, indication = null) {
                                onSelect(item.route)
                            }
                            .background(
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = fill),
                                MaterialTheme.shapes.large
                            )
                            .padding(vertical = Spacing.sm),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.label,
                                tint = lerp(
                                    MaterialTheme.colorScheme.onSurfaceVariant,
                                    MaterialTheme.colorScheme.onPrimaryContainer,
                                    fill
                                ),
                                modifier = Modifier.size(18.dp)
                            )
                            AnimatedVisibility(
                                visible = selected,
                                enter = fadeIn(tween(AillmMotion.fast)) +
                                    expandHorizontally(tween(AillmMotion.medium), expandFrom = Alignment.Start),
                                exit = fadeOut(tween(AillmMotion.fast)) +
                                    shrinkHorizontally(tween(AillmMotion.fast), shrinkTowards = Alignment.Start)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
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
}

private data class BottomItem(val route: String, val label: String, val icon: ImageVector)
