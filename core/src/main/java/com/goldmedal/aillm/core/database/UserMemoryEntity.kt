package com.goldmedal.aillm.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "user_memories",
    indices = [Index(value = ["category", "key"], unique = true)]
)
data class UserMemoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val category: String,
    val key: String,
    val value: String,
    val importance: Int = 0,
    val confidence: Float = 1.0f,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lastAccessedAt: Long = System.currentTimeMillis(),
    val isActive: Boolean = true,
    val sourceMessageId: Long? = null
)
