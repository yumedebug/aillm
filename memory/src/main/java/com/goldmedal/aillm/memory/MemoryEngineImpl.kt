package com.goldmedal.aillm.memory

import com.goldmedal.aillm.core.database.UserMemoryDao
import com.goldmedal.aillm.core.database.UserMemoryEntity
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemoryEngineImpl @Inject constructor(
    private val userMemoryDao: UserMemoryDao
) : MemoryEngine {

    override suspend fun storeMemory(
        category: String,
        key: String,
        value: String,
        importance: Int,
        confidence: Float,
        sourceMessageId: Long?
    ): Result<Long> {
        return try {
            val existingMemory = userMemoryDao.getMemoryByKey(category, key)
            if (existingMemory != null) {
                // Update existing memory
                val updatedMemory = existingMemory.copy(
                    value = value,
                    importance = importance,
                    confidence = confidence,
                    updatedAt = System.currentTimeMillis(),
                    lastAccessedAt = System.currentTimeMillis(),
                    sourceMessageId = sourceMessageId ?: existingMemory.sourceMessageId
                )
                userMemoryDao.updateMemory(updatedMemory)
                Result.success(updatedMemory.id)
            } else {
                // Create new memory
                val newMemory = UserMemoryEntity(
                    category = category,
                    key = key,
                    value = value,
                    importance = importance,
                    confidence = confidence,
                    sourceMessageId = sourceMessageId
                )
                val id = userMemoryDao.insertMemory(newMemory)
                Result.success(id)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getMemory(category: String, key: String): UserMemoryEntity? {
        return userMemoryDao.getMemoryByKey(category, key)
    }

    override suspend fun searchMemories(query: String): List<UserMemoryEntity> {
        return userMemoryDao.searchMemoriesByValue(query)
    }

    override suspend fun getMemoriesByCategory(category: String): List<UserMemoryEntity> {
        return userMemoryDao.getMemoriesByCategory(category).first()
    }

    override suspend fun getMostImportantMemories(limit: Int): List<UserMemoryEntity> {
        return userMemoryDao.getMostImportantMemories(limit)
    }

    override suspend fun getMostRecentlyAccessedMemories(limit: Int): List<UserMemoryEntity> {
        return userMemoryDao.getMostRecentlyAccessedMemories(limit)
    }

    override suspend fun updateMemory(memory: UserMemoryEntity): Result<Unit> {
        return try {
            userMemoryDao.updateMemory(memory)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteMemory(memoryId: Long): Result<Unit> {
        return try {
            userMemoryDao.deleteMemoryById(memoryId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteAllMemories(): Result<Unit> {
        return try {
            userMemoryDao.deleteAllMemories()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun forgetMemory(category: String, key: String): Result<Unit> {
        return try {
            val memory = userMemoryDao.getMemoryByKey(category, key)
            if (memory != null) {
                userMemoryDao.deleteMemory(memory)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
