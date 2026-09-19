package com.goldmedal.aillm.core.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface UserMemoryDao {
    @Query("SELECT * FROM user_memories WHERE isActive = 1 ORDER BY importance DESC, updatedAt DESC")
    fun getAllActiveMemories(): Flow<List<UserMemoryEntity>>

    @Query("SELECT * FROM user_memories WHERE category = :category AND isActive = 1")
    fun getMemoriesByCategory(category: String): Flow<List<UserMemoryEntity>>

    @Query("SELECT * FROM user_memories WHERE key = :key AND category = :category AND isActive = 1 LIMIT 1")
    suspend fun getMemoryByKey(category: String, key: String): UserMemoryEntity?

    @Query("SELECT * FROM user_memories WHERE value LIKE '%' || :query || '%' AND isActive = 1")
    suspend fun searchMemoriesByValue(query: String): List<UserMemoryEntity>

    @Query("SELECT * FROM user_memories WHERE isActive = 1 ORDER BY lastAccessedAt DESC LIMIT :limit")
    suspend fun getMostRecentlyAccessedMemories(limit: Int): List<UserMemoryEntity>

    @Query("SELECT * FROM user_memories WHERE isActive = 1 ORDER BY importance DESC LIMIT :limit")
    suspend fun getMostImportantMemories(limit: Int): List<UserMemoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(memory: UserMemoryEntity): Long

    @Update
    suspend fun updateMemory(memory: UserMemoryEntity)

    @Delete
    suspend fun deleteMemory(memory: UserMemoryEntity)

    @Query("DELETE FROM user_memories WHERE id = :memoryId")
    suspend fun deleteMemoryById(memoryId: Long)

    @Query("DELETE FROM user_memories")
    suspend fun deleteAllMemories()
}
