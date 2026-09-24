package com.goldmedal.aillm.ui.decision

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.goldmedal.aillm.ai.decision.DecisionResult
import com.goldmedal.aillm.ai.decision.DecisionSource
import com.goldmedal.aillm.ai.decision.DecisionVerdict
import com.goldmedal.aillm.ai.model.ModelSpec
import com.goldmedal.aillm.ai.model.ModelStatus
import com.goldmedal.aillm.core.design.AillmTopBar
import com.goldmedal.aillm.core.design.BadgeTone
import com.goldmedal.aillm.core.design.DownloadProgress
import com.goldmedal.aillm.core.design.EmptyState
import com.goldmedal.aillm.core.design.LiquidGlassSurface
import com.goldmedal.aillm.core.design.PrimaryButton
import com.goldmedal.aillm.core.design.Spacing
import com.goldmedal.aillm.core.design.StatusBadge
import com.goldmedal.aillm.core.design.formatBytes
import java.util.Locale

/**
 * The VON screen — the app's main screen. Von is not a chat model: it judges
 * "does A hold of B?" for every A line, one forward pass each, and answers
 * with a probability that becomes Y, N or C. A form, never a conversation.
 */
@Composable
fun DecisionScreen(
    onBack: (() -> Unit)? = null,
    viewModel: DecisionViewModel = hiltViewModel()
) {
    val spec = viewModel.spec
    val status by viewModel.status.collectAsState()
    val subjects by viewModel.subjects.collectAsState()
    val context by viewModel.context.collectAsState()
    val rows by viewModel.rows.collectAsState()
    val error by viewModel.error.collectAsState()
    val running by viewModel.running.collectAsState()

    val ready = status is ModelStatus.Ready
    val canJudge = ready && !running &&
        subjects.isNotBlank() && context.isNotBlank()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AillmTopBar(
                title = "VON",
                subtitle = "AはBか？",
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
                EmptyState(
                    icon = Icons.Default.ThumbUp,
                    title = "No decision model configured",
                    message = "This build has no decision model in its library."
                )
                return@Column
            }

            ModelStateCard(
                spec = spec,
                status = status,
                onDownload = { viewModel.download() },
                onCancel = { viewModel.cancelDownload() }
            )

            if (ready) {
                Spacer(Modifier.height(Spacing.md))

                // ---------------------------------------------------- A
                FieldLabel("A", supporting = "1行に1つ入力してください")
                OutlinedTextField(
                    value = subjects,
                    onValueChange = viewModel::onSubjectsChange,
                    placeholder = { Text("東京都\n埼玉県\nカリフォルニア州") },
                    minLines = 4,
                    maxLines = 8,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg)
                )

                Spacer(Modifier.height(Spacing.md))

                // ---------------------------------------------------- B
                FieldLabel("B", supporting = "1行で入力してください")
                OutlinedTextField(
                    value = context,
                    onValueChange = viewModel::onContextChange,
                    placeholder = { Text("日本のもの") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg)
                )

                Spacer(Modifier.height(Spacing.lg))

                PrimaryButton(
                    text = if (running) "判定中…" else "判定",
                    onClick = { viewModel.judge() },
                    enabled = canJudge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg)
                        .height(52.dp)
                )
            }

            if (rows.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.lg))
                Text(
                    text = "結果",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = Spacing.lg)
                )
                Spacer(Modifier.height(Spacing.sm))
                rows.forEachIndexed { index, row ->
                    ResultRow(
                        index = index,
                        row = row
                    )
                    Spacer(Modifier.height(Spacing.sm))
                }
                if (!running && rows.all { it.state == DecisionViewModel.RowState.DONE }) {
                    FlowNote()
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

            Spacer(Modifier.height(Spacing.xxl))
        }
    }
}

@Composable
private fun FieldLabel(label: String, supporting: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.xs),
        verticalAlignment = Alignment.Bottom
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.width(Spacing.sm))
        Text(
            text = supporting,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 1.dp)
        )
    }
}

/**
 * The model's state, compressed to what the flow needs: Von must say Ready
 * before 判定 does anything, and a failure is explained in words.
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
                    StatusBadge("Von Ready", BadgeTone.SUCCESS)
                    Spacer(Modifier.width(Spacing.sm))
                    Text(
                        text = "${spec.name} · ${spec.parameters}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                is ModelStatus.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusBadge("Loading Von…", BadgeTone.ACCENT)
                    Text(
                        text = "端末内でONNXモデルをロードしています",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                is ModelStatus.Downloading -> Column {
                    Text(
                        text = "Downloading Von…",
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
                        text = "次回の起動から自動的にロードされます。",
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
                    Text(
                        text = "Models から再ダウンロードできます。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                is ModelStatus.NotInstalled -> Column {
                    Text(
                        text = "Von is not installed",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        text = "huggingface.co/wfzyx/von から直接ダウンロードします。" +
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

/**
 * One judgment. While the batch runs each row shows where it is — Waiting,
 * Checking — and then settles on its verdict and probability.
 */
@Composable
private fun ResultRow(
    index: Int,
    row: DecisionViewModel.Row
) {
    LiquidGlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = row.subject,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2
                )
                val result = row.result
                when (row.state) {
                    DecisionViewModel.RowState.WAITING -> RowHint("Waiting…")
                    DecisionViewModel.RowState.CHECKING -> RowHint("Checking…")
                    DecisionViewModel.RowState.DONE -> if (result != null) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "→ ${result.context} か？",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                    DecisionViewModel.RowState.FAILED -> RowHint(
                        row.failure?.message ?: "判定できませんでした"
                    )
                }
            }

            val result = row.result
            when {
                row.state == DecisionViewModel.RowState.DONE && result != null ->
                    VerdictView(result)
                row.state == DecisionViewModel.RowState.FAILED ->
                    StatusBadge("Error", BadgeTone.ERROR)
                row.state == DecisionViewModel.RowState.CHECKING ->
                    StatusBadge("Checking", BadgeTone.ACCENT)
                else ->
                    StatusBadge("Waiting", BadgeTone.NEUTRAL)
            }
        }
    }
}

@Composable
private fun RowHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun VerdictView(result: DecisionResult) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = result.verdict.name,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = when (result.verdict) {
                DecisionVerdict.Y -> MaterialTheme.colorScheme.primary
                DecisionVerdict.N -> MaterialTheme.colorScheme.error
                DecisionVerdict.C -> MaterialTheme.colorScheme.tertiary
            }
        )
        Spacer(Modifier.width(Spacing.sm))
        Column {
            Text(
                text = when (result.verdict) {
                    DecisionVerdict.Y -> "Yes"
                    DecisionVerdict.N -> "No"
                    DecisionVerdict.C -> "Not Clear"
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "%.1f%%".format(Locale.US, result.probability * 100f),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(Spacing.sm))
        if (result.source == DecisionSource.HEURISTIC) {
            StatusBadge("Fallback", BadgeTone.WARNING)
        }
    }
}

/**
 * The pipeline the finished list represents, spelled out once:
 * A ↓ Von ↓ Bとの成立確率 ↓ Y/N/C.
 */
@Composable
private fun FlowNote() {
    LiquidGlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg)
    ) {
        Text(
            text = "A ↓ Von ↓ Bとの成立確率 ↓ Y / N / C",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md)
        )
    }
}
