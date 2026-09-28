package com.goldmedal.aillm.ui.decision

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.goldmedal.aillm.BuildConfig
import com.goldmedal.aillm.ai.decision.DecisionOutcome
import com.goldmedal.aillm.ai.decision.DecisionResult
import com.goldmedal.aillm.ai.decision.DecisionVerdict
import com.goldmedal.aillm.ai.decision.LayaQuestionType
import com.goldmedal.aillm.ai.model.ModelSpec
import com.goldmedal.aillm.ai.model.ModelStatus
import com.goldmedal.aillm.core.design.AillmTopBar
import com.goldmedal.aillm.core.design.BadgeTone
import com.goldmedal.aillm.core.design.DownloadProgress
import com.goldmedal.aillm.core.design.EmptyState
import com.goldmedal.aillm.core.design.GlassPanel
import com.goldmedal.aillm.core.design.PrimaryButton
import com.goldmedal.aillm.core.design.Spacing
import com.goldmedal.aillm.core.design.StatusBadge
import com.goldmedal.aillm.core.design.formatBytes
import java.util.Locale

/**
 * The LAYA screen — the app's front door.
 *
 * Laya is a decision model, not a chat model: the screen is a form. State holds
 * one item per line (each line is judged independently — lines are never merged
 * into one state), Question holds the single question asked of all of them, and
 * Question Type selects the typed primitive. The results list shows Y / N / C
 * and the calibrated probability for each line, or an explicit failure that can
 * be retried.
 */
@Composable
fun DecisionScreen(
    onBack: (() -> Unit)? = null,
    viewModel: DecisionViewModel = hiltViewModel()
) {
    val spec = viewModel.spec
    val status by viewModel.status.collectAsState()
    val states by viewModel.states.collectAsState()
    val question by viewModel.question.collectAsState()
    val questionType by viewModel.questionType.collectAsState()
    val rows by viewModel.rows.collectAsState()
    val error by viewModel.error.collectAsState()
    val running by viewModel.running.collectAsState()
    val debugEnabled by viewModel.debugEnabled.collectAsState()

    val ready = status is ModelStatus.Ready
    val canJudge = ready && !running && questionType.isYesNo &&
        states.isNotBlank() && question.isNotBlank()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AillmTopBar(
                title = "LAYA",
                subtitle = "Decision Model",
                onBack = onBack,
                actions = {
                    if (BuildConfig.DEBUG) {
                        TextButton(onClick = { viewModel.toggleDebug() }) {
                            Text(if (debugEnabled) "Debug on" else "Debug")
                        }
                    }
                }
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

                // ---------------------------------------------------------- State
                FieldLabel("State", "1行に1つ。各行が独立した State として判定されます")
                OutlinedTextField(
                    value = states,
                    onValueChange = viewModel::onStatesChange,
                    placeholder = { Text("東京都\n埼玉県\nカリフォルニア州") },
                    minLines = 3,
                    maxLines = 8,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg)
                )

                Spacer(Modifier.height(Spacing.md))

                // ------------------------------------------------------- Question
                FieldLabel("Question", "1行で入力してください")
                OutlinedTextField(
                    value = question,
                    onValueChange = viewModel::onQuestionChange,
                    placeholder = { Text("日本のものか？") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg)
                )

                Spacer(Modifier.height(Spacing.md))

                // -------------------------------------------------- Question Type
                FieldLabel("Question Type", "Laya の typed question")
                QuestionTypeSelector(
                    selected = questionType,
                    ready = !running,
                    onSelect = viewModel::onQuestionTypeChange
                )
                if (!questionType.isYesNo) {
                    Text(
                        text = "このビルドでは Noul (Yes / No) のみ対応しています。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xs)
                    )
                }

                Spacer(Modifier.height(Spacing.lg))

                PrimaryButton(
                    text = if (running) "Checking…" else "判定する",
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
                    text = "Results",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = Spacing.lg)
                )
                Spacer(Modifier.height(Spacing.sm))
                rows.forEachIndexed { index, row ->
                    ResultRow(
                        row = row,
                        debugEnabled = debugEnabled,
                        onRetry = { viewModel.retry(index) }
                    )
                    Spacer(Modifier.height(Spacing.sm))
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

@Composable
private fun QuestionTypeSelector(
    selected: LayaQuestionType,
    ready: Boolean,
    onSelect: (LayaQuestionType) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.padding(horizontal = Spacing.lg)) {
        OutlinedButton(
            onClick = { if (ready) expanded = true },
            enabled = ready,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(selected.displayName)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            LayaQuestionType.values().forEach { type ->
                DropdownMenuItem(
                    text = { Text(type.displayName) },
                    onClick = {
                        onSelect(type)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun ModelStateCard(
    spec: ModelSpec,
    status: ModelStatus,
    onDownload: () -> Unit,
    onCancel: () -> Unit
) {
    GlassPanel(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg)
    ) {
        Column(modifier = Modifier.padding(Spacing.lg)) {
            when (status) {
                is ModelStatus.Ready -> Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusBadge("Laya Ready", BadgeTone.SUCCESS)
                    Spacer(Modifier.width(Spacing.sm))
                    Text(
                        text = "${spec.name} · ${spec.parameters}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                is ModelStatus.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusBadge("Loading Laya…", BadgeTone.ACCENT)
                    Text(
                        text = "端末内でONNXモデルをロードしています",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                is ModelStatus.Downloading -> Column {
                    Text(
                        text = "Downloading Laya…",
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
                        text = "Laya is not installed",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        text = "Laya の ONNX グラフ・トークナイザ・較正設定をダウンロードします。" +
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
 * One judgment. Waiting / Checking while the batch runs, then either the
 * verdict and its calibrated probability, or an explicit failure with Retry.
 */
@Composable
private fun ResultRow(
    row: DecisionViewModel.Row,
    debugEnabled: Boolean,
    onRetry: () -> Unit
) {
    GlassPanel(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg)
    ) {
        Column(modifier = Modifier.padding(Spacing.lg)) {
            Text(
                text = row.subject.ifBlank { "(empty)" },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(Spacing.xs))

            when (row.state) {
                DecisionViewModel.RowState.WAITING -> RowHint("Waiting…")
                DecisionViewModel.RowState.CHECKING -> RowHint("Checking…")
                DecisionViewModel.RowState.DONE -> {
                    val answered = row.outcome as? DecisionOutcome.Answered
                    if (answered != null) VerdictView(answered.result)
                }
                DecisionViewModel.RowState.FAILED -> FailedView(row, onRetry)
            }

            if (debugEnabled) {
                val answered = row.outcome as? DecisionOutcome.Answered
                if (answered != null) {
                    Spacer(Modifier.height(Spacing.sm))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(Spacing.sm))
                    DebugView(answered.result)
                } else {
                    val failed = row.outcome as? DecisionOutcome.Failed
                    if (failed != null) {
                        Spacer(Modifier.height(Spacing.sm))
                        Text(
                            text = "error: ${failed.failure.kind.name}" +
                                (failed.failure.detail?.let { " · $it" } ?: ""),
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RowHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun VerdictView(result: DecisionResult) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = result.verdict.name,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            color = when (result.verdict) {
                DecisionVerdict.Y -> MaterialTheme.colorScheme.primary
                DecisionVerdict.N -> MaterialTheme.colorScheme.error
                DecisionVerdict.C -> MaterialTheme.colorScheme.tertiary
            }
        )
        Spacer(Modifier.width(Spacing.md))
        Column {
            Text(
                text = String.format(Locale.US, "%.1f%%", result.probability * 100f),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = when (result.verdict) {
                    DecisionVerdict.Y -> "P(true) ≥ 51%"
                    DecisionVerdict.N -> "P(true) ≤ 49%"
                    DecisionVerdict.C -> "Not clear (49–51%)"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun FailedView(row: DecisionViewModel.Row, onRetry: () -> Unit) {
    val failed = row.outcome as? DecisionOutcome.Failed
    Column {
        Text(
            text = failed?.failure?.kind?.displayMessage ?: "判定できませんでした",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(Modifier.height(Spacing.xs))
        TextButton(onClick = onRetry) { Text("Retry") }
    }
}

/** The debug view: proof that one judgment followed Laya's real pipeline. */
@Composable
private fun DebugView(result: DecisionResult) {
    val debug = result.debug
    val lines = listOf(
        "Question Type: ${debug.questionType.key}",
        "qtype: ${debug.qtype}",
        "State: ${debug.subject}",
        "Question: ${debug.question}",
        "marker_pos: ${debug.markerPos}",
        "raw logits: ${debug.rawLogits}",
        "temperature: ${debug.temperature} (config ${debug.temperatureConfig})",
        "P(false): ${debug.probabilities.getOrNull(0)}",
        "P(true): ${debug.probabilities.getOrNull(1)}",
        "final: ${result.verdict.name}"
    )
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        lines.forEach { line ->
            Text(
                text = line,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
