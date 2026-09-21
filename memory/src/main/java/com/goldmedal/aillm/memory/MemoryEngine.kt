package com.goldmedal.aillm.memory

import com.goldmedal.aillm.core.database.UserMemoryEntity
import kotlinx.coroutines.flow.Flow

interface MemoryEngine {
    /** Live view of everything the assistant remembers. */
    fun observeMemories(): Flow<List<UserMemoryEntity>>

    /** Memories consolidated away: kept, but no longer shown to the model. */
    fun observeArchivedMemories(): Flow<List<UserMemoryEntity>>

    /**
     * Writes a fact, merging it into what is already known rather than adding
     * another near-copy. Returns the row id it landed on.
     */
    suspend fun storeMemory(
        category: String,
        key: String,
        value: String,
        importance: Int = 0,
        confidence: Float = 1.0f,
        sourceMessageId: Long? = null
    ): Result<Long>

    suspend fun getMemory(category: String, key: String): UserMemoryEntity?
    suspend fun searchMemories(query: String): List<UserMemoryEntity>
    suspend fun getMemoriesByCategory(category: String): List<UserMemoryEntity>
    suspend fun getMostImportantMemories(limit: Int): List<UserMemoryEntity>
    suspend fun getMostRecentlyAccessedMemories(limit: Int): List<UserMemoryEntity>
    suspend fun updateMemory(memory: UserMemoryEntity): Result<Unit>

    /** Brings an archived memory back, e.g. one that was consolidated away. */
    suspend fun restoreMemory(memoryId: Long): Result<Unit>

    /**
     * Collapses near-duplicates and trims over-full categories. Returns how
     * many memories were archived.
     */
    suspend fun cleanUpMemories(): Result<Int>

    suspend fun deleteMemory(memoryId: Long): Result<Unit>
    suspend fun deleteAllMemories(): Result<Unit>
    suspend fun forgetMemory(category: String, key: String): Result<Unit>
}
