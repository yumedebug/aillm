package com.goldmedal.aillm.core.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ModelDao {
    @Query("SELECT * FROM models")
    fun getAllModels(): Flow<List<ModelEntity>>

    @Query("SELECT * FROM models WHERE type = :type")
    fun getModelsByType(type: String): Flow<List<ModelEntity>>

    @Query("SELECT * FROM models WHERE id = :modelId")
    suspend fun getModelById(modelId: Long): ModelEntity?

    @Query("SELECT * FROM models WHERE type = :type AND isInstalled = 1")
    suspend fun getInstalledModelsByType(type: String): List<ModelEntity>

    @Query("SELECT * FROM models WHERE type = :type AND isLoaded = 1 LIMIT 1")
    suspend fun getLoadedModelByType(type: String): ModelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertModel(model: ModelEntity): Long

    @Update
    suspend fun updateModel(model: ModelEntity)

    @Delete
    suspend fun deleteModel(model: ModelEntity)

    @Query("UPDATE models SET isLoaded = :isLoaded, lastUsedAt = :timestamp WHERE id = :modelId")
    suspend fun updateModelLoadStatus(modelId: Long, isLoaded: Boolean, timestamp: Long = System.currentTimeMillis())
}
