package com.goldmedal.aillm.settings.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import com.goldmedal.aillm.settings.viewmodel.SettingsViewModel

@Composable
fun AdvancedScreen(
    onBack: () -> Unit,
    onOpenOnlineSources: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val reduceMotion by viewModel.reduceMotion.collectAsState()
    val onlineEnabled by viewModel.onlineSourcesEnabled.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AillmTopBar(title = "Advanced", onBack = onBack) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            SettingsGroup(title = "Performance") {
                SettingsRow(
                    title = "Reduce motion",
                    subtitle = "Fewer animations across the app",
                    trailing = {
                        Switch(
                            checked = reduceMotion,
                            onCheckedChange = { viewModel.setReduceMotion(it) }
                        )
                    }
                )
            }

            SettingsGroup(title = "Tools") {
                SettingsRow(
                    title = "Online sources",
                    subtitle = if (onlineEnabled) "Enabled" else "Off by default",
                    showChevron = true,
                    onClick = onOpenOnlineSources
                )
            }

            Spacer(Modifier.height(Spacing.lg))
            Text(
                text = "Performance, tools and diagnostics live here so the main screens stay uncluttered.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.lg)
            )
            Spacer(Modifier.height(Spacing.xxl))
        }
    }
}
