package com.goldmedal.aillm.core.util

object FileSizeUtils {
    fun formatSize(bytes: Long): String {
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

    fun formatRamUsage(bytes: Long): String {
        val mb = bytes / (1024.0 * 1024.0)
        val gb = mb / 1024.0

        return when {
            gb >= 1.0 -> String.format("%.1f GB RAM", gb)
            mb >= 1.0 -> String.format("%.0f MB RAM", mb)
            else -> "$bytes bytes RAM"
        }
    }
}
