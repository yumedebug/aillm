package com.goldmedal.aillm.chat.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.goldmedal.aillm.core.database.UserMemoryEntity
import com.goldmedal.aillm.core.design.EmptyState
import com.goldmedal.aillm.core.design.Spacing
import com.goldmedal.aillm.memory.ui.MemoryCard
import com.goldmedal.aillm.memory.ui.MemoryEditorDialog
import com.goldmedal.aillm.memory.viewmodel.MemoryViewModel

/**
 * What the assistant remembers, opened from the conversation itself.
 *
 * The point is not having to leave the chat to fix what it believes about you:
 * the list can be searched, a memory can be corrected in place, and one can be
 * forgotten outright. Edits are picked up by the very next reply, because
 * memories are read fresh for every turn.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemorySheet(
    onDismiss: () -> Unit,
    onManageAll: () -> Unit,
    viewModel: MemoryViewModel = hiltViewModel()
) {
    val memories by viewModel.memories.collectAsState()
    val showArchived by viewModel.isShowingArchived.collectAsState()
    var query by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<UserMemoryEntity?>(null) }

    // The view model outlives the sheet, so a previous search must not leak in.
    LaunchedEffect(Unit) { viewModel.setQuery("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = Spacing.lg, end = Spacing.md)
            ) {
                Text(
                    text = "What I remember",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onManageAll) { Text("Manage all") }
            }

            Text(
                text = "Used in this conversation. Edit one or forget it — the next reply already sees the change.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.lg)
            )

            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    viewModel.setQuery(it)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                placeholder = { Text("Search memory") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = MaterialTheme.shapes.medium
            )

            if (memories.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyState(
                        icon = Icons.Default.Search,
                        title = "Nothing remembered",
                        message = "Facts your assistant picks up about you appear here, and you can always correct them."
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    contentPadding = PaddingValues(
                        start = Spacing.lg,
                        end = Spacing.lg,
                        bottom = Spacing.lg
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

            Spacer(Modifier.height(Spacing.md))
        }
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
