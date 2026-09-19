package com.goldmedal.aillm.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "models")
data class ModelEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val type: String,
    val filePath: String,
    val fileSize: Long = 0,
    val isInstalled: Boolean = false,
    val isLoaded: Boolean = false,
    val estimatedRamUsage: Long = 0,
    val contextLength: Int = 4096,
    val createdAt: Long = System.currentTimeMillis(),
    val lastUsedAt: Long? = null
)
