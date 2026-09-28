package com.goldmedal.aillm.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface GeneratedImageDao {

    /** Newest first — the order a gallery is read in. */
    @Query("SELECT * FROM generated_images ORDER BY createdAt DESC")
    fun getAll(): Flow<List<GeneratedImageEntity>>

    @Query("SELECT * FROM generated_images WHERE id = :id")
    suspend fun getById(id: Long): GeneratedImageEntity?

    @Insert
    suspend fun insert(image: GeneratedImageEntity): Long

    @Query("DELETE FROM generated_images WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM generated_images")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM generated_images")
    suspend fun count(): Int
}
