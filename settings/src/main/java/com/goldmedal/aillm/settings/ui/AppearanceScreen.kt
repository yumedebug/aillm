package com.goldmedal.aillm.settings.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
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

@Composable
fun AppearanceScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val themeMode by viewModel.themeMode.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AillmTopBar(title = "Appearance", onBack = onBack) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            SettingsGroup(title = "Theme") {
                ThemeOption("Follow system", ThemeMode.SYSTEM, themeMode) { viewModel.setThemeMode(it) }
                AillmDivider()
                ThemeOption("Dark", ThemeMode.DARK, themeMode) { viewModel.setThemeMode(it) }
                AillmDivider()
                ThemeOption("Light", ThemeMode.LIGHT, themeMode) { viewModel.setThemeMode(it) }
            }
            Spacer(Modifier.height(Spacing.lg))
        }
    }
}

@Composable
private fun ThemeOption(
    label: String,
    mode: ThemeMode,
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit
) {
    SettingsRow(
        title = label,
        trailing = {
            if (selected == mode) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        },
        onClick = { onSelect(mode) }
    )
}
