package com.goldmedal.aillm.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.goldmedal.aillm.ai.model.ModelFit
import com.goldmedal.aillm.ai.model.ModelStatus
import com.goldmedal.aillm.core.design.BadgeTone
import com.goldmedal.aillm.core.design.DownloadProgress
import com.goldmedal.aillm.core.design.LiquidGlassSurface
import com.goldmedal.aillm.core.design.PrimaryButton
import com.goldmedal.aillm.core.design.RatingStars
import com.goldmedal.aillm.core.design.SectionHeader
import com.goldmedal.aillm.core.design.Spacing
import com.goldmedal.aillm.core.design.StatusBadge
import com.goldmedal.aillm.core.design.TextAction
import com.goldmedal.aillm.core.design.formatBytes

/**
 * First-run wizard. It never downloads anything by itself — it reads the real
 * device, proposes a short list per job, and waits for the user to choose.
 */
@Composable
fun SetupScreen(viewModel: SetupViewModel = hiltViewModel()) {
    val sections by viewModel.sections.collectAsState()
    val profile = viewModel.profile
    val installedCount = sections.sumOf { section ->
        section.rows.count { it.status is ModelStatus.Installed || it.status is ModelStatus.Ready }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.lg)
            ) {
                PrimaryButton(
                    text = if (installedCount > 0) "Continue" else "Continue without a model",
                    onClick = { viewModel.finish() },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(Spacing.xs))
                TextAction(
                    text = "Skip for now",
                    onClick = { viewModel.finish() },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            item {
                Column(modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.lg)) {
                    Text(
                        text = "Welcome",
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    Text(
                        text = "Everything here runs on this phone. Here is what your device can " +
                            "handle — pick what you want, and nothing downloads until you tap Install.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item { DeviceCard(viewModel) }

            sections.forEach { section ->
                item(key = "header-${section.title}") {
                    Column {
                        SectionHeader(section.title)
                        Text(
                            text = section.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = Spacing.lg)
                        )
                    }
                }
                items(section.rows, key = { it.spec.id }) { row ->
                    SetupModelCard(
                        row = row,
                        showKind = section.showsKind,
                        onInstall = { viewModel.install(row.spec.id) },
                        onCancel = { viewModel.cancelDownload(row.spec.id) }
                    )
                }
            }

            item {
                Text(
                    text = "You can add, swap or remove models any time from Models.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.lg)
                )
            }
        }
    }
}

@Composable
private fun DeviceCard(viewModel: SetupViewModel) {
    val profile = viewModel.profile
    LiquidGlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg)
    ) {
        Column(modifier = Modifier.padding(Spacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Your device",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                StatusBadge(text = profile.tier.label, tone = BadgeTone.ACCENT)
            }
            Spacer(Modifier.height(Spacing.sm))
            DeviceFact("Memory", formatBytes(profile.totalRamBytes))
            DeviceFact("Free storage", formatBytes(profile.freeStorageBytes))
            DeviceFact("CPU cores", profile.cpuCores.toString())
            DeviceFact("Architecture", profile.abi)
            DeviceFact("Android", "API ${profile.androidSdk}")
            profile.acceleration?.let { DeviceFact("Graphics", it) }
        }
    }
}

@Composable
private fun DeviceFact(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun SetupModelCard(
    row: SetupModelRow,
    showKind: Boolean,
    onInstall: () -> Unit,
    onCancel: () -> Unit
) {
    val spec = row.spec
    LiquidGlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg)
    ) {
        Column(modifier = Modifier.padding(Spacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = spec.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                if (row.bestFit) {
                    StatusBadge(text = "Best fit", tone = BadgeTone.ACCENT)
                }
            }
            if (showKind) {
                Spacer(Modifier.height(Spacing.xs))
                StatusBadge(text = spec.kind.label, tone = BadgeTone.NEUTRAL)
            }
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = spec.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(Spacing.sm))
            Text(
                text = "${spec.parameters} · ${spec.quantization} · ${formatBytes(spec.downloadBytes)}" +
                    if (spec.contextLength > 0) " · ${spec.contextLength / 1024}K context" else "",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Needs about ${formatBytes(spec.minRamBytes)} of RAM",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(Spacing.xs))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Speed ",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                RatingStars(spec.speedRating)
                Spacer(Modifier.padding(horizontal = 4.dp))
                Text(
                    text = "Quality ",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                RatingStars(spec.qualityRating)
            }

            when (row.fit) {
                ModelFit.TOO_HEAVY -> {
                    Spacer(Modifier.height(Spacing.sm))
                    Text(
                        text = "Needs more memory than this device has.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                ModelFit.NO_STORAGE -> {
                    Spacer(Modifier.height(Spacing.sm))
                    Text(
                        text = "Not enough free storage right now.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                ModelFit.TIGHT -> {
                    Spacer(Modifier.height(Spacing.sm))
                    Text(
                        text = "Will run, but it is close to this device's memory limit.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                ModelFit.GOOD -> Unit
            }

            Spacer(Modifier.height(Spacing.md))
            when (val status = row.status) {
                is ModelStatus.Downloading -> DownloadProgress(
                    progress = status.progress,
                    downloadedBytes = status.bytesDownloaded,
                    totalBytes = status.totalBytes,
                    onCancel = onCancel
                )
                is ModelStatus.Verifying -> Text(
                    text = "Verifying…",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                is ModelStatus.Installed, is ModelStatus.Ready -> StatusBadge(
                    text = "Installed",
                    tone = BadgeTone.SUCCESS
                )
                is ModelStatus.Loading -> Text(
                    text = "Loading…",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                is ModelStatus.Error -> Column {
                    Text(
                        text = status.message,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(Spacing.xs))
                    if (row.isUsable) {
                        PrimaryButton(
                            text = "Try again",
                            onClick = onInstall,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                is ModelStatus.NotInstalled -> if (row.isUsable) {
                    PrimaryButton(
                        text = "Install ${formatBytes(spec.downloadBytes)}",
                        onClick = onInstall,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Text(
                        text = "Not suitable for this device",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
