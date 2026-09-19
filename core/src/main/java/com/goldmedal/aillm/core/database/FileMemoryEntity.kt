package com.goldmedal.aillm.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "file_memories")
data class FileMemoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val filePath: String,
    val fileName: String,
    val fileType: String,
    val fileSize: Long,
    val summary: String = "",
    val extractedText: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lastAccessedAt: Long = System.currentTimeMillis(),
    val chatId: Long? = null
)
