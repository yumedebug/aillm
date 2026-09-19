package com.goldmedal.aillm.memory

import com.goldmedal.aillm.core.database.UserMemoryEntity

interface MemoryEngine {
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
    suspend fun deleteMemory(memoryId: Long): Result<Unit>
    suspend fun deleteAllMemories(): Result<Unit>
    suspend fun forgetMemory(category: String, key: String): Result<Unit>
}
