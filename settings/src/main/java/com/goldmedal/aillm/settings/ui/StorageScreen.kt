package com.goldmedal.aillm.settings.ui

import android.app.ActivityManager
import android.content.Context
import android.os.Environment
import android.os.StatFs
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.io.File
import java.text.DecimalFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    var storageInfo by remember { mutableStateOf(StorageInfo()) }

    LaunchedEffect(Unit) {
        storageInfo = calculateStorageInfo(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Storage") },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Device Storage",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary
            )

            StorageCard(
                title = "Available Space",
                value = formatSize(storageInfo.availableSpace),
                subtitle = "Free space on device"
            )

            StorageCard(
                title = "Total Space",
                value = formatSize(storageInfo.totalSpace),
                subtitle = "Total device storage"
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "App Data",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary
            )

            StorageCard(
                title = "Conversations",
                value = formatSize(storageInfo.conversationsSize),
                subtitle = "Chat history"
            )

            StorageCard(
                title = "Images",
                value = formatSize(storageInfo.imagesSize),
                subtitle = "Stored images"
            )

            StorageCard(
                title = "Files",
                value = formatSize(storageInfo.filesSize),
                subtitle = "User files"
            )

            StorageCard(
                title = "Models",
                value = formatSize(storageInfo.modelsSize),
                subtitle = "AI models"
            )

            StorageCard(
                title = "Total App Data",
                value = formatSize(storageInfo.totalAppSize),
                subtitle = "Total app usage"
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Memory (RAM)",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary
            )

            StorageCard(
                title = "Available RAM",
                value = formatSize(storageInfo.availableRam),
                subtitle = "Free memory"
            )

            StorageCard(
                title = "Total RAM",
                value = formatSize(storageInfo.totalRam),
                subtitle = "Total device memory"
            )
        }
    }
}

@Composable
fun StorageCard(
    title: String,
    value: String,
    subtitle: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

data class StorageInfo(
    val availableSpace: Long = 0,
    val totalSpace: Long = 0,
    val conversationsSize: Long = 0,
    val imagesSize: Long = 0,
    val filesSize: Long = 0,
    val modelsSize: Long = 0,
    val totalAppSize: Long = 0,
    val availableRam: Long = 0,
    val totalRam: Long = 0
)

private fun calculateStorageInfo(context: Context): StorageInfo {
    val stat = StatFs(Environment.getDataDirectory().path)
    val availableSpace = stat.availableBlocksLong * stat.blockSizeLong
    val totalSpace = stat.blockCountLong * stat.blockSizeLong

    val appDir = context.getExternalFilesDir(null) ?: context.filesDir
    val conversationsSize = getDirSize(File(appDir, "conversations"))
    val imagesSize = getDirSize(File(appDir, "images"))
    val filesSize = getDirSize(File(appDir, "user_files"))
    val modelsSize = getDirSize(File(appDir, "models"))
    val totalAppSize = getDirSize(appDir)

    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val memInfo = ActivityManager.MemoryInfo()
    activityManager.getMemoryInfo(memInfo)

    return StorageInfo(
        availableSpace = availableSpace,
        totalSpace = totalSpace,
        conversationsSize = conversationsSize,
        imagesSize = imagesSize,
        filesSize = filesSize,
        modelsSize = modelsSize,
        totalAppSize = totalAppSize,
        availableRam = memInfo.availMem,
        totalRam = memInfo.totalMem
    )
}

private fun getDirSize(dir: File): Long {
    if (!dir.exists()) return 0
    var size = 0L
    val files = dir.listFiles() ?: return 0
    for (file in files) {
        size += if (file.isDirectory) {
            getDirSize(file)
        } else {
            file.length()
        }
    }
    return size
}

private fun formatSize(bytes: Long): String {
    val df = DecimalFormat("#.##")
    return when {
        bytes >= 1_073_741_824 -> "${df.format(bytes / 1_073_741_824.0)} GB"
        bytes >= 1_048_576 -> "${df.format(bytes / 1_048_576.0)} MB"
        bytes >= 1_024 -> "${df.format(bytes / 1_024.0)} KB"
        else -> "$bytes B"
    }
}
