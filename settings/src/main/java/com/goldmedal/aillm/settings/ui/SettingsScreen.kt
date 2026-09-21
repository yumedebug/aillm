package com.goldmedal.aillm.settings.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.goldmedal.aillm.core.design.AillmDivider
import com.goldmedal.aillm.core.design.AillmTopBar
import com.goldmedal.aillm.core.design.SettingsGroup
import com.goldmedal.aillm.core.design.SettingsRow
import com.goldmedal.aillm.core.design.Spacing
import com.goldmedal.aillm.core.design.ThemeMode
import com.goldmedal.aillm.settings.viewmodel.SettingsViewModel

/**
 * Settings is a short, grouped index — not a wall of toggles. Anything niche
 * (performance, online sources, diagnostics) lives a level deeper.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigate: (SettingsDestination) -> Unit,
    appVersion: String,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val themeMode by viewModel.themeMode.collectAsState()
    val loadedModel by viewModel.loadedChatModel.collectAsState()
    val installed by viewModel.installedModels.collectAsState()
    val onlineEnabled by viewModel.onlineSourcesEnabled.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AillmTopBar(title = "Settings", onBack = onBack) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            SettingsGroup(title = "Assistant") {
                SettingsRow(
                    title = "Appearance",
                    subtitle = themeMode.label(),
                    leadingIcon = Icons.Default.Palette,
                    showChevron = true,
                    onClick = { onNavigate(SettingsDestination.APPEARANCE) }
                )
                AillmDivider()
                SettingsRow(
                    title = "AI behaviour",
                    subtitle = "Generation parameters and context",
                    leadingIcon = Icons.Default.AutoAwesome,
                    showChevron = true,
                    onClick = { onNavigate(SettingsDestination.AI) }
                )
                AillmDivider()
                SettingsRow(
                    title = "Chat model",
                    subtitle = loadedModel?.name ?: "None loaded",
                    leadingIcon = Icons.Default.Tune,
                    showChevron = true,
                    onClick = { onNavigate(SettingsDestination.MODELS) }
                )
            }

            SettingsGroup(title = "Library") {
                SettingsRow(
                    title = "Models",
                    subtitle = if (installed.isEmpty()) "No models installed" else "${installed.size} installed",
                    leadingIcon = Icons.Default.Memory,
                    showChevron = true,
                    onClick = { onNavigate(SettingsDestination.MODELS) }
                )
                AillmDivider()
                SettingsRow(
                    title = "Memory",
                    subtitle = "What your assistant remembers",
                    leadingIcon = Icons.Default.Psychology,
                    showChevron = true,
                    onClick = { onNavigate(SettingsDestination.MEMORY) }
                )
                AillmDivider()
                SettingsRow(
                    title = "Files",
                    subtitle = "Documents you imported",
                    leadingIcon = Icons.Default.Storage,
                    showChevron = true,
                    onClick = { onNavigate(SettingsDestination.FILES) }
                )
                AillmDivider()
                SettingsRow(
                    title = "Storage",
                    subtitle = "Space used by models and data",
                    leadingIcon = Icons.Default.Storage,
                    showChevron = true,
                    onClick = { onNavigate(SettingsDestination.STORAGE) }
                )
            }

            SettingsGroup(title = "Advanced") {
                SettingsRow(
                    title = "Advanced",
                    subtitle = "Performance, diagnostics and tools",
                    leadingIcon = Icons.Default.Public,
                    showChevron = true,
                    onClick = { onNavigate(SettingsDestination.ADVANCED) }
                )
                AillmDivider()
                SettingsRow(
                    title = "About",
                    subtitle = "Version $appVersion",
                    leadingIcon = Icons.Default.Info,
                    showChevron = true,
                    onClick = { onNavigate(SettingsDestination.ABOUT) }
                )
            }

            Spacer(Modifier.height(Spacing.lg))
            Column(modifier = Modifier.padding(horizontal = Spacing.lg)) {
                androidx.compose.material3.Text(
                    text = if (onlineEnabled) {
                        "Online sources are on. At most one search runs per message."
                    } else {
                        "Everything you do stays on this device. Online sources are off."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(Spacing.xxl))
        }
    }
}

private fun ThemeMode.label(): String = when (this) {
    ThemeMode.SYSTEM -> "Follow system"
    ThemeMode.DARK -> "Dark"
    ThemeMode.LIGHT -> "Light"
}
