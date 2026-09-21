package com.goldmedal.aillm.memory.ui

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.goldmedal.aillm.core.database.UserMemoryEntity
import com.goldmedal.aillm.core.design.AillmTopBar
import com.goldmedal.aillm.core.design.EmptyState
import com.goldmedal.aillm.core.design.Spacing
import com.goldmedal.aillm.core.design.StatusBadge
import com.goldmedal.aillm.core.design.BadgeTone
import com.goldmedal.aillm.memory.viewmodel.MemoryViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
fun MemoryScreen(
    onBack: () -> Unit,
    viewModel: MemoryViewModel = hiltViewModel()
) {
    val memories by viewModel.memories.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val showArchived by viewModel.isShowingArchived.collectAsState()
    val notice by viewModel.notice.collectAsState()
    var query by remember { mutableStateOf("") }

    LaunchedEffect(notice) {
        if (notice != null) {
            delay(4_000)
            viewModel.clearNotice()
        }
    }
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<UserMemoryEntity?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AillmTopBar(
                title = "Memory",
                onBack = onBack,
                actions = {
                    // Tidying is a merge, never a delete: anything it moves
                    // aside lands in Archived and can be brought back.
                    TextButton(onClick = { viewModel.tidyUp() }) {
                        Text("Tidy up", style = MaterialTheme.typography.labelLarge)
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAdd = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add memory")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    viewModel.setQuery(it)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg),
                placeholder = { Text("Search what I remember") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = MaterialTheme.shapes.medium
            )

            if (categories.isNotEmpty() || showArchived) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = Spacing.sm),
                    contentPadding = PaddingValues(horizontal = Spacing.lg),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    if (!showArchived) {
                        item {
                            FilterChip(
                                selected = selectedCategory == null,
                                onClick = { viewModel.setCategory(null) },
                                label = { Text("All") }
                            )
                        }
                        items(categories) { category ->
                            FilterChip(
                                selected = selectedCategory == category,
                                onClick = {
                                    viewModel.setCategory(
                                        if (selectedCategory == category) null else category
                                    )
                                },
                                label = { Text(category.lowercase().replaceFirstChar { it.uppercase() }) }
                            )
                        }
                    }
                    item {
                        FilterChip(
                            selected = showArchived,
                            onClick = { viewModel.setShowArchived(!showArchived) },
                            label = { Text("Archived") }
                        )
                    }
                }
            }

            notice?.let { message ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg, vertical = Spacing.xs)
                ) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(Spacing.md)
                    )
                }
            }

            if (memories.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        icon = Icons.Default.Search,
                        title = if (showArchived) "Nothing archived" else "Nothing remembered yet",
                        message = if (showArchived) {
                            "Older and duplicate memories land here when they are tidied up. Nothing is ever deleted without you asking for it."
                        } else {
                            "As you talk with your assistant, the important things — your preferences, projects and environment — appear here. You can edit or forget any of them."
                        }
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = Spacing.lg,
                        end = Spacing.lg,
                        top = Spacing.xs,
                        bottom = 96.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    items(memories, key = { it.id }) { memory ->
                        MemoryCard(
                            memory = memory,
                            archived = showArchived,
                            onEdit = { editing = memory },
                            onRestore = { viewModel.restoreMemory(memory.id) },
                            onDelete = { viewModel.deleteMemory(memory.id) }
                        )
                    }
                }
            }
        }
    }

    if (showAdd) {
        MemoryEditorDialog(
            title = "Add memory",
            initial = null,
            onDismiss = { showAdd = false },
            onSave = { category, key, value ->
                viewModel.addMemory(category, key, value)
                showAdd = false
            }
        )
    }

    editing?.let { memory ->
        MemoryEditorDialog(
            title = "Edit memory",
            initial = memory,
            onDismiss = { editing = null },
            onSave = { category, key, value ->
                viewModel.updateMemory(memory.copy(category = category, key = key, value = value))
                editing = null
            }
        )
    }
}

/**
 * Shared with the in-chat memory sheet, so what the assistant remembers looks
 * and behaves the same wherever it is opened.
 */
@Composable
fun MemoryCard(
    memory: UserMemoryEntity,
    archived: Boolean,
    onEdit: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit
) {
    val date = remember(memory.updatedAt) {
        SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(memory.updatedAt))
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(Spacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusBadge(text = memory.category.lowercase(), tone = BadgeTone.ACCENT)
                Spacer(Modifier.weight(1f))
                if (archived) {
                    IconButton(onClick = onRestore) {
                        Icon(Icons.Default.Restore, contentDescription = "Restore")
                    }
                } else {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                }
            }
            Text(
                text = memory.key,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = memory.value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(Spacing.sm))
            Text(
                text = if (archived) "Archived · updated $date" else "Updated $date",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun MemoryEditorDialog(
    title: String,
    initial: UserMemoryEntity?,
    onDismiss: () -> Unit,
    onSave: (category: String, key: String, value: String) -> Unit
) {
    var category by remember { mutableStateOf(initial?.category ?: "PROFILE") }
    var key by remember { mutableStateOf(initial?.key ?: "") }
    var value by remember { mutableStateOf(initial?.value ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Category") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text("Key") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text("Value") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(category.trim(), key.trim(), value.trim()) },
                enabled = category.isNotBlank() && key.isNotBlank() && value.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
