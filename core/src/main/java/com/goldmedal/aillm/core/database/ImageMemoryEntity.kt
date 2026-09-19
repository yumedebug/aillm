package com.goldmedal.aillm.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "image_memories")
data class ImageMemoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val filePath: String,
    val fileName: String,
    val description: String = "",
    val tags: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lastAccessedAt: Long = System.currentTimeMillis(),
    val chatId: Long? = null,
    val messageId: Long? = null
)
