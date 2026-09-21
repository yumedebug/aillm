package com.goldmedal.aillm.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A model whose file actually exists on disk. "Installed" means the file is
 * present and verified — it says nothing about whether it is loaded into the
 * inference engine. Load state is tracked separately, in memory.
 */
@Entity(tableName = "installed_models")
data class InstalledModelEntity(
    @PrimaryKey val specId: String,
    val filePath: String,
    val fileSizeBytes: Long,
    val installedAt: Long = System.currentTimeMillis(),
    val verified: Boolean = true,
    val lastUsedAt: Long? = null
)
