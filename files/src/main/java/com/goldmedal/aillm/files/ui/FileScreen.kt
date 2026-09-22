package com.goldmedal.aillm.files.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.goldmedal.aillm.core.database.FileMemoryEntity
import com.goldmedal.aillm.core.design.AillmTopBar
import com.goldmedal.aillm.core.design.EmptyState
import com.goldmedal.aillm.core.design.LiquidGlassSurface
import com.goldmedal.aillm.core.design.Spacing
import com.goldmedal.aillm.core.design.formatBytes
import com.goldmedal.aillm.files.viewmodel.FileViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun FileScreen(
    onBack: () -> Unit,
    viewModel: FileViewModel = hiltViewModel()
) {
    val files by viewModel.files.collectAsState()

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> uri?.let { viewModel.importFile(it) } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AillmTopBar(title = "Files", onBack = onBack) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    filePicker.launch(
                        arrayOf("text/*", "application/json", "application/xml", "application/pdf")
                    )
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Import file")
            }
        }
    ) { padding ->
        if (files.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                EmptyState(
                    icon = Icons.Default.InsertDriveFile,
                    title = "No files yet",
                    message = "Import a document and its text becomes available to your assistant, kept on this device."
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(
                    start = Spacing.lg,
                    end = Spacing.lg,
                    top = Spacing.sm,
                    bottom = 96.dp
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                items(files, key = { it.id }) { file ->
                    FileRow(file = file, onDelete = { viewModel.deleteFile(file.id) })
                }
            }
        }
    }
}

@Composable
private fun FileRow(
    file: FileMemoryEntity,
    onDelete: () -> Unit
) {
    val date = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(file.createdAt))
    LiquidGlassSurface(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(start = Spacing.lg, end = Spacing.xs, top = Spacing.md, bottom = Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.fileName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${file.fileType} · ${formatBytes(file.fileSize)} · $date",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (file.summary.isNotBlank()) {
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        text = file.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete file",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
