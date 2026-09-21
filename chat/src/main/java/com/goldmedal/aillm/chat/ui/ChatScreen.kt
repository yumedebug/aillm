package com.goldmedal.aillm.chat.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.goldmedal.aillm.ai.model.ModelStatus
import com.goldmedal.aillm.chat.image.ChatImageStore
import com.goldmedal.aillm.chat.viewmodel.ChatViewModel
import com.goldmedal.aillm.core.database.MessageEntity
import com.goldmedal.aillm.core.design.AillmGlass
import com.goldmedal.aillm.core.design.AillmTopBar
import com.goldmedal.aillm.core.design.Spacing
import com.goldmedal.aillm.core.design.glassBorderColor

@Composable
fun ChatScreen(
    onOpenModels: () -> Unit,
    onOpenMemory: () -> Unit,
    modifier: Modifier = Modifier,
    chatId: Long? = null,
    viewModel: ChatViewModel = hiltViewModel()
) {
    LaunchedEffect(chatId) {
        if (chatId != null && chatId > 0L) viewModel.loadChat(chatId)
    }

    val messages by viewModel.messages.collectAsState()
    val streamingText by viewModel.streamingText.collectAsState()
    val isGenerating by viewModel.isGenerating.collectAsState()
    val attachedImage by viewModel.attachedImage.collectAsState()
    val attachedFileName by viewModel.attachedFileName.collectAsState()
    val modelStatus by viewModel.chatModelStatus.collectAsState()
    val modelName by viewModel.chatModelName.collectAsState()
    val error by viewModel.error.collectAsState()

    var input by remember { mutableStateOf("") }
    var showMemory by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val context = LocalContext.current

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? -> uri?.let { viewModel.attachImage(it) } }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> uri?.let { viewModel.attachFile(it) } }

    LaunchedEffect(messages.size, isGenerating) {
        val target = if (isGenerating) messages.size else messages.size - 1
        if (target >= 0) listState.animateScrollToItem(target)
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AillmTopBar(
                title = "AI",
                subtitle = if (modelStatus is ModelStatus.Ready) modelName else "No chat model loaded",
                actions = {
                    // Opens in place: what the assistant remembers is part of the
                    // conversation, so it should not be a separate trip.
                    IconButton(onClick = { showMemory = true }) {
                        Icon(Icons.Default.Psychology, contentDescription = "Memory")
                    }
                }
            )
        },
        bottomBar = {
            Composer(
                input = input,
                onInputChange = { input = it },
                isGenerating = isGenerating,
                modelReady = modelStatus is ModelStatus.Ready,
                onChooseModel = onOpenModels,
                onSend = {
                    val text = input.trim()
                    if (text.isNotEmpty()) {
                        viewModel.sendMessage(text)
                        input = ""
                    }
                },
                onStop = { viewModel.stopGeneration() },
                onPickImage = { imagePicker.launch("image/*") },
                onPickFile = { filePicker.launch(arrayOf("text/*", "application/json", "application/xml")) },
                attachedImage = attachedImage,
                attachedFileName = attachedFileName,
                onRemoveImage = { viewModel.removeAttachedImage() },
                onRemoveFile = { viewModel.removeAttachedFile() }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (messages.isEmpty() && !isGenerating) {
                EmptyChat(
                    modelReady = modelStatus is ModelStatus.Ready,
                    onChooseModel = onOpenModels
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = Spacing.sm),
                    verticalArrangement = Arrangement.Bottom
                ) {
                    items(messages, key = { it.id }) { message ->
                        val isLastAssistant = message.role == "assistant" && message.id == messages.lastOrNull()?.id
                        // Shared images are shown again here so it is clear the
                        // picture is still part of the conversation.
                        val imageModel = remember(message.id, message.imagePath) {
                            if (message.hasImage) {
                                ChatImageStore.model(context, message.imagePath)
                            } else {
                                null
                            }
                        }
                        MessageBubble(
                            message = message,
                            imageModel = imageModel,
                            documentAttachment = message.documentName,
                            canRegenerate = isLastAssistant && !isGenerating,
                            onRegenerate = { viewModel.regenerate() }
                        )
                    }
                    if (streamingText != null) {
                        item(key = "streaming") {
                            MessageBubble(
                                message = MessageEntity(
                                    chatId = 0L,
                                    role = "assistant",
                                    content = streamingText.orEmpty()
                                ),
                                isStreaming = true
                            )
                        }
                    }
                }
            }

            error?.let { message ->
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = Spacing.lg)
                        .fillMaxWidth()
                ) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(Spacing.md)
                    )
                }
            }
        }
    }

    if (showMemory) {
        MemorySheet(
            onDismiss = { showMemory = false },
            onManageAll = {
                showMemory = false
                onOpenMemory()
            }
        )
    }
}

@Composable
private fun EmptyChat(modelReady: Boolean, onChooseModel: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.xl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = "Hello.",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(Spacing.sm))
        Text(
            text = "Your assistant runs entirely on this device. Nothing leaves your phone.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (!modelReady) {
            Spacer(Modifier.height(Spacing.lg))
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = MaterialTheme.shapes.large,
                border = BorderStroke(AillmGlass.borderWidth, glassBorderColor()),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(Spacing.lg)) {
                    Text(
                        text = "No model yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        text = "Pick a chat model that fits your phone, then download it. You only keep the models you want.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    TextButton(onClick = onChooseModel) {
                        Text("Choose a model", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun Composer(
    input: String,
    onInputChange: (String) -> Unit,
    isGenerating: Boolean,
    modelReady: Boolean,
    onChooseModel: () -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onPickImage: () -> Unit,
    onPickFile: () -> Unit,
    attachedImage: Uri?,
    attachedFileName: String?,
    onRemoveImage: () -> Unit,
    onRemoveFile: () -> Unit
) {
    var showAttachMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .padding(horizontal = Spacing.md, vertical = Spacing.sm)
    ) {
        if (attachedImage != null || attachedFileName != null) {
            Row(
                modifier = Modifier.padding(bottom = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (attachedImage != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = MaterialTheme.shapes.medium,
                        border = BorderStroke(AillmGlass.borderWidth, glassBorderColor())
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(
                                model = attachedImage,
                                contentDescription = "Attached image",
                                modifier = Modifier
                                    .size(40.dp)
                                    .padding(Spacing.xs)
                            )
                            IconButton(onClick = onRemoveImage) {
                                Icon(Icons.Default.Close, contentDescription = "Remove image")
                            }
                        }
                    }
                }
                if (attachedFileName != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = MaterialTheme.shapes.medium,
                        border = BorderStroke(AillmGlass.borderWidth, glassBorderColor())
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = attachedFileName ?: "",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = Spacing.md)
                            )
                            IconButton(onClick = onRemoveFile) {
                                Icon(Icons.Default.Close, contentDescription = "Remove file")
                            }
                        }
                    }
                }
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = MaterialTheme.shapes.extraLarge,
            border = BorderStroke(AillmGlass.borderWidth, glassBorderColor()),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Box {
                    IconButton(onClick = { showAttachMenu = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Attach")
                    }
                    DropdownMenu(
                        expanded = showAttachMenu,
                        onDismissRequest = { showAttachMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Photo") },
                            leadingIcon = { Icon(Icons.Default.Image, contentDescription = null) },
                            onClick = {
                                showAttachMenu = false
                                onPickImage()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Document") },
                            leadingIcon = { Icon(Icons.Default.AttachFile, contentDescription = null) },
                            onClick = {
                                showAttachMenu = false
                                onPickFile()
                            }
                        )
                    }
                }

                TextField(
                    value = input,
                    onValueChange = onInputChange,
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            if (modelReady) "Message" else "Choose a model to begin",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    },
                    maxLines = 5,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent
                    )
                )

                if (isGenerating) {
                    IconButton(onClick = onStop) {
                        Icon(Icons.Default.Stop, contentDescription = "Stop")
                    }
                } else {
                    IconButton(
                        onClick = {
                            if (!modelReady) onChooseModel() else onSend()
                        },
                        enabled = input.isNotBlank() || !modelReady
                    ) {
                        Surface(
                            color = if (input.isNotBlank() && modelReady) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHighest
                            },
                            shape = CircleShape
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send",
                                tint = if (input.isNotBlank() && modelReady) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier
                                    .padding(Spacing.sm)
                                    .size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
