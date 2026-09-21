package com.goldmedal.aillm.settings.ui

import android.app.ActivityManager
import android.content.Context
import android.os.Environment
import android.os.StatFs
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.goldmedal.aillm.core.design.AillmDivider
import com.goldmedal.aillm.core.design.AillmTopBar
import com.goldmedal.aillm.core.design.SettingsGroup
import com.goldmedal.aillm.core.design.SettingsRow
import com.goldmedal.aillm.core.design.Spacing
import com.goldmedal.aillm.core.design.formatBytes
import java.io.File

@Composable
fun StorageScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var info by remember { mutableStateOf(StorageInfo()) }

    LaunchedEffect(Unit) {
        info = calculateStorageInfo(context)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AillmTopBar(title = "Storage", onBack = onBack) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            SettingsGroup(title = "Device") {
                SettingsRow(title = "Available", subtitle = formatBytes(info.availableSpace))
                AillmDivider()
                SettingsRow(title = "Total", subtitle = formatBytes(info.totalSpace))
            }

            SettingsGroup(title = "App data") {
                SettingsRow(title = "Models", subtitle = formatBytes(info.modelsSize))
                AillmDivider()
                SettingsRow(title = "Files", subtitle = formatBytes(info.filesSize))
                AillmDivider()
                SettingsRow(title = "Images", subtitle = formatBytes(info.imagesSize))
                AillmDivider()
                SettingsRow(title = "Conversations & memory", subtitle = formatBytes(info.databaseSize))
            }

            SettingsGroup(title = "Memory (RAM)") {
                SettingsRow(title = "Available", subtitle = formatBytes(info.availableRam))
                AillmDivider()
                SettingsRow(title = "Total", subtitle = formatBytes(info.totalRam))
            }

            Spacer(Modifier.height(Spacing.lg))
            androidx.compose.material3.Text(
                text = "Unloading a model frees its memory immediately. Deleting a model removes its file from this device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.lg)
            )
            Spacer(Modifier.height(Spacing.xxl))
        }
    }
}

data class StorageInfo(
    val availableSpace: Long = 0,
    val totalSpace: Long = 0,
    val databaseSize: Long = 0,
    val imagesSize: Long = 0,
    val filesSize: Long = 0,
    val modelsSize: Long = 0,
    val availableRam: Long = 0,
    val totalRam: Long = 0
)

private fun calculateStorageInfo(context: Context): StorageInfo {
    val stat = StatFs(Environment.getDataDirectory().path)
    val availableSpace = stat.availableBlocksLong * stat.blockSizeLong
    val totalSpace = stat.blockCountLong * stat.blockSizeLong

    val modelsSize = getDirSize(File(context.filesDir, "models"))
    val filesSize = getDirSize(File(context.getExternalFilesDir(null), "user_files"))
    val imagesSize = getDirSize(File(context.getExternalFilesDir(null), "images"))
    val databaseSize = runCatching {
        File(context.getDatabasePath("aillm_database").absolutePath).length()
    }.getOrDefault(0L)

    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val memInfo = ActivityManager.MemoryInfo()
    activityManager.getMemoryInfo(memInfo)

    return StorageInfo(
        availableSpace = availableSpace,
        totalSpace = totalSpace,
        databaseSize = databaseSize,
        imagesSize = imagesSize,
        filesSize = filesSize,
        modelsSize = modelsSize,
        availableRam = memInfo.availMem,
        totalRam = memInfo.totalMem
    )
}

private fun getDirSize(dir: File?): Long {
    if (dir == null || !dir.exists()) return 0L
    var size = 0L
    dir.listFiles()?.forEach { file ->
        size += if (file.isDirectory) getDirSize(file) else file.length()
    }
    return size
}
