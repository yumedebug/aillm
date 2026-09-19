package com.goldmedal.aillm.core.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ImageMemoryDao {
    @Query("SELECT * FROM image_memories ORDER BY createdAt DESC")
    fun getAllImages(): Flow<List<ImageMemoryEntity>>

    @Query("SELECT * FROM image_memories WHERE id = :imageId")
    suspend fun getImageById(imageId: Long): ImageMemoryEntity?

    @Query("SELECT * FROM image_memories WHERE description LIKE '%' || :query || '%' OR tags LIKE '%' || :query || '%'")
    suspend fun searchImages(query: String): List<ImageMemoryEntity>

    @Query("SELECT * FROM image_memories WHERE chatId = :chatId ORDER BY createdAt DESC")
    fun getImagesByChatId(chatId: Long): Flow<List<ImageMemoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertImage(image: ImageMemoryEntity): Long

    @Update
    suspend fun updateImage(image: ImageMemoryEntity)

    @Delete
    suspend fun deleteImage(image: ImageMemoryEntity)

    @Query("DELETE FROM image_memories WHERE id = :imageId")
    suspend fun deleteImageById(imageId: Long)
}
