package com.goldmedal.aillm.ui.models

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.goldmedal.aillm.ai.model.ModelFit
import com.goldmedal.aillm.ai.model.ModelKind
import com.goldmedal.aillm.ai.model.ModelStatus
import com.goldmedal.aillm.ai.model.isInstalled
import com.goldmedal.aillm.core.design.AillmTopBar
import com.goldmedal.aillm.core.design.BadgeTone
import com.goldmedal.aillm.core.design.DownloadProgress
import com.goldmedal.aillm.core.design.EmptyState
import com.goldmedal.aillm.core.design.LiquidGlassSurface
import com.goldmedal.aillm.core.design.PrimaryButton
import com.goldmedal.aillm.core.design.RatingStars
import com.goldmedal.aillm.core.design.SectionHeader
import com.goldmedal.aillm.core.design.Spacing
import com.goldmedal.aillm.core.design.StatusBadge
import com.goldmedal.aillm.core.design.formatBytes

@Composable
fun ModelsScreen(
    onOpenImages: () -> Unit = {},
    viewModel: ModelsViewModel = hiltViewModel()
) {
    val kind by viewModel.kind.collectAsState()
    val rows by viewModel.rows.collectAsState()

    val installed = rows.filter { it.status.isInstalled }
    val available = rows.filterNot { it.status.isInstalled }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AillmTopBar(
                title = "Models",
                subtitle = "Download only the models you want"
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = Spacing.lg),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                items(ModelKind.values().toList()) { item ->
                    FilterChip(
                        selected = kind == item,
                        onClick = { viewModel.selectKind(item) },
                        label = { Text(item.label) }
                    )
                }
            }

            if (rows.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        icon = Icons.Default.Memory,
                        title = "Nothing here yet",
                        message = "Models for this category will appear here as the library grows."
                    )
                }
                return@Column
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = Spacing.xxl),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                if (installed.isNotEmpty()) {
                    item { SectionHeader("Installed") }
                    items(installed, key = { it.spec.id }) { row ->
                        ModelCard(
                            row = row,
                            kind = kind,
                            onDownload = { viewModel.download(row.spec.id) },
                            onCancel = { viewModel.cancelDownload(row.spec.id) },
                            onDelete = { viewModel.delete(row.spec.id) },
                            onLoad = { viewModel.load(row.spec.id) },
                            onUnload = { viewModel.unload() },
                            onOpenImages = onOpenImages
                        )
                    }
                }
                if (available.isNotEmpty()) {
                    item { SectionHeader("Available") }
                    items(available, key = { it.spec.id }) { row ->
                        ModelCard(
                            row = row,
                            kind = kind,
                            onDownload = { viewModel.download(row.spec.id) },
                            onCancel = { viewModel.cancelDownload(row.spec.id) },
                            onDelete = { viewModel.delete(row.spec.id) },
                            onLoad = { viewModel.load(row.spec.id) },
                            onUnload = { viewModel.unload() },
                            onOpenImages = onOpenImages
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelCard(
    row: ModelRow,
    kind: ModelKind,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onLoad: () -> Unit,
    onUnload: () -> Unit,
    onOpenImages: () -> Unit
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
                if (row.recommended && !row.status.isInstalled) {
                    StatusBadge("Recommended", BadgeTone.ACCENT)
                    Spacer(Modifier.width(Spacing.xs))
                }
                StatusBadgeFor(row.status)
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = spec.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(Spacing.sm))
            Text(
                text = "${spec.family} · ${spec.parameters} · ${spec.quantization} · ${formatBytes(spec.downloadBytes)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Needs ~${formatBytes(spec.minRamBytes)} RAM" +
                    if (spec.contextLength > 0) " · ${spec.contextLength / 1024}K context" else "",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(2.dp))
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
                        text = "Runs, but close to this device's memory limit.",
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
                is ModelStatus.Loading -> Text(
                    text = "Loading…",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                is ModelStatus.Ready -> Row {
                    TextButton(onClick = onUnload) { Text("Unload") }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDelete) { Text("Delete") }
                }
                is ModelStatus.Installed -> if (spec.kind == ModelKind.IMAGE_GENERATION) {
                    // The image engine is driven from its own screen, where the
                    // model is loaded lazily on the first generation.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PrimaryButton(
                            text = "Generate",
                            onClick = onOpenImages,
                            modifier = Modifier.height(40.dp)
                        )
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = onDelete) { Text("Delete") }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PrimaryButton(
                            text = "Load",
                            onClick = onLoad,
                            modifier = Modifier.height(40.dp)
                        )
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = onDelete) { Text("Delete") }
                    }
                }
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
                            onClick = onDownload,
                            modifier = Modifier.height(40.dp)
                        )
                    }
                }
                is ModelStatus.NotInstalled -> if (row.isUsable) {
                    PrimaryButton(
                        text = "Download ${formatBytes(spec.downloadBytes)}",
                        onClick = onDownload,
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

@Composable
private fun StatusBadgeFor(status: ModelStatus) {
    when (status) {
        is ModelStatus.Ready -> StatusBadge("Ready", BadgeTone.SUCCESS)
        is ModelStatus.Installed -> StatusBadge("Installed", BadgeTone.NEUTRAL)
        is ModelStatus.Downloading -> StatusBadge("Downloading", BadgeTone.ACCENT)
        is ModelStatus.Verifying -> StatusBadge("Verifying", BadgeTone.ACCENT)
        is ModelStatus.Loading -> StatusBadge("Loading", BadgeTone.ACCENT)
        is ModelStatus.Error -> StatusBadge("Error", BadgeTone.ERROR)
        is ModelStatus.NotInstalled -> StatusBadge("Not installed", BadgeTone.NEUTRAL)
    }
}
