package com.goldmedal.aillm.ui.models

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.goldmedal.aillm.ai.modelmanager.ModelInfo

/**
 * Models tab. Category-first: Chat / Vision / Image Generation.
 * Each model shows install/load state and a download action.
 */
@Composable
fun ModelsScreen(
    viewModel: ModelsViewModel = hiltViewModel()
) {
    val chatModels by viewModel.chatModels.collectAsState()
    val visionModels by viewModel.visionModels.collectAsState()
    val imageGenModels by viewModel.imageGenModels.collectAsState()
    val progress by viewModel.downloadProgress.collectAsState()
    val error by viewModel.error.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "Models",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
            )
            Text(
                "Download only the models you want to use. Nothing is auto-downloaded.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        error?.let { msg ->
            item {
                Text(
                    msg,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        item {
            CategoryHeader("Chat")
        }
        items(chatModels.values.toList(), key = { it.name }) { model ->
            ModelCard(
                model = model,
                progress = progress[model.name],
                onDownload = { /* download chart models wired via engine */  },
                onLoad = { viewModel.loadModel(model.name) },
                onUnload = { viewModel.unloadModel(model.name) }
            )
        }

        item {
            CategoryHeader("Vision")
        }
        items(visionModels.values.toList(), key = { it.name }) { model ->
            ModelCard(
                model = model,
                progress = progress[model.name],
                onDownload = { },
                onLoad = { viewModel.loadModel(model.name) },
                onUnload = { viewModel.unloadModel(model.name) }
            )
        }

        item {
            CategoryHeader("Image Generation")
        }
        items(imageGenModels.values.toList(), key = { it.name }) { model ->
            ModelCard(
                model = model,
                progress = progress[model.name],
                onDownload = { },
                onLoad = { viewModel.loadModel(model.name) },
                onUnload = { viewModel.unloadModel(model.name) }
            )
        }

        item { Spacer(Modifier.height(32.dp)) }
    }
}

@Composable
private fun CategoryHeader(title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Memory,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(8.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
    HorizontalDivider()
}

@Composable
private fun ModelCard(
    model: ModelInfo,
    progress: Float?,
    onDownload: () -> Unit,
    onLoad: () -> Unit,
    onUnload: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    model.name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                StatusBadge(model)
            }

            Spacer(Modifier.height(6.dp))
            Text(
                model.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (progress != null) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (model.isInstalled) {
                Spacer(Modifier.height(10.dp))
                Row {
                    TextButton(onClick = if (model.isLoaded) onUnload else onLoad) {
                        Text(if (model.isLoaded) "Unload" else "Load")
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(model: ModelInfo) {
    val (label, color, icon) = when {
        model.isInstalled && model.isLoaded -> Triple(
            "Ready", MaterialTheme.colorScheme.primary, Icons.Default.CheckCircle
        )
        model.isInstalled -> Triple(
            "Installed", MaterialTheme.colorScheme.onSurfaceVariant, Icons.Default.CheckCircle
        )
        else -> Triple(
            "Not installed", MaterialTheme.colorScheme.error, Icons.Default.ErrorOutline
        )
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.height(16.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = color)
    }
}
