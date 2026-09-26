package com.goldmedal.aillm.ui.image

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.goldmedal.aillm.ai.model.ModelSpec
import com.goldmedal.aillm.ai.model.ModelStatus
import com.goldmedal.aillm.core.design.AillmTopBar
import com.goldmedal.aillm.core.design.BadgeTone
import com.goldmedal.aillm.core.design.DownloadProgress
import com.goldmedal.aillm.core.design.LiquidGlassSurface
import com.goldmedal.aillm.core.design.PrimaryButton
import com.goldmedal.aillm.core.design.Spacing
import com.goldmedal.aillm.core.design.StatusBadge
import com.goldmedal.aillm.core.design.formatBytes

/**
 * The image screen. One text-to-image model (Lykon's Absolute Reality 1.81),
 * run entirely on-device through stable-diffusion.cpp: a prompt in, a picture
 * out, and nothing leaves the phone.
 */
@Composable
fun ImageScreen(
    onBack: (() -> Unit)? = null,
    viewModel: ImageViewModel = hiltViewModel()
) {
    val spec = viewModel.spec
    val status by viewModel.status.collectAsState()
    val prompt by viewModel.prompt.collectAsState()
    val negative by viewModel.negative.collectAsState()
    val size by viewModel.size.collectAsState()
    val steps by viewModel.steps.collectAsState()
    val generating by viewModel.generating.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val bitmap by viewModel.bitmap.collectAsState()
    val error by viewModel.error.collectAsState()

    val loadable = status is ModelStatus.Installed || status is ModelStatus.Ready
    val canGenerate = loadable && !generating && prompt.isNotBlank()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AillmTopBar(
                title = "Images",
                subtitle = "端末内で画像を生成",
                onBack = onBack
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
        ) {
            if (spec == null) {
                Text(
                    text = "This build has no image model in its library.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(Spacing.lg)
                )
                return@Column
            }

            ModelStateCard(
                spec = spec,
                status = status,
                onDownload = { viewModel.download() },
                onCancel = { viewModel.cancelDownload() }
            )

            if (loadable) {
                Spacer(Modifier.height(Spacing.md))
                OutlinedTextField(
                    value = prompt,
                    onValueChange = viewModel::onPromptChange,
                    label = { Text("Prompt") },
                    placeholder = { Text("a portrait of a woman, natural light, 85mm") },
                    minLines = 3,
                    maxLines = 6,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg)
                )

                Spacer(Modifier.height(Spacing.sm))
                OutlinedTextField(
                    value = negative,
                    onValueChange = viewModel::onNegativeChange,
                    label = { Text("Negative prompt") },
                    placeholder = { Text("低品質, ぼやけた, watermark") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg)
                )

                OptionRow(
                    label = "Size",
                    options = SIZE_OPTIONS,
                    selected = size,
                    format = { "$it px" },
                    onSelect = viewModel::selectSize
                )
                OptionRow(
                    label = "Steps",
                    options = STEP_OPTIONS,
                    selected = steps,
                    format = { it.toString() },
                    onSelect = viewModel::selectSteps
                )

                Spacer(Modifier.height(Spacing.md))
                PrimaryButton(
                    text = if (generating) "生成中…" else "Generate",
                    onClick = { viewModel.generate() },
                    enabled = canGenerate,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg)
                        .height(52.dp)
                )

                if (generating) {
                    Spacer(Modifier.height(Spacing.md))
                    Text(
                        text = "Sampling… ${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Spacing.lg)
                    )
                    Spacer(Modifier.height(Spacing.xs))
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.lg)
                            .clip(MaterialTheme.shapes.small)
                    )
                }
            }

            error?.let { message ->
                Spacer(Modifier.height(Spacing.md))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = Spacing.lg)
                )
            }

            bitmap?.let { image ->
                Spacer(Modifier.height(Spacing.lg))
                LiquidGlassSurface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg)
                ) {
                    Image(
                        bitmap = image.asImageBitmap(),
                        contentDescription = "Generated image",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .padding(Spacing.sm)
                            .clip(MaterialTheme.shapes.medium)
                    )
                }
            }

            Spacer(Modifier.height(Spacing.xxl))
        }
    }
}

@Composable
private fun OptionRow(
    label: String,
    options: List<Int>,
    selected: Int,
    format: (Int) -> String,
    onSelect: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(format(option)) }
            )
        }
    }
}

/**
 * The model's state, compressed to what the flow needs: the weights must be on
 * disk before anything can be generated, and a failure is explained in words.
 */
@Composable
private fun ModelStateCard(
    spec: ModelSpec,
    status: ModelStatus,
    onDownload: () -> Unit,
    onCancel: () -> Unit
) {
    LiquidGlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg)
    ) {
        Column(modifier = Modifier.padding(Spacing.lg)) {
            when (status) {
                is ModelStatus.Ready -> Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusBadge("Ready", BadgeTone.SUCCESS)
                    Spacer(Modifier.width(Spacing.sm))
                    Text(
                        text = "${spec.name} · ${spec.parameters}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                is ModelStatus.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusBadge("Loading…", BadgeTone.ACCENT)
                    Text(
                        text = "端末内でモデルをロードしています",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                is ModelStatus.Downloading -> Column {
                    Text(
                        text = "Downloading ${spec.name}…",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(Spacing.xs))
                    DownloadProgress(
                        progress = status.progress,
                        downloadedBytes = status.bytesDownloaded,
                        totalBytes = status.totalBytes,
                        onCancel = onCancel
                    )
                }
                is ModelStatus.Verifying -> StatusBadge("Verifying…", BadgeTone.ACCENT)
                is ModelStatus.Installed -> Column {
                    StatusBadge("Installed", BadgeTone.NEUTRAL)
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        text = "最初の生成時に読み込まれます。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                is ModelStatus.Error -> Column {
                    StatusBadge("Error", BadgeTone.ERROR)
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        text = status.message,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(Spacing.xs))
                    PrimaryButton(
                        text = "Try again",
                        onClick = onDownload,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                is ModelStatus.NotInstalled -> Column {
                    Text(
                        text = "${spec.name} is not installed",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        text = "huggingface.co/Lykon/AbsoluteReality から直接ダウンロードします。" +
                            "推論はすべて端末内で行われ、データは外部に送信されません。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    PrimaryButton(
                        text = "Download ${formatBytes(spec.downloadBytes)}",
                        onClick = onDownload,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

private val SIZE_OPTIONS = listOf(384, 512, 640)
private val STEP_OPTIONS = listOf(15, 25, 35)
