package com.goldmedal.aillm.memory

import com.goldmedal.aillm.core.database.UserMemoryDao
import com.goldmedal.aillm.core.database.UserMemoryEntity
import com.goldmedal.aillm.memory.consolidation.MemoryConsolidator
import com.goldmedal.aillm.memory.embedding.SemanticSearch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemoryEngineImpl @Inject constructor(
    private val userMemoryDao: UserMemoryDao,
    private val semanticSearch: SemanticSearch,
    private val consolidator: MemoryConsolidator
) : MemoryEngine {

    override fun observeMemories(): Flow<List<UserMemoryEntity>> =
        userMemoryDao.getAllActiveMemories()

    override fun observeArchivedMemories(): Flow<List<UserMemoryEntity>> =
        userMemoryDao.getArchivedMemories()

    /**
     * Facts are never just inserted: [MemoryConsolidator] merges them into what
     * is already known, so saying the same thing twice strengthens one memory
     * instead of creating two.
     */
    override suspend fun storeMemory(
        category: String,
        key: String,
        value: String,
        importance: Int,
        confidence: Float,
        sourceMessageId: Long?
    ): Result<Long> {
        return try {
            Result.success(
                consolidator.store(
                    category = category,
                    key = key,
                    value = value,
                    importance = importance,
                    confidence = confidence,
                    sourceMessageId = sourceMessageId
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getMemory(category: String, key: String): UserMemoryEntity? {
        return userMemoryDao.getMemoryByKey(category, key)
    }

    /**
     * What the model gets to see about the user for one turn. Retrieval lives
     * in [SemanticSearch]; this only records that the memories were used, which
     * is what makes "recently used" ordering meaningful.
     */
    override suspend fun searchMemories(query: String): List<UserMemoryEntity> {
        val memories = runCatching { semanticSearch.search(query, RECALL_LIMIT) }
            .getOrDefault(emptyList())
        if (memories.isNotEmpty()) {
            runCatching {
                userMemoryDao.touchMemories(memories.map { it.id }, System.currentTimeMillis())
            }
        }
        return memories
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

    override suspend fun restoreMemory(memoryId: Long): Result<Unit> {
        return try {
            consolidator.restore(memoryId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun cleanUpMemories(): Result<Int> {
        return try {
            Result.success(consolidator.cleanUp())
        } catch (e: Exception) {
            Result.failure(e)
        }
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

    private companion object {
        /** Upper bound on memories recalled per turn. */
        private const val RECALL_LIMIT = 10
    }
}
