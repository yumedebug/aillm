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

    /** Every active memory in one shot, for ranking in memory rather than in SQL. */
    @Query("SELECT * FROM user_memories WHERE isActive = 1 ORDER BY importance DESC, updatedAt DESC")
    suspend fun getAllActiveMemoriesOnce(): List<UserMemoryEntity>

    @Query("SELECT * FROM user_memories WHERE id = :memoryId")
    suspend fun getMemoryById(memoryId: Long): UserMemoryEntity?

    @Query("SELECT * FROM user_memories WHERE category = :category AND isActive = 1 ORDER BY updatedAt DESC")
    suspend fun getActiveMemoriesByCategoryOnce(category: String): List<UserMemoryEntity>

    /**
     * Memories that were consolidated away. They are archived rather than
     * deleted so nothing the assistant believed about the user is lost without
     * the user being able to bring it back.
     */
    @Query("SELECT * FROM user_memories WHERE isActive = 0 ORDER BY updatedAt DESC")
    fun getArchivedMemories(): Flow<List<UserMemoryEntity>>

    @Query("UPDATE user_memories SET isActive = :isActive WHERE id = :memoryId")
    suspend fun setMemoryActive(memoryId: Long, isActive: Boolean)

    /** Marks memories as used, so "recently used" ordering means something. */
    @Query("UPDATE user_memories SET lastAccessedAt = :timestamp WHERE id IN (:ids)")
    suspend fun touchMemories(ids: List<Long>, timestamp: Long)

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
