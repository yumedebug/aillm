package com.goldmedal.aillm.settings.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
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
fun AiSettingsScreen(
    onBack: () -> Unit,
    onOpenModels: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val loadedModel by viewModel.loadedChatModel.collectAsState()
    val temperature by viewModel.temperature.collectAsState()
    val maxTokens by viewModel.maxTokens.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AillmTopBar(title = "AI behaviour", onBack = onBack) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            SettingsGroup(title = "Model") {
                SettingsRow(
                    title = "Chat model",
                    subtitle = loadedModel?.name ?: "None loaded",
                    showChevron = true,
                    onClick = onOpenModels
                )
            }

            SettingsGroup(title = "Generation") {
                SliderRow(
                    title = "Creativity",
                    value = temperature,
                    valueText = String.format("%.2f", temperature),
                    range = 0f..1.5f,
                    onValueChange = { viewModel.setTemperature(it) }
                )
                AillmDivider()
                SliderRow(
                    title = "Max reply length",
                    value = maxTokens.toFloat(),
                    valueText = "$maxTokens tokens",
                    range = 256f..4096f,
                    onValueChange = { viewModel.setMaxTokens(it.toInt()) }
                )
            }

            Spacer(Modifier.height(Spacing.lg))
            Text(
                text = "Lower creativity gives more focused answers. Higher values explore more.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.lg)
            )
            Spacer(Modifier.height(Spacing.xxl))
        }
    }
}

@Composable
private fun SliderRow(
    title: String,
    value: Float,
    valueText: String,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md)) {
        androidx.compose.foundation.layout.Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
