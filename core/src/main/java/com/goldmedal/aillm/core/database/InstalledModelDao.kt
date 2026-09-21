package com.goldmedal.aillm.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface InstalledModelDao {
    @Query("SELECT * FROM installed_models ORDER BY installedAt DESC")
    fun observeAll(): Flow<List<InstalledModelEntity>>

    @Query("SELECT * FROM installed_models")
    suspend fun getAll(): List<InstalledModelEntity>

    @Query("SELECT * FROM installed_models WHERE specId = :specId LIMIT 1")
    suspend fun getById(specId: String): InstalledModelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: InstalledModelEntity)

    @Query("DELETE FROM installed_models WHERE specId = :specId")
    suspend fun deleteById(specId: String)

    @Query("UPDATE installed_models SET lastUsedAt = :timestamp WHERE specId = :specId")
    suspend fun touch(specId: String, timestamp: Long = System.currentTimeMillis())
}
