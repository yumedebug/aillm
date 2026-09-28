package com.goldmedal.aillm.ui.image

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.goldmedal.aillm.core.database.GeneratedImageEntity
import com.goldmedal.aillm.core.design.AillmTopBar
import com.goldmedal.aillm.core.design.EmptyState
import com.goldmedal.aillm.core.design.GlassPanel
import com.goldmedal.aillm.core.design.SecondaryButton
import com.goldmedal.aillm.core.design.Spacing
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The gallery: every picture produced on this device, newest first.
 *
 * A grid of thumbnails, and a preview for the one that is opened — where the
 * picture can be saved into the device gallery, shared, or deleted. The list is
 * the app's own storage, so it needs no permission and no model to be loaded;
 * looking back at past generations is free.
 */
@Composable
fun GalleryScreen(
    onBack: (() -> Unit)? = null,
    viewModel: GalleryViewModel = hiltViewModel()
) {
    val images by viewModel.images.collectAsState()
    val preview by viewModel.preview.collectAsState()
    val notice by viewModel.notice.collectAsState()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(notice) {
        notice?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearNotice()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            AillmTopBar(
                title = "Gallery",
                subtitle = "端末内で生成した画像",
                onBack = onBack
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (images.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        icon = Icons.Default.PhotoLibrary,
                        title = "No pictures yet",
                        message = "Generate an image from Models → Images and it will be kept " +
                            "here, together with the prompt that made it."
                    )
                }
                return@Box
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = Spacing.lg,
                    end = Spacing.lg,
                    top = Spacing.sm,
                    bottom = Spacing.xxl
                ),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                items(images, key = { it.id }) { image ->
                    GalleryCell(
                        image = image,
                        file = viewModel.fileOf(image),
                        onClick = { viewModel.open(image) }
                    )
                }
            }

            preview?.let { image ->
                PreviewOverlay(
                    image = image,
                    file = viewModel.fileOf(image),
                    onClose = { viewModel.closePreview() },
                    onSave = { viewModel.saveToGallery(image) },
                    onDelete = { viewModel.delete(image) },
                    onShare = {
                        scope.launch {
                            val intent = viewModel.shareIntent(image)
                            if (intent == null) {
                                viewModel.report("Could not prepare the image to share")
                            } else {
                                context.startActivity(
                                    Intent.createChooser(intent, "Share image")
                                )
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun GalleryCell(
    image: GeneratedImageEntity,
    file: java.io.File?,
    onClick: () -> Unit
) {
    GlassPanel(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        onClick = onClick,
        elevation = 4.dp
    ) {
        Column {
            if (file != null) {
                AsyncImage(
                    model = file,
                    contentDescription = image.prompt,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                )
            } else {
                // The row outlived its file; still show what it was.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoLibrary,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Column(modifier = Modifier.padding(Spacing.sm)) {
                Text(
                    text = image.prompt,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = formatStamp(image.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * The opened picture and what can be done with it. Drawn over the grid rather
 * than pushed as another destination, so closing it returns to the same scroll
 * position.
 */
@Composable
private fun PreviewOverlay(
    image: GeneratedImageEntity,
    file: java.io.File?,
    onClose: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.94f))
            .clickable(onClick = onClose)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(Spacing.lg)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White
                    )
                }
            }

            if (file != null) {
                AsyncImage(
                    model = file,
                    contentDescription = image.prompt,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                )
            }

            Spacer(Modifier.height(Spacing.lg))
            Text(
                text = image.prompt,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = buildString {
                    append(image.modelName)
                    append(" · ")
                    append("${image.width}×${image.height}")
                    append(" · ")
                    append("${image.steps} steps")
                    append(" · ")
                    append(formatStamp(image.createdAt))
                },
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.fillMaxWidth()
            )
            if (image.negativePrompt.isNotBlank()) {
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = "Negative: ${image.negativePrompt}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(Spacing.lg))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                SecondaryButton(
                    text = "Save",
                    onClick = onSave,
                    modifier = Modifier.weight(1f)
                )
                SecondaryButton(
                    text = "Share",
                    onClick = onShare,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(Spacing.xs))
            TextButton(onClick = onDelete) {
                Text(
                    text = "Delete",
                    color = MaterialTheme.colorScheme.error
                )
            }
            Spacer(Modifier.height(Spacing.xxl))
        }
    }
}

private fun formatStamp(millis: Long): String =
    SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(millis))
