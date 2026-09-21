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
import com.goldmedal.aillm.core.design.AillmTopBar
import com.goldmedal.aillm.core.design.SettingsGroup
import com.goldmedal.aillm.core.design.SettingsRow
import com.goldmedal.aillm.core.design.Spacing
import com.goldmedal.aillm.settings.viewmodel.SettingsViewModel

@Composable
fun OnlineSourcesScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val enabled by viewModel.onlineSourcesEnabled.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AillmTopBar(title = "Online sources", onBack = onBack) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            SettingsGroup {
                SettingsRow(
                    title = "Allow online sources",
                    subtitle = if (enabled) "On" else "Off",
                    trailing = {
                        Switch(
                            checked = enabled,
                            onCheckedChange = { viewModel.setOnlineSourcesEnabled(it) }
                        )
                    }
                )
            }

            Spacer(Modifier.height(Spacing.lg))
            Column(modifier = Modifier.padding(horizontal = Spacing.lg)) {
                Text(
                    text = "When this is off, your assistant has no access to the internet at all — no search tool is even attached to the model.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(Spacing.md))
                Text(
                    text = "When it is on, the assistant may look something up only when a question genuinely needs current information. At most one lookup happens per message, and results are shown with their sources.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(Spacing.md))
                Text(
                    text = "Online lookup is not configured in this build yet, so it stays inactive even when enabled.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(Spacing.xxl))
        }
    }
}
