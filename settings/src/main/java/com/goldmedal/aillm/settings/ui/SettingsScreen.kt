package com.goldmedal.aillm.settings.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.goldmedal.aillm.ai.modelmanager.ModelInfo
import com.goldmedal.aillm.settings.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val models by viewModel.models.collectAsState()
    val isWebSearchEnabled by viewModel.isWebSearchEnabled.collectAsState()
    val braveApiKey by viewModel.braveApiKey.collectAsState()
    val tavilyApiKey by viewModel.tavilyApiKey.collectAsState()
    var showDeleteAllDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            item {
                Text("Model Management", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(8.dp))
            }
            items(models) { model ->
                ModelCard(model = model, onLoad = { viewModel.loadModel(model.name) }, onUnload = { viewModel.unloadModel(model.name) })
            }
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text("Web Search", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(8.dp))
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Enable Web Search", style = MaterialTheme.typography.bodyLarge)
                            Text("Allow AI to search the web for current information", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = isWebSearchEnabled, onCheckedChange = { viewModel.setWebSearchEnabled(it) })
                    }
                }
                if (isWebSearchEnabled) {
                    OutlinedTextField(value = braveApiKey, onValueChange = { viewModel.setBraveApiKey(it) }, label = { Text("Brave Search API Key") }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
                    OutlinedTextField(value = tavilyApiKey, onValueChange = { viewModel.setTavilyApiKey(it) }, label = { Text("Tavily Search API Key") }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
                }
            }
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text("Memory Management", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { showDeleteAllDialog = true },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete All", tint = MaterialTheme.colorScheme.error)
                        Column(modifier = Modifier.padding(start = 16.dp)) {
                            Text("Delete All Memories", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onErrorContainer)
                            Text("Remove all stored memories and user data", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
            }
        }
    }

    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            title = { Text("Delete All Memories") },
            text = { Text("This will permanently delete all stored memories and user data. This action cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteAllMemories(); showDeleteAllDialog = false }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllDialog = false }) { Text("Cancel") }
            },
            icon = { Icon(Icons.Default.Warning, contentDescription = "Warning", tint = MaterialTheme.colorScheme.error) }
        )
    }
}

@Composable
fun ModelCard(model: ModelInfo, onLoad: () -> Unit, onUnload: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(model.name, style = MaterialTheme.typography.bodyLarge)
                    Text(model.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (model.isLoaded) {
                    TextButton(onClick = onUnload) { Text("Unload") }
                } else {
                    TextButton(onClick = onLoad) { Text(if (model.isInstalled) "Load" else "Download") }
                }
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                Text("Size: ${formatSize(model.sizeBytes)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.weight(1f))
                Text("RAM: ${formatSize(model.estimatedRamUsageBytes)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun formatSize(bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format("%.1f GB", gb)
        mb >= 1.0 -> String.format("%.1f MB", mb)
        kb >= 1.0 -> String.format("%.1f KB", kb)
        else -> "$bytes B"
    }
}
