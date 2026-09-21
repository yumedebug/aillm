package com.goldmedal.aillm.settings.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.goldmedal.aillm.core.design.AillmTopBar
import com.goldmedal.aillm.core.design.SettingsGroup
import com.goldmedal.aillm.core.design.SettingsRow
import com.goldmedal.aillm.core.design.Spacing

@Composable
fun AboutScreen(
    appVersion: String,
    onBack: () -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AillmTopBar(title = "About", onBack = onBack) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.lg)) {
                Text(
                    text = "AILLM",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = "A local assistant with long-term memory that lives on your phone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            SettingsGroup(title = "Info") {
                SettingsRow(title = "Version", subtitle = appVersion)
                com.goldmedal.aillm.core.design.AillmDivider()
                SettingsRow(title = "License", subtitle = "MIT")
                com.goldmedal.aillm.core.design.AillmDivider()
                SettingsRow(
                    title = "Privacy",
                    subtitle = "Chats, memory, images and inference stay on-device"
                )
            }
            Spacer(Modifier.height(Spacing.xxl))
        }
    }
}
