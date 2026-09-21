package com.goldmedal.aillm.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.goldmedal.aillm.ai.modelmanager.ModelCategory
import com.goldmedal.aillm.ai.modelmanager.ModelDownloader
import com.goldmedal.aillm.ai.modelmanager.RecommendedModel
import com.goldmedal.aillm.ai.modelmanager.ModelRecommender
import kotlinx.coroutines.launch

/**
 * First-run onboarding — the "which AI for THIS phone?" screen.
 *
 * Reads the actual device (RAM / free storage / CPU cores) on this Android
 * and surfaces EXACTLY 3 recommended models (one per category: Chat, Coding,
 * Vision — Gemma + Qwen families, all packaged as GGUF on Hugging Face).
 * The user picks a category, then one-click-installs the model **inside the
 * app** via DownloadManager. No browser is ever opened; everything completes
 * in-app and stays private.
 */
@Composable
fun SetupScreen(
    onComplete: () -> Unit = {},
    recommender: ModelRecommender? = null,
    downloader: ModelDownloader? = null
) {
    var selectedCategory by rememberSaveable { mutableStateOf(ModelCategory.CHAT) }
    var downloadingName by rememberSaveable { mutableStateOf<String?>(null) }
    var progress by rememberSaveable { mutableStateOf<Float?>(null) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val recommendations = remember(recommender) {
        recommender?.recommendForThisDevice() ?: emptyList()
    }
    val visible = when (selectedCategory) {
        ModelCategory.CHAT -> recommendations.filter { it.category == ModelCategory.CHAT }
        ModelCategory.CODING -> recommendations.filter { it.category == ModelCategory.CODING }
        ModelCategory.VISION -> recommendations.filter { it.category == ModelCategory.VISION }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "Best models for THIS phone",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(4.dp))
        val bundle = recommendations.firstOrNull()
        Text(
            if (bundle != null) {
                "Picked from your actual RAM · free storage · CPU. "
                    + "Small, private, and it lives on your device."
            } else {
                "Your phone, your AI. Everything runs locally — nothing leaves the device."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ModelCategory.entries.forEach { cat ->
                FilterChip(
                    selected = selectedCategory == cat,
                    onClick = { selectedCategory = cat },
                    label = { Text(cat.label) }
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        if (visible.isEmpty()) {
            Text(
                "No ${selectedCategory.label} model in this tier yet — pick another category.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            visible.take(1).forEach { model ->
                ModelDownloadCard(
                    model = model,
                    progress = if (downloadingName == model.fileName) progress else null,
                    onDownload = {
                        error = null
                        val d = downloader
                        if (d != null) {
                            scope.launch {
                                downloadingName = model.fileName
                                progress = 0f
                                d.downloadModel(model, onProgress = { progress = it })
                                    .onSuccess {
                                        downloadingName = null
                                        progress = null
                                    }
                                    .onFailure { e ->
                                        downloadingName = null
                                        progress = null
                                        error = e.message ?: "Download failed"
                                    }
                            }
                        } else {
                            error = "Model manager not ready — retry on the Models tab."
                        }
                    }
                )
            }
        }

        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onComplete,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text("Get started")
        }
    }
}

@Composable
private fun ModelDownloadCard(
    model: RecommendedModel,
    progress: Float?,
    onDownload: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(model.name, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(2.dp))
            Text(
                "${model.description} · ~${model.sizeBytes / 1_000_000_000L}GB",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(if (model.fileName.endsWith(".gguf")) "GGUF · runs fully offline" else "GGUF · runs fully offline",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            if (progress != null) {
                Column {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                Button(
                    onClick = onDownload,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Install now — in-app, one tap")
                }
            }
        }
    }
}

private val ModelCategory.label: String
    get() = when (this) {
        ModelCategory.CHAT -> "Chat"
        ModelCategory.CODING -> "Coding"
        ModelCategory.VISION -> "Vision"
    }
